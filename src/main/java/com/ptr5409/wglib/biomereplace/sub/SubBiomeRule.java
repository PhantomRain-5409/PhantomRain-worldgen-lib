package com.ptr5409.wglib.biomereplace.sub;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * 功能：单条子群系覆盖规则
 */
public record SubBiomeRule(
        String dimension,
        Holder<Biome> target,
        Holder<Biome> biome,
        SubCriterion criterion
) {
    public boolean matchesDimension(String dim) {
        return dimension == null || dimension.equals(dim);
    }
}
