package com.ptr5409.wglib.biomereplace.sub;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/**
 * 功能：运行时子群系判定上下文
 */
public record SubBiomeContext(
        Holder<Biome> primary,
        Holder<Biome> neighbor,
        long primaryDistance,
        long neighborDistance,
        Climate.TargetPoint targetPoint,
        Climate.ParameterPoint primaryPoint,
        Holder<Biome> original,
        int x,
        int y,
        int z,
        long seed
) {
    public float edgeRatio() {
        if (neighbor == null || neighborDistance <= 0L) {
            return 1.0f;
        }
        long gap = neighborDistance - primaryDistance;
        if (gap < 0L) {
            gap = 0L;
        }
        return (float) gap / (float) neighborDistance;
    }

    public float centerRatio(ClimateParameterAxis axis) {
        if (primaryPoint == null || targetPoint == null) {
            return 1.0f;
        }
        Climate.Parameter range = axis.rangeOf(primaryPoint);
        long center = (range.min() + range.max()) / 2L;
        long value = axis.valueOf(targetPoint);
        long half = Math.max(1L, (range.max() - range.min()) / 2L);
        long delta = Math.abs(value - center);
        return Math.min(1.0f, (float) delta / (float) half);
    }

    public float centerRatio() {
        if (primaryPoint == null || targetPoint == null) {
            return 1.0f;
        }
        double sum = square(distanceFromCenter(primaryPoint.temperature(), targetPoint.temperature()));
        sum += square(distanceFromCenter(primaryPoint.humidity(), targetPoint.humidity()));
        sum += square(distanceFromCenter(primaryPoint.continentalness(), targetPoint.continentalness()));
        sum += square(distanceFromCenter(primaryPoint.erosion(), targetPoint.erosion()));
        sum += square(distanceFromCenter(primaryPoint.depth(), targetPoint.depth()));
        sum += square(distanceFromCenter(primaryPoint.weirdness(), targetPoint.weirdness()));
        sum += square(primaryPoint.offset());
        return (float) (Math.sqrt(sum) / 10000.0D);
    }

    private static double distanceFromCenter(Climate.Parameter range, long value) {
        long center = (range.min() + range.max()) / 2L;
        return Math.abs(value - center);
    }

    private static double square(double value) {
        return value * value;
    }

    public float patchNoise(float scale, long salt) {
        double safeScale = Math.max(4.0D, scale);
        double fx = x * 4.0D / safeScale;
        double fy = y * 4.0D / safeScale;
        double fz = z * 4.0D / safeScale;
        int x0 = floor(fx);
        int y0 = floor(fy);
        int z0 = floor(fz);
        double tx = smooth(fx - x0);
        double ty = smooth(fy - y0);
        double tz = smooth(fz - z0);
        double bottom = lerp(
                lerp(lattice(x0, y0, z0, salt), lattice(x0 + 1, y0, z0, salt), tx),
                lerp(lattice(x0, y0, z0 + 1, salt), lattice(x0 + 1, y0, z0 + 1, salt), tx),
                tz);
        double top = lerp(
                lerp(lattice(x0, y0 + 1, z0, salt), lattice(x0 + 1, y0 + 1, z0, salt), tx),
                lerp(lattice(x0, y0 + 1, z0 + 1, salt), lattice(x0 + 1, y0 + 1, z0 + 1, salt), tx),
                tz);
        return (float) lerp(bottom, top, ty);
    }

    private double lattice(int latticeX, int latticeY, int latticeZ, long salt) {
        long value = seed ^ salt
                ^ (latticeX * 341873128712L)
                ^ (latticeZ * 132897987541L)
                ^ (latticeY * 42317861L);
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static int floor(double value) {
        int rounded = (int) value;
        return value < rounded ? rounded - 1 : rounded;
    }

    private static double smooth(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

}
