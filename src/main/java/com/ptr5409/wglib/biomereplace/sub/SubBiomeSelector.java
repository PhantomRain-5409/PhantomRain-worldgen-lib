package com.ptr5409.wglib.biomereplace.sub;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能：用预构建气候索引执行运行时子群系选择
 */
public final class SubBiomeSelector {
    private final String dimension;
    private final List<SubBiomeRule> rules;
    private final List<Pair<Climate.ParameterPoint, Holder<Biome>>> originalValues;
    private final List<Pair<Climate.ParameterPoint, Holder<Biome>>> effectiveValues;
    private final long seed;

    private volatile Climate.ParameterList<Candidate> originalIndex;
    private volatile Map<Holder<Biome>, TargetIndex> targetIndexes = Map.of();

    public SubBiomeSelector(String dimension,
                            Climate.ParameterList<Holder<Biome>> originalParameters,
                            Climate.ParameterList<Holder<Biome>> parameters,
                            List<SubBiomeRule> rules,
                            long seed) {
        this.dimension = dimension;
        this.rules = rules.stream().filter(rule -> rule.matchesDimension(dimension)).toList();
        this.originalValues = new ArrayList<>(originalParameters.values());
        this.effectiveValues = new ArrayList<>(parameters.values());
        this.seed = seed;
        rebuildIndexes();
    }

    public Holder<Biome> select(Holder<Biome> primary, Climate.TargetPoint target, int x, int y, int z) {
        TargetIndex index = targetIndexes.get(primary);
        if (index == null) {
            return primary;
        }

        Candidate primaryCandidate = index.primary().findValue(target);
        Candidate neighborCandidate = index.others() == null ? null : index.others().findValue(target);
        Candidate originalCandidate = originalIndex.findValue(target);
        long primaryDistance = fitness(primaryCandidate.point(), target);
        long neighborDistance = neighborCandidate == null
                ? Long.MAX_VALUE
                : fitness(neighborCandidate.point(), target);

        SubBiomeContext context = new SubBiomeContext(
                primary,
                neighborCandidate == null ? null : neighborCandidate.biome(),
                primaryDistance,
                neighborDistance,
                target,
                primaryCandidate.point(),
                originalCandidate.biome(),
                x,
                y,
                z,
                seed
        );

        for (SubBiomeRule rule : index.rules()) {
            if (rule.criterion().matches(context)) {
                return rule.biome();
            }
        }
        return primary;
    }

    public List<Holder<Biome>> possibleBiomes() {
        return rules.stream().map(SubBiomeRule::biome).distinct().toList();
    }

    public synchronized void appendParameters(
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> original,
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> effective) {
        originalValues.addAll(original);
        effectiveValues.addAll(effective);
        rebuildIndexes();
    }

    private void rebuildIndexes() {
        this.originalIndex = buildIndex(originalValues);

        Map<Holder<Biome>, List<SubBiomeRule>> grouped = new LinkedHashMap<>();
        for (SubBiomeRule rule : rules) {
            grouped.computeIfAbsent(rule.target(), key -> new ArrayList<>()).add(rule);
        }

        Map<Holder<Biome>, TargetIndex> built = new HashMap<>();
        for (Map.Entry<Holder<Biome>, List<SubBiomeRule>> entry : grouped.entrySet()) {
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> primary = new ArrayList<>();
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> others = new ArrayList<>();
            for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : effectiveValues) {
                (entry.getKey().equals(pair.getSecond()) ? primary : others).add(pair);
            }
            if (primary.isEmpty()) {
                continue;
            }
            built.put(entry.getKey(), new TargetIndex(
                    buildIndex(primary),
                    others.isEmpty() ? null : buildIndex(others),
                    List.copyOf(entry.getValue())
            ));
        }
        this.targetIndexes = Map.copyOf(built);
    }

    private static Climate.ParameterList<Candidate> buildIndex(
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> values) {
        List<Pair<Climate.ParameterPoint, Candidate>> candidates = new ArrayList<>(values.size());
        for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : values) {
            candidates.add(Pair.of(pair.getFirst(), new Candidate(pair.getFirst(), pair.getSecond())));
        }
        return new Climate.ParameterList<>(candidates);
    }

    private static long fitness(Climate.ParameterPoint point, Climate.TargetPoint target) {
        long sum = square(point.temperature().distance(target.temperature()));
        sum += square(point.humidity().distance(target.humidity()));
        sum += square(point.continentalness().distance(target.continentalness()));
        sum += square(point.erosion().distance(target.erosion()));
        sum += square(point.depth().distance(target.depth()));
        sum += square(point.weirdness().distance(target.weirdness()));
        sum += square(point.offset());
        return sum;
    }

    private static long square(long value) {
        return value * value;
    }

    private record Candidate(Climate.ParameterPoint point, Holder<Biome> biome) {
    }

    private record TargetIndex(
            Climate.ParameterList<Candidate> primary,
            Climate.ParameterList<Candidate> others,
            List<SubBiomeRule> rules
    ) {
    }
}
