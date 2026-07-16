package com.ptr5409.wglib.biomereplace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ptr5409.wglib.PhantomRainWorldgenLib;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 功能：从数据包加载群系替换规则
 */
public final class BiomeReplaceData {
    public static final String DIRECTORY = "ptrlib_wg/biome_replace";

    public static final String TYPE_DIRECT = "direct_replace";
    public static final String TYPE_WEIGHTED = "weighted_replace";
    public static final String TYPE_WEIGHTED_INFUSE = "weighted_infuse";
    public static final String TYPE_SUB = "sub_biome";

    private BiomeReplaceData() {
    }

    public record WeightedEntry(String biome, int weight) {
    }

    /** 静态表规则（direct / weighted / weighted_infuse） */
    public record RawRule(
            ResourceLocation sourceFile,
            int index,
            String type,
            String dimension,
            String target,
            String replacement,
            List<WeightedEntry> entries,
            int parentWeight
    ) {
        public boolean isDirect() {
            return TYPE_DIRECT.equals(type);
        }

        public boolean isWeighted() {
            return TYPE_WEIGHTED.equals(type);
        }

        public boolean isWeightedInfuse() {
            return TYPE_WEIGHTED_INFUSE.equals(type);
        }
    }

    /** 运行时子群系规则（Biolith 风格条件覆盖） */
    public record RawSubRule(
            ResourceLocation sourceFile,
            int index,
            String dimension,
            String target,
            String biome,
            JsonObject criterion
    ) {
    }

    public record LoadResult(List<RawRule> staticRules, List<RawSubRule> subRules) {
    }

