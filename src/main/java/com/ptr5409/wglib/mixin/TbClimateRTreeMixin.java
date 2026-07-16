package com.ptr5409.wglib.mixin;

import com.mojang.datafixers.util.Pair;
import com.ptr5409.wglib.biomereplace.BiomeReplaceEngine;
import com.ptr5409.wglib.biomereplace.TbInjectContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * 功能：在 TerraBlender 初始化窗口内改写 Climate.RTree 的 pair 输入
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree")
public abstract class TbClimateRTreeMixin {
    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true)
    private static <T> List<Pair<Climate.ParameterPoint, T>> ptrlib$rewriteTbPairs(
            List<Pair<Climate.ParameterPoint, T>> pairs) {
        if (!TbInjectContext.isActive() || !TbInjectContext.shouldTransformTree()
                || pairs == null || pairs.isEmpty()) {
            return pairs;
        }
        Registry<Biome> registry = TbInjectContext.biomeRegistry();
        if (registry == null) {
            return pairs;
        }

        Object sample = pairs.get(0).getSecond();
        if (!(sample instanceof Holder<?>)) {
            return pairs;
        }

        @SuppressWarnings("unchecked")
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> typed =
                (List<Pair<Climate.ParameterPoint, Holder<Biome>>>) (List<?>) pairs;
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> rewritten =
                BiomeReplaceEngine.transformTbPairList(typed, registry, TbInjectContext.dimensionId());
        TbInjectContext.appendSubBiomeParameters(typed, rewritten);
        if (rewritten == typed) {
            return pairs;
        }
        @SuppressWarnings("unchecked")
        List<Pair<Climate.ParameterPoint, T>> cast =
                (List<Pair<Climate.ParameterPoint, T>>) (List<?>) rewritten;
        return cast;
    }
}
