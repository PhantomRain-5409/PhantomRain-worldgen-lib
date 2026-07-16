package com.ptr5409.wglib.biomereplace;

import net.minecraft.world.level.biome.Climate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 功能：按权重沿气候轴切开 ParameterPoint，使加权/子群系在参数表上有面积占比
 */
public final class ClimateSplit {
    private ClimateSplit() {
    }

    public record AxisChoice(String name, Climate.Parameter param,
                             Function<Climate.Parameter, Climate.ParameterPoint> rebuild) {
    }

    public static AxisChoice chooseAxis(Climate.ParameterPoint point) {
        AxisChoice[] candidates = new AxisChoice[]{
                axis("weirdness", point.weirdness(), p -> rebuild(point, null, null, null, null, null, p)),
                axis("humidity", point.humidity(), p -> rebuild(point, null, p, null, null, null, null)),
                axis("temperature", point.temperature(), p -> rebuild(point, p, null, null, null, null, null)),
                axis("continentalness", point.continentalness(), p -> rebuild(point, null, null, p, null, null, null)),
                axis("erosion", point.erosion(), p -> rebuild(point, null, null, null, p, null, null))
        };
        AxisChoice best = null;
        long bestSpan = 0;
        for (AxisChoice c : candidates) {
            long span = c.param().max() - c.param().min();
            if (span > bestSpan) {
                bestSpan = span;
                best = c;
            }
        }
        return bestSpan > 0 ? best : null;
    }

    private static AxisChoice axis(String name, Climate.Parameter param,
                                   Function<Climate.Parameter, Climate.ParameterPoint> rebuild) {
        return new AxisChoice(name, param, rebuild);
    }

    private static Climate.ParameterPoint rebuild(Climate.ParameterPoint src,
                                                  Climate.Parameter temperature,
                                                  Climate.Parameter humidity,
                                                  Climate.Parameter continentalness,
                                                  Climate.Parameter erosion,
                                                  Climate.Parameter depth,
                                                  Climate.Parameter weirdness) {
        return new Climate.ParameterPoint(
                temperature != null ? temperature : src.temperature(),
                humidity != null ? humidity : src.humidity(),
                continentalness != null ? continentalness : src.continentalness(),
                erosion != null ? erosion : src.erosion(),
                depth != null ? depth : src.depth(),
                weirdness != null ? weirdness : src.weirdness(),
                src.offset()
        );
    }

    public static List<Climate.ParameterPoint> splitByWeights(Climate.ParameterPoint point, int[] weights) {
        if (weights.length == 0) {
            return List.of();
        }
        AxisChoice axis = chooseAxis(point);
        if (axis == null) {
            return null;
        }

        long totalWeight = 0;
        for (int w : weights) {
            totalWeight += Math.max(0, w);
        }
        if (totalWeight <= 0) {
            return null;
        }

        long min = axis.param().min();
        long max = axis.param().max();
        long span = max - min;
        if (span <= 0) {
            return null;
        }

        List<Climate.ParameterPoint> result = new ArrayList<>(weights.length);
        long cursor = min;
        long assigned = 0;
        for (int i = 0; i < weights.length; i++) {
            int w = Math.max(0, weights[i]);
            long next;
            if (i == weights.length - 1) {
                next = max;
            } else {
                assigned += w;
                next = min + (span * assigned) / totalWeight;
                if (next < cursor) {
                    next = cursor;
                }
                if (next > max) {
                    next = max;
                }
            }
            if (next <= cursor && cursor < max) {
                next = Math.min(max, cursor + 1);
            }
            Climate.Parameter slice = new Climate.Parameter(cursor, next);
            result.add(axis.rebuild().apply(slice));
            cursor = next;
        }
        return result;
    }
}
