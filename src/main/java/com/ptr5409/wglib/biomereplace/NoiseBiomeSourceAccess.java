package com.ptr5409.wglib.biomereplace;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import com.ptr5409.wglib.biomereplace.sub.SubBiomeSelector;
import com.mojang.datafixers.util.Pair;

import java.util.List;

/**
 * 功能：读写 MultiNoise 群系源的气候参数表（运行时覆盖）
 */
public interface NoiseBiomeSourceAccess {
    Climate.ParameterList<Holder<Biome>> ptrlib$getNoiseParameters();

    void ptrlib$setNoiseParameters(Climate.ParameterList<Holder<Biome>> parameters);

    void ptrlib$setSubBiomeSelector(SubBiomeSelector selector);

    void ptrlib$appendSubBiomeParameters(
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> original,
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> effective);
}
