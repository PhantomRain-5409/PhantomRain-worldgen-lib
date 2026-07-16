package com.ptr5409.wglib.biomereplace.sub;

import net.minecraft.world.level.biome.Climate;

import java.util.Locale;

/**
 * 功能：气候噪声轴枚举（用于 value / deviation 条件）
 */
public enum ClimateParameterAxis {
    TEMPERATURE,
    HUMIDITY,
    CONTINENTALNESS,
    EROSION,
    DEPTH,
    WEIRDNESS,
    PEAKS_VALLEYS;

    public static ClimateParameterAxis parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing parameter");
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "temperature", "temp" -> TEMPERATURE;
            case "humidity", "humid" -> HUMIDITY;
            case "continentalness", "continental", "continents" -> CONTINENTALNESS;
            case "erosion" -> EROSION;
            case "depth" -> DEPTH;
            case "weirdness", "weird" -> WEIRDNESS;
            case "peaks_valleys", "peaksvalleys", "pv" -> PEAKS_VALLEYS;
            default -> throw new IllegalArgumentException("unknown parameter '" + raw + "'");
        };
    }

    public Climate.Parameter rangeOf(Climate.ParameterPoint point) {
        return switch (this) {
            case TEMPERATURE -> point.temperature();
            case HUMIDITY -> point.humidity();
            case CONTINENTALNESS -> point.continentalness();
            case EROSION -> point.erosion();
            case DEPTH -> point.depth();
            case WEIRDNESS, PEAKS_VALLEYS -> point.weirdness();
        };
    }

    public long valueOf(Climate.TargetPoint target) {
        return switch (this) {
            case TEMPERATURE -> target.temperature();
            case HUMIDITY -> target.humidity();
            case CONTINENTALNESS -> target.continentalness();
            case EROSION -> target.erosion();
            case DEPTH -> target.depth();
            case WEIRDNESS -> target.weirdness();
            case PEAKS_VALLEYS -> peaksValleys(target.weirdness());
        };
    }

    /** 与原版 DensityFunctions.getPeaksValleysNoise 等价的 long 近似 */
    public static long peaksValleys(long weirdness) {
        return 10000L - Math.abs(Math.abs(weirdness * 3L) - 20000L);
    }
}
