package com.ptr5409.wglib.biomereplace;

import com.mojang.datafixers.util.Pair;
import com.ptr5409.wglib.PhantomRainWorldgenLib;
import com.ptr5409.wglib.biomereplace.sub.SubBiomeRule;
import com.ptr5409.wglib.biomereplace.sub.SubBiomeSelector;
import com.ptr5409.wglib.biomereplace.sub.SubCriteria;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 功能：解析数据包规则并改写 MultiNoise 群系源参数表
 */
public final class BiomeReplaceEngine {
    /** TerraBlender 用的“回退原版区域”占位群系，移除群系时写入该占位而非丢弃条目 */
    public static final ResourceKey<Biome> TB_DEFERRED_PLACEHOLDER =
            ResourceKey.create(Registries.BIOME, new ResourceLocation("terrablender", "deferred_placeholder"));

    private static Map<Holder<Biome>, Holder<Biome>> globalDirect = Map.of();
    private static Map<String, Map<Holder<Biome>, Holder<Biome>>> dimDirect = Map.of();

    private static Map<Holder<Biome>, List<WeightedBiome>> globalSplit = Map.of();
    private static Map<String, Map<Holder<Biome>, List<WeightedBiome>>> dimSplit = Map.of();

    public record WeightedBiome(Holder<Biome> biome, int weight) {
    }

    private BiomeReplaceEngine() {
    }

    public static boolean hasAnyRules() {
        return !globalDirect.isEmpty() || !dimDirect.isEmpty()
                || !globalSplit.isEmpty() || !dimSplit.isEmpty();
    }

