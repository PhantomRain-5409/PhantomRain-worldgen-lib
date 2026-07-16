package com.ptr5409.wglib.biomereplace.sub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 功能：解析/组合子群系条件（参考 Biolith Criterion）
 */
public final class SubCriteria {
    private SubCriteria() {
    }

    public static SubCriterion parse(JsonObject obj, Registry<Biome> registry) {
        if (obj == null) {
            throw new IllegalArgumentException("criterion is null");
        }

        String type = getString(obj, "type");
        if (type == null || type.isBlank()) {
            if (obj.has("preset")) {
                type = "preset";
            } else {
                throw new IllegalArgumentException("criterion missing type");
            }
        }
        type = type.trim().toLowerCase(Locale.ROOT);

        return switch (type) {
            case "all_of", "allof", "and" -> allOf(parseList(obj, registry));
            case "any_of", "anyof", "or" -> anyOf(parseList(obj, registry));
            case "not" -> {
                JsonObject inner = obj.has("criterion") && obj.get("criterion").isJsonObject()
                        ? obj.getAsJsonObject("criterion")
                        : obj.has("of") && obj.get("of").isJsonObject()
                        ? obj.getAsJsonObject("of")
                        : null;
                if (inner == null) {
                    throw new IllegalArgumentException("not criterion needs nested criterion");
                }
                SubCriterion child = parse(inner, registry);
                yield ctx -> !child.matches(ctx);
            }
            case "value" -> value(obj);
            case "deviation" -> deviation(obj);
            case "ratio" -> ratio(obj);
            case "noise", "patch_noise" -> patchNoise(obj);
            case "neighbor" -> neighbor(obj, registry);
            case "original" -> original(obj, registry);
            case "primary" -> primary(obj, registry);
            case "preset" -> preset(obj, registry);
            default -> throw new IllegalArgumentException("unknown criterion type '" + type + "'");
        };
    }