    public static LoadResult load(ResourceManager resourceManager) {
        List<RawRule> rules = new ArrayList<>();
        List<RawSubRule> subRules = new ArrayList<>();
        Map<ResourceLocation, Resource> found = resourceManager.listResources(
                DIRECTORY,
                loc -> loc.getPath().endsWith(".json")
        );

        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8))) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonArray()) {
                    PhantomRainWorldgenLib.LOGGER.warn(
                            "[BiomeReplace] {} root must be a JSON array, skipped", fileId);
                    continue;
                }
                JsonArray array = root.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    JsonElement element = array.get(i);
                    if (!element.isJsonObject()) {
                        PhantomRainWorldgenLib.LOGGER.warn(
                                "[BiomeReplace] {}[{}]: expected object, skipped", fileId, i);
                        continue;
                    }
                    parseAny(fileId, i, element.getAsJsonObject(), rules, subRules);
                }
            } catch (Exception e) {
                PhantomRainWorldgenLib.LOGGER.error("[BiomeReplace] Failed to read {}", fileId, e);
            }
        }

        PhantomRainWorldgenLib.LOGGER.info(
                "[BiomeReplace] Loaded {} static + {} sub_biome rule(s) from {} file(s)",
                rules.size(), subRules.size(), found.size());
        return new LoadResult(rules, subRules);
    }

    private static void parseAny(ResourceLocation fileId, int index, JsonObject obj,
                                 List<RawRule> rules, List<RawSubRule> subRules) {
        String type = getString(obj, "type");
        if (type == null || type.isBlank()) {
            PhantomRainWorldgenLib.LOGGER.warn(
                    "[BiomeReplace] {}[{}]: missing type, skipped", fileId, index);
            return;
        }
        type = type.trim().toLowerCase(Locale.ROOT);

        if ("direct".equals(type)) {
            type = TYPE_DIRECT;
        } else if ("weighted".equals(type)) {
            type = TYPE_WEIGHTED;
        }

        String target = firstString(obj, "target", "from");
        if (target == null || target.isBlank()) {
            PhantomRainWorldgenLib.LOGGER.warn(
                    "[BiomeReplace] {}[{}]: missing target, skipped", fileId, index);
            return;
        }
        target = target.trim();

        String dimension = getString(obj, "dimension");
        if (dimension != null) {
            dimension = dimension.trim();
            if (dimension.isEmpty()) {
                dimension = null;
            }
        }

        switch (type) {
            case TYPE_DIRECT -> {
                RawRule r = parseDirect(fileId, index, obj, dimension, target);
                if (r != null) {
                    rules.add(r);
                }
            }
            case TYPE_WEIGHTED -> {
                RawRule r = parseWeighted(fileId, index, obj, dimension, target);
                if (r != null) {
                    rules.add(r);
                }
            }
            case TYPE_WEIGHTED_INFUSE -> {
                RawRule r = parseWeightedInfuse(fileId, index, obj, dimension, target);
                if (r != null) {
                    rules.add(r);
                }
            }
            case TYPE_SUB -> {
                RawSubRule r = parseSubBiome(fileId, index, obj, dimension, target);
                if (r != null) {
                    subRules.add(r);
                }
            }
            default -> PhantomRainWorldgenLib.LOGGER.warn(
                    "[BiomeReplace] {}[{}]: unknown type '{}', skipped", fileId, index, type);
        }
    }

    private static RawRule parseDirect(ResourceLocation fileId, int index, JsonObject obj,
                                       String dimension, String target) {
        String replacement = firstString(obj, "replacement", "to");
        if (replacement != null) {
            replacement = replacement.trim();
            if (replacement.isEmpty()) {
                replacement = null;
            }
        }
        return new RawRule(fileId, index, TYPE_DIRECT, dimension, target, replacement, List.of(), 0);
    }

    private static RawRule parseWeighted(ResourceLocation fileId, int index, JsonObject obj,
                                         String dimension, String target) {
        JsonArray arr = firstArray(obj, "replacements", "to");
        if (arr == null || arr.isEmpty()) {
            PhantomRainWorldgenLib.LOGGER.warn(
                    "[BiomeReplace] {}[{}]: weighted_replace needs non-empty replacements, skipped",
                    fileId, index);
            return null;
        }
        List<WeightedEntry> entries = parseWeightedEntries(fileId, index, arr);
        if (entries.isEmpty()) {
            return null;
        }
        return new RawRule(fileId, index, TYPE_WEIGHTED, dimension, target, null, entries, 0);
    }

    private static RawRule parseWeightedInfuse(ResourceLocation fileId, int index, JsonObject obj,
                                               String dimension, String target) {
        if (target.startsWith("#")) {
            PhantomRainWorldgenLib.LOGGER.warn(
                    "[BiomeReplace] {}[{}]: weighted_infuse target cannot be a tag, skipped", fileId, index);
            return null;
        }
        JsonArray arr = firstArray(obj, "subs", "replacements", "children");
        if (arr == null || arr.isEmpty()) {
            PhantomRainWorldgenLib.LOGGER.warn(
                    "[BiomeReplace] {}[{}]: weighted_infuse needs non-empty subs, skipped", fileId, index);
            return null;
        }
        List<WeightedEntry> entries = parseWeightedEntries(fileId, index, arr);
        if (entries.isEmpty()) {
            return null;
        }
        int parentWeight = 1;
        if (obj.has("parent_weight") && !obj.get("parent_weight").isJsonNull()) {
            try {
                parentWeight = obj.get("parent_weight").getAsInt();
            } catch (Exception e) {
                parentWeight = 1;
            }
        }
        if (parentWeight < 0) {
            parentWeight = 0;
        }
        return new RawRule(fileId, index, TYPE_WEIGHTED_INFUSE, dimension, target, null, entries, parentWeight);
    }

    private static RawSubRule parseSubBiome(ResourceLocation fileId, int index, JsonObject obj,
                                           String dimension, String target) {
        String biome = firstString(obj, "biome", "replacement", "to");
        if (biome == null || biome.isBlank()) {
            PhantomRainWorldgenLib.LOGGER.warn(
                    "[BiomeReplace] {}[{}]: sub_biome needs biome, skipped", fileId, index);
            return null;
        }
        biome = biome.trim();

        JsonObject criterion;
        if (obj.has("criterion") && obj.get("criterion").isJsonObject()) {
            criterion = obj.getAsJsonObject("criterion");
        } else if (obj.has("criteria") && obj.get("criteria").isJsonArray()) {
            criterion = new JsonObject();
            criterion.addProperty("type", "all_of");
            criterion.add("criteria", obj.get("criteria"));
        } else if (obj.has("preset")) {
            criterion = new JsonObject();
            criterion.addProperty("type", "preset");
            criterion.addProperty("preset", obj.get("preset").getAsString());
            if (obj.has("neighbor")) {
                criterion.add("neighbor", obj.get("neighbor"));
            }
            if (obj.has("max")) {
                criterion.add("max", obj.get("max"));
            }
            if (obj.has("min")) {
                criterion.add("min", obj.get("min"));
            }
        } else {
            PhantomRainWorldgenLib.LOGGER.warn(
                    "[BiomeReplace] {}[{}]: sub_biome needs criterion/preset, skipped", fileId, index);
            return null;
        }

        return new RawSubRule(fileId, index, dimension, target, biome, criterion);
    }

    private static List<WeightedEntry> parseWeightedEntries(ResourceLocation fileId, int index, JsonArray arr) {
        List<WeightedEntry> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if (!el.isJsonObject()) {
                PhantomRainWorldgenLib.LOGGER.warn(
                        "[BiomeReplace] {}[{}].entries[{}]: expected object, skipped", fileId, index, i);
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String biome = firstString(o, "biome", "id", "replacement");
            if (biome == null || biome.isBlank()) {
                PhantomRainWorldgenLib.LOGGER.warn(
                        "[BiomeReplace] {}[{}].entries[{}]: missing biome, skipped", fileId, index, i);
                continue;
            }
            biome = biome.trim();
            int weight = 1;
            if (o.has("weight") && !o.get("weight").isJsonNull()) {
                try {
                    weight = o.get("weight").getAsInt();
                } catch (Exception e) {
                    weight = 1;
                }
            }
            if (weight <= 0) {
                PhantomRainWorldgenLib.LOGGER.warn(
                        "[BiomeReplace] {}[{}].entries[{}]: weight must be positive, skipped", fileId, index, i);
                continue;
            }
            list.add(new WeightedEntry(biome, weight));
        }
        return list;
    }

    private static String getString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstString(JsonObject obj, String... keys) {
        for (String key : keys) {
            String v = getString(obj, key);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static JsonArray firstArray(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                return obj.getAsJsonArray(key);
            }
        }
        return null;
    }
}