    public static void apply(ResourceManager resourceManager,
                             Registry<Biome> biomeRegistry,
                             Registry<LevelStem> stemRegistry,
                             long seed) {
        BiomeReplaceData.LoadResult loaded = BiomeReplaceData.load(resourceManager);
        prepareRules(loaded.staticRules(), biomeRegistry, stemRegistry);
        List<SubBiomeRule> subRules = prepareSubRules(loaded.subRules(), biomeRegistry, stemRegistry);

        if (!hasAnyRules() && subRules.isEmpty()) {
            PhantomRainWorldgenLib.LOGGER.info("[BiomeReplace] No resolved rules, nothing to replace");
            return;
        }

        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : stemRegistry.entrySet()) {
            ResourceLocation levelId = entry.getKey().location();
            LevelStem stem = entry.getValue();

            if (!(stem.generator() instanceof NoiseBasedChunkGenerator generator)
                    || !(generator.getBiomeSource() instanceof MultiNoiseBiomeSource)) {
                PhantomRainWorldgenLib.LOGGER.debug("[BiomeReplace] Skipping non-MultiNoise {}", levelId);
                continue;
            }

            Map<Holder<Biome>, Holder<Biome>> direct = effectiveDirect(levelId.toString());
            Map<Holder<Biome>, List<WeightedBiome>> split = effectiveSplit(levelId.toString());
            boolean hasSubRules = hasSubRules(subRules, levelId.toString());
            if (direct.isEmpty() && split.isEmpty() && !hasSubRules) {
                continue;
            }

            NoiseBiomeSourceAccess access = (NoiseBiomeSourceAccess) generator.getBiomeSource();
            Climate.ParameterList<Holder<Biome>> parameters = access.ptrlib$getNoiseParameters();

            Climate.ParameterList<Holder<Biome>> effectiveParameters = parameters;
            if (!direct.isEmpty() || !split.isEmpty()) {
                RebuildResult result = rebuildPairs(parameters.values(), direct, split, null, false);
                if (result.changed()) {
                    if (result.pairs().isEmpty()) {
                        PhantomRainWorldgenLib.LOGGER.warn(
                                "[BiomeReplace] Rules would remove every biome in {}, leaving untouched",
                                levelId);
                    } else {
                        effectiveParameters = new Climate.ParameterList<>(result.pairs());
                        access.ptrlib$setNoiseParameters(effectiveParameters);
                        if (result.unsplitFallback() > 0) {
                            PhantomRainWorldgenLib.LOGGER.info(
                                    "[BiomeReplace] Applied replacements in {} ({} entry(ies) used heaviest-weight fallback)",
                                    levelId, result.unsplitFallback());
                        } else {
                            PhantomRainWorldgenLib.LOGGER.info("[BiomeReplace] Applied replacements in {}", levelId);
                        }
                    }
                }
            }

            if (hasSubRules) {
                access.ptrlib$setSubBiomeSelector(new SubBiomeSelector(
                        levelId.toString(), parameters, effectiveParameters, subRules, seed));
                PhantomRainWorldgenLib.LOGGER.info("[BiomeReplace] Enabled runtime sub-biomes in {}", levelId);
            }
        }
    }

    /**
     * TerraBlender 区域参数表转换：direct / weighted / weighted_infuse 规则，移除改为 deferred 占位。
     */
    public static List<Pair<Climate.ParameterPoint, Holder<Biome>>> transformTbPairList(
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> original,
            Registry<Biome> biomeRegistry,
            String dimensionId) {
        if (original == null || original.isEmpty() || !hasAnyRules()) {
            return original;
        }

        Map<Holder<Biome>, Holder<Biome>> direct = effectiveDirect(dimensionId);
        Map<Holder<Biome>, List<WeightedBiome>> split = effectiveSplit(dimensionId);
        if (direct.isEmpty() && split.isEmpty()) {
            return original;
        }

        Holder<Biome> placeholder = null;
        Optional<Holder.Reference<Biome>> ph = biomeRegistry.getHolder(TB_DEFERRED_PLACEHOLDER);
        if (ph.isPresent()) {
            placeholder = ph.get();
        } else {
            PhantomRainWorldgenLib.LOGGER.warn(
                    "[BiomeReplace] TerraBlender deferred placeholder missing; removals will drop pairs");
        }

        RebuildResult result = rebuildPairs(original, direct, split, placeholder, true);
        return result.changed() ? result.pairs() : original;
    }

    /**
     * TerraBlender 延迟群系列表（/locate biome 等）：替换并过滤移除项。
     */
    public static List<Holder<Biome>> transformTbDeferredList(List<Holder<Biome>> original, String dimensionId) {
        if (original == null || original.isEmpty() || !hasAnyRules()) {
            return original;
        }

        Map<Holder<Biome>, Holder<Biome>> direct = effectiveDirect(dimensionId);
        Map<Holder<Biome>, List<WeightedBiome>> split = effectiveSplit(dimensionId);
        if (direct.isEmpty() && split.isEmpty()) {
            return original;
        }

        List<Holder<Biome>> out = new ArrayList<>(original.size());
        boolean changed = false;
        for (Holder<Biome> biome : original) {
            if (split.containsKey(biome)) {
                for (WeightedBiome w : split.get(biome)) {
                    out.add(w.biome());
                }
                changed = true;
                continue;
            }
            if (direct.containsKey(biome)) {
                Holder<Biome> replaced = direct.get(biome);
                changed = true;
                if (replaced != null) {
                    out.add(replaced);
                }
                continue;
            }
            out.add(biome);
        }
        return changed ? List.copyOf(out) : original;
    }

    private record RebuildResult(
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> pairs,
            boolean changed,
            int unsplitFallback) {
    }

    private static RebuildResult rebuildPairs(
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> source,
            Map<Holder<Biome>, Holder<Biome>> direct,
            Map<Holder<Biome>, List<WeightedBiome>> split,
            Holder<Biome> removalPlaceholder,
            boolean usePlaceholderOnRemove) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> rebuilt = new ArrayList<>();
        boolean changed = false;
        int unsplitFallback = 0;

        for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : source) {
            Holder<Biome> original = pair.getSecond();
            Climate.ParameterPoint point = pair.getFirst();

            if (split.containsKey(original)) {
                List<WeightedBiome> weights = split.get(original);
                List<Pair<Climate.ParameterPoint, Holder<Biome>>> expanded =
                        expandWeighted(point, weights);
                if (expanded == null) {
                    Holder<Biome> fallback = heaviest(weights);
                    rebuilt.add(Pair.of(point, fallback));
                    changed = true;
                    unsplitFallback++;
                } else {
                    rebuilt.addAll(expanded);
                    changed = true;
                }
                continue;
            }

            if (direct.containsKey(original)) {
                Holder<Biome> replaced = direct.get(original);
                changed = true;
                if (replaced == null) {
                    if (usePlaceholderOnRemove && removalPlaceholder != null) {
                        rebuilt.add(Pair.of(point, removalPlaceholder));
                    }
                    continue;
                }
                rebuilt.add(Pair.of(point, replaced));
                continue;
            }

            rebuilt.add(pair);
        }

        return new RebuildResult(rebuilt, changed, unsplitFallback);
    }

    private static List<Pair<Climate.ParameterPoint, Holder<Biome>>> expandWeighted(
            Climate.ParameterPoint point, List<WeightedBiome> weights) {
        int[] w = new int[weights.size()];
        for (int i = 0; i < weights.size(); i++) {
            w[i] = weights.get(i).weight();
        }
        List<Climate.ParameterPoint> slices = ClimateSplit.splitByWeights(point, w);
        if (slices == null || slices.size() != weights.size()) {
            return null;
        }
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> out = new ArrayList<>(weights.size());
        for (int i = 0; i < weights.size(); i++) {
            out.add(Pair.of(slices.get(i), weights.get(i).biome()));
        }
        return out;
    }

    private static Holder<Biome> heaviest(List<WeightedBiome> weights) {
        WeightedBiome best = weights.get(0);
        for (WeightedBiome w : weights) {
            if (w.weight() > best.weight()) {
                best = w;
            }
        }
        return best.biome();
    }

    private static Map<Holder<Biome>, Holder<Biome>> effectiveDirect(String dim) {
        if (dim != null) {
            Map<Holder<Biome>, Holder<Biome>> d = dimDirect.get(dim);
            if (d != null) {
                return d;
            }
        }
        return globalDirect;
    }

    private static Map<Holder<Biome>, List<WeightedBiome>> effectiveSplit(String dim) {
        if (dim != null) {
            Map<Holder<Biome>, List<WeightedBiome>> d = dimSplit.get(dim);
            if (d != null) {
                return d;
            }
        }
        return globalSplit;
    }

    private static void prepareRules(List<BiomeReplaceData.RawRule> rawRules,
                                     Registry<Biome> biomeRegistry,
                                     Registry<LevelStem> stemRegistry) {
        Map<Holder<Biome>, Holder<Biome>> gDirect = new HashMap<>();
        Map<String, Map<Holder<Biome>, Holder<Biome>>> pDirect = new HashMap<>();
        Map<Holder<Biome>, List<WeightedBiome>> gSplit = new HashMap<>();
        Map<String, Map<Holder<Biome>, List<WeightedBiome>>> pSplit = new HashMap<>();

        Set<String> knownDimensions = stemRegistry.keySet().stream()
                .map(ResourceLocation::toString)
                .collect(Collectors.toSet());

        int nDirect = 0;
        int nWeighted = 0;
        int nInfuse = 0;
        int nTag = 0;
        int ignored = 0;

        for (BiomeReplaceData.RawRule rule : rawRules) {
            try {
                String dimKey = null;
                if (rule.dimension() != null) {
                    ResourceLocation dimId = ResourceLocation.tryParse(rule.dimension());
                    if (dimId == null) {
                        throw new IllegalArgumentException(
                                "Invalid dimension id '" + rule.dimension() + "'");
                    }
                    dimKey = dimId.toString();
                    if (!knownDimensions.contains(dimKey)) {
                        throw new IllegalArgumentException(
                                "Dimension '" + dimKey + "' does not exist");
                    }
                }

                Map<Holder<Biome>, Holder<Biome>> directMap =
                        dimKey == null ? gDirect : pDirect.computeIfAbsent(dimKey, k -> new HashMap<>());
                Map<Holder<Biome>, List<WeightedBiome>> splitMap =
                        dimKey == null ? gSplit : pSplit.computeIfAbsent(dimKey, k -> new HashMap<>());

                if (rule.isDirect()) {
                    Holder<Biome> replacement = resolveOptionalBiome(rule.replacement(), biomeRegistry);
                    List<Holder<Biome>> targets = resolveTargets(rule.target(), biomeRegistry);
                    boolean tag = rule.target().startsWith("#");
                    for (Holder<Biome> t : targets) {
                        if (tag && directMap.containsKey(t)) {
                            continue;
                        }
                        directMap.put(t, replacement);
                        splitMap.remove(t);
                    }
                    if (tag) {
                        nTag++;
                    } else {
                        nDirect++;
                    }
                } else if (rule.isWeighted()) {
                    List<WeightedBiome> entries = resolveWeighted(rule.entries(), biomeRegistry);
                    List<Holder<Biome>> targets = resolveTargets(rule.target(), biomeRegistry);
                    boolean tag = rule.target().startsWith("#");
                    for (Holder<Biome> t : targets) {
                        if (tag && (directMap.containsKey(t) || splitMap.containsKey(t))) {
                            continue;
                        }
                        directMap.remove(t);
                        splitMap.put(t, entries);
                    }
                    if (tag) {
                        nTag++;
                    } else {
                        nWeighted++;
                    }
                } else if (rule.isWeightedInfuse()) {
                    Holder<Biome> parent = resolveBiome(rule.target(), biomeRegistry);
                    List<WeightedBiome> subs = resolveWeighted(rule.entries(), biomeRegistry);
                    List<WeightedBiome> combined = arrangeParentCenter(parent, rule.parentWeight(), subs);
                    if (combined.isEmpty()) {
                        throw new IllegalArgumentException("weighted_infuse produced empty weight table");
                    }
                    directMap.remove(parent);
                    splitMap.put(parent, combined);
                    nInfuse++;
                } else {
                    ignored++;
                }
            } catch (Exception e) {
                PhantomRainWorldgenLib.LOGGER.warn(
                        "[BiomeReplace] {}[{}]: {}, ignoring rule",
                        rule.sourceFile(), rule.index(), e.getMessage());
                ignored++;
            }
        }

        globalDirect = freezeDirect(gDirect);
        globalSplit = freezeSplit(gSplit);
        dimDirect = mergeDirect(gDirect, pDirect, knownDimensions);
        dimSplit = mergeSplit(gSplit, pSplit, knownDimensions, pDirect.keySet());

        PhantomRainWorldgenLib.LOGGER.info(
                "[BiomeReplace] Resolved direct={}, weighted={}, weighted_infuse={}, tag-expand={}, ignored={}",
                nDirect, nWeighted, nInfuse, nTag, ignored);
    }

    private static List<SubBiomeRule> prepareSubRules(List<BiomeReplaceData.RawSubRule> rawRules,
                                                      Registry<Biome> biomeRegistry,
                                                      Registry<LevelStem> stemRegistry) {
        Set<String> knownDimensions = stemRegistry.keySet().stream()
                .map(ResourceLocation::toString)
                .collect(Collectors.toSet());
        List<SubBiomeRule> resolved = new ArrayList<>();

        for (BiomeReplaceData.RawSubRule rule : rawRules) {
            try {
                String dim = null;
                if (rule.dimension() != null) {
                    ResourceLocation dimId = ResourceLocation.tryParse(rule.dimension());
                    if (dimId == null || !knownDimensions.contains(dimId.toString())) {
                        throw new IllegalArgumentException("Dimension '" + rule.dimension() + "' does not exist");
                    }
                    dim = dimId.toString();
                }

                List<Holder<Biome>> targets = resolveTargets(rule.target(), biomeRegistry);
                Holder<Biome> biome = resolveBiome(rule.biome(), biomeRegistry);
                var criterion = SubCriteria.parse(rule.criterion(), biomeRegistry);
                for (Holder<Biome> target : targets) {
                    resolved.add(new SubBiomeRule(dim, target, biome, criterion));
                }
            } catch (Exception e) {
                PhantomRainWorldgenLib.LOGGER.warn(
                        "[BiomeReplace] {}[{}]: {}, ignoring sub_biome rule",
                        rule.sourceFile(), rule.index(), e.getMessage());
            }
        }

        PhantomRainWorldgenLib.LOGGER.info(
                "[BiomeReplace] Resolved {} runtime sub_biome rule(s)", resolved.size());
        return List.copyOf(resolved);
    }

    private static boolean hasSubRules(List<SubBiomeRule> rules, String dimension) {
        for (SubBiomeRule rule : rules) {
            if (rule.matchesDimension(dimension)) {
                return true;
            }
        }
        return false;
    }

    private static List<WeightedBiome> arrangeParentCenter(Holder<Biome> parent, int parentWeight,
                                                           List<WeightedBiome> subs) {
        if (parentWeight <= 0) {
            return List.copyOf(subs);
        }
        if (subs.isEmpty()) {
            return List.of(new WeightedBiome(parent, parentWeight));
        }
        int mid = (subs.size() + 1) / 2;
        List<WeightedBiome> out = new ArrayList<>(subs.size() + 1);
        out.addAll(subs.subList(0, mid));
        out.add(new WeightedBiome(parent, parentWeight));
        out.addAll(subs.subList(mid, subs.size()));
        return out;
    }

    private static Map<Holder<Biome>, Holder<Biome>> freezeDirect(Map<Holder<Biome>, Holder<Biome>> map) {
        return Collections.unmodifiableMap(new HashMap<>(map));
    }

    private static Map<Holder<Biome>, List<WeightedBiome>> freezeSplit(
            Map<Holder<Biome>, List<WeightedBiome>> map) {
        Map<Holder<Biome>, List<WeightedBiome>> copy = new HashMap<>();
        for (Map.Entry<Holder<Biome>, List<WeightedBiome>> e : map.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Map<Holder<Biome>, Holder<Biome>>> mergeDirect(
            Map<Holder<Biome>, Holder<Biome>> global,
            Map<String, Map<Holder<Biome>, Holder<Biome>>> perDim,
            Set<String> knownDimensions) {
        Map<String, Map<Holder<Biome>, Holder<Biome>>> out = new HashMap<>();
        Set<String> dims = new HashSet<>(perDim.keySet());
        for (String dim : dims) {
            Map<Holder<Biome>, Holder<Biome>> merged = new HashMap<>(global);
            merged.putAll(perDim.get(dim));
            out.put(dim, freezeDirect(merged));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, Map<Holder<Biome>, List<WeightedBiome>>> mergeSplit(
            Map<Holder<Biome>, List<WeightedBiome>> global,
            Map<String, Map<Holder<Biome>, List<WeightedBiome>>> perDim,
            Set<String> knownDimensions,
            Set<String> directDims) {
        Map<String, Map<Holder<Biome>, List<WeightedBiome>>> out = new HashMap<>();
        Set<String> dims = new HashSet<>();
        dims.addAll(perDim.keySet());
        dims.addAll(directDims);
        for (String dim : dims) {
            Map<Holder<Biome>, List<WeightedBiome>> merged = new HashMap<>(global);
            Map<Holder<Biome>, List<WeightedBiome>> local = perDim.get(dim);
            if (local != null) {
                merged.putAll(local);
            }
            out.put(dim, freezeSplit(merged));
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<Holder<Biome>> resolveTargets(String target, Registry<Biome> registry) {
        if (target.startsWith("#")) {
            TagKey<Biome> tag = resolveTag(target.substring(1));
            List<Holder<Biome>> list = new ArrayList<>();
            for (Holder<Biome> h : registry.getTagOrEmpty(tag)) {
                list.add(h);
            }
            if (list.isEmpty()) {
                throw new IllegalArgumentException("Biome tag '" + target + "' is empty or missing");
            }
            return list;
        }
        return List.of(resolveBiome(target, registry));
    }

    private static List<WeightedBiome> resolveWeighted(List<BiomeReplaceData.WeightedEntry> entries,
                                                       Registry<Biome> registry) {
        List<WeightedBiome> list = new ArrayList<>(entries.size());
        for (BiomeReplaceData.WeightedEntry e : entries) {
            list.add(new WeightedBiome(resolveBiome(e.biome(), registry), e.weight()));
        }
        return list;
    }

    private static Holder<Biome> resolveOptionalBiome(String id, Registry<Biome> registry) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return resolveBiome(id, registry);
    }

    private static Holder<Biome> resolveBiome(String id, Registry<Biome> registry) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            throw new IllegalArgumentException("Invalid biome id '" + id + "'");
        }
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, loc);
        Optional<Holder.Reference<Biome>> holder = registry.getHolder(key);
        if (holder.isEmpty()) {
            throw new IllegalArgumentException("Biome '" + id + "' does not exist");
        }
        return holder.get();
    }

    private static TagKey<Biome> resolveTag(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            throw new IllegalArgumentException("Invalid biome tag '#" + id + "'");
        }
        return TagKey.create(Registries.BIOME, loc);
    }
}