    private static List<SubCriterion> parseList(JsonObject obj, Registry<Biome> registry) {
        JsonArray arr = null;
        if (obj.has("criteria") && obj.get("criteria").isJsonArray()) {
            arr = obj.getAsJsonArray("criteria");
        } else if (obj.has("of") && obj.get("of").isJsonArray()) {
            arr = obj.getAsJsonArray("of");
        }
        if (arr == null || arr.isEmpty()) {
            throw new IllegalArgumentException("composite criterion needs non-empty criteria");
        }
        List<SubCriterion> list = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                throw new IllegalArgumentException("criteria entry must be object");
            }
            list.add(parse(el.getAsJsonObject(), registry));
        }
        return list;
    }

    private static SubCriterion allOf(List<SubCriterion> list) {
        return ctx -> {
            for (SubCriterion c : list) {
                if (!c.matches(ctx)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static SubCriterion anyOf(List<SubCriterion> list) {
        return ctx -> {
            for (SubCriterion c : list) {
                if (c.matches(ctx)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static SubCriterion value(JsonObject obj) {
        ClimateParameterAxis axis = ClimateParameterAxis.parse(getString(obj, "parameter", "axis"));
        float min = getFloat(obj, "min", Float.NEGATIVE_INFINITY);
        float max = getFloat(obj, "max", Float.POSITIVE_INFINITY);
        return ctx -> {
            if (ctx.targetPoint() == null) {
                return false;
            }
            float v = Climate.unquantizeCoord(axis.valueOf(ctx.targetPoint()));
            return v >= min && v <= max;
        };
    }

    private static SubCriterion deviation(JsonObject obj) {
        ClimateParameterAxis axis = ClimateParameterAxis.parse(getString(obj, "parameter", "axis"));
        float min = getFloat(obj, "min", Float.NEGATIVE_INFINITY);
        float max = getFloat(obj, "max", Float.POSITIVE_INFINITY);
        return ctx -> {
            if (ctx.targetPoint() == null || ctx.primaryPoint() == null) {
                return false;
            }
            Climate.Parameter range = axis.rangeOf(ctx.primaryPoint());
            long center = (range.min() + range.max()) / 2L;
            float dev = Climate.unquantizeCoord(axis.valueOf(ctx.targetPoint()) - center);
            return dev >= min && dev <= max;
        };
    }

    private static SubCriterion ratio(JsonObject obj) {
        String target = getString(obj, "target", "ratio");
        if (target == null) {
            throw new IllegalArgumentException("ratio needs target=center|edge");
        }
        String kind = target.trim().toLowerCase(Locale.ROOT);
        float min = getFloat(obj, "min", Float.NEGATIVE_INFINITY);
        float max = getFloat(obj, "max", Float.POSITIVE_INFINITY);
        ClimateParameterAxis axis = null;
        String axisName = getString(obj, "parameter", "axis");
        if (axisName != null) {
            axis = ClimateParameterAxis.parse(axisName);
        }
        ClimateParameterAxis finalAxis = axis;
        return ctx -> {
            float v;
            if ("edge".equals(kind) || "border".equals(kind)) {
                v = ctx.edgeRatio();
            } else if ("center".equals(kind) || "interior".equals(kind)) {
                v = finalAxis == null ? ctx.centerRatio() : ctx.centerRatio(finalAxis);
            } else {
                return false;
            }
            return v >= min && v <= max;
        };
    }

    private static SubCriterion patchNoise(JsonObject obj) {
        float min = getFloat(obj, "min", Float.NEGATIVE_INFINITY);
        float max = getFloat(obj, "max", Float.POSITIVE_INFINITY);
        float scale = getFloat(obj, "scale", 96.0f);
        long salt = getLong(obj, "salt", 0L);
        return ctx -> {
            float value = ctx.patchNoise(scale, salt);
            return value >= min && value <= max;
        };
    }

    private static SubCriterion neighbor(JsonObject obj, Registry<Biome> registry) {
        Predicate<Holder<Biome>> pred = biomePredicate(obj, registry, "biome", "biomes", "tag");
        return ctx -> ctx.neighbor() != null && pred.test(ctx.neighbor());
    }

    private static SubCriterion primary(JsonObject obj, Registry<Biome> registry) {
        Predicate<Holder<Biome>> pred = biomePredicate(obj, registry, "biome", "biomes", "tag");
        return ctx -> ctx.primary() != null && pred.test(ctx.primary());
    }

    private static SubCriterion original(JsonObject obj, Registry<Biome> registry) {
        Predicate<Holder<Biome>> pred = biomePredicate(obj, registry, "biome", "biomes", "tag");
        return ctx -> ctx.original() != null && pred.test(ctx.original());
    }

    private static SubCriterion preset(JsonObject obj, Registry<Biome> registry) {
        String preset = getString(obj, "preset", "name");
        if (preset == null) {
            throw new IllegalArgumentException("preset criterion missing preset");
        }
        String key = preset.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        float max = getFloat(obj, "max", 0.2f);
        return switch (key) {
            case "near_border", "border", "edge" -> ratioMaxEdge(max);
            case "near_interior", "interior", "center" -> ratioMaxCenter(max);
            case "beachside" -> allOf(List.of(
                    ratioMaxEdge(max),
                    neighborTag(registry, "minecraft:is_beach")
            ));
            case "oceanside" -> allOf(List.of(
                    ratioMaxEdge(max),
                    neighborTag(registry, "minecraft:is_ocean")
            ));
            case "riverside" -> allOf(List.of(
                    ratioMaxEdge(max),
                    neighborTag(registry, "minecraft:is_river")
            ));
            default -> throw new IllegalArgumentException("unknown preset '" + preset + "'");
        };
    }

    private static SubCriterion ratioMaxEdge(float max) {
        return ctx -> ctx.edgeRatio() <= max;
    }

    private static SubCriterion ratioMaxCenter(float max) {
        return ctx -> ctx.centerRatio() <= max;
    }

    private static SubCriterion neighborTag(Registry<Biome> registry, String tagId) {
        TagKey<Biome> tag = TagKey.create(Registries.BIOME, ResourceLocation.tryParse(tagId));
        return ctx -> ctx.neighbor() != null && ctx.neighbor().is(tag);
    }

    private static Predicate<Holder<Biome>> biomePredicate(JsonObject obj, Registry<Biome> registry, String... keys) {
        // tag first
        String tag = getString(obj, "tag");
        if (tag != null) {
            String id = tag.startsWith("#") ? tag.substring(1) : tag;
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc == null) {
                throw new IllegalArgumentException("invalid tag '" + tag + "'");
            }
            TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, loc);
            return h -> h.is(tagKey);
        }

        if (obj.has("biomes") && obj.get("biomes").isJsonArray()) {
            List<ResourceKey<Biome>> keysList = new ArrayList<>();
            for (JsonElement el : obj.getAsJsonArray("biomes")) {
                String id = el.getAsString();
                ResourceLocation loc = ResourceLocation.tryParse(id);
                if (loc == null) {
                    throw new IllegalArgumentException("invalid biome '" + id + "'");
                }
                keysList.add(ResourceKey.create(Registries.BIOME, loc));
            }
            return h -> {
                for (ResourceKey<Biome> k : keysList) {
                    if (h.is(k)) {
                        return true;
                    }
                }
                return false;
            };
        }

        String biome = getString(obj, "biome");
        if (biome != null) {
            if (biome.startsWith("#")) {
                ResourceLocation loc = ResourceLocation.tryParse(biome.substring(1));
                if (loc == null) {
                    throw new IllegalArgumentException("invalid tag '" + biome + "'");
                }
                TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, loc);
                return h -> h.is(tagKey);
            }
            ResourceLocation loc = ResourceLocation.tryParse(biome);
            if (loc == null) {
                throw new IllegalArgumentException("invalid biome '" + biome + "'");
            }
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, loc);
            // ensure exists
            Optional<Holder.Reference<Biome>> holder = registry.getHolder(key);
            if (holder.isEmpty()) {
                throw new IllegalArgumentException("biome '" + biome + "' does not exist");
            }
            return h -> h.is(key);
        }

        throw new IllegalArgumentException("biome predicate needs biome/biomes/tag");
    }

    private static String getString(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                try {
                    return obj.get(key).getAsString();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsFloat();
        } catch (Exception e) {
            return def;
        }
    }

    private static long getLong(JsonObject obj, String key, long def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsLong();
        } catch (Exception e) {
            return def;
        }
    }
}
