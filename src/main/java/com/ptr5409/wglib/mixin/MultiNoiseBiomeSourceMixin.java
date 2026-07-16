package com.ptr5409.wglib.mixin;

import com.mojang.datafixers.util.Either;
import com.ptr5409.wglib.biomereplace.NoiseBiomeSourceAccess;
import com.ptr5409.wglib.biomereplace.sub.SubBiomeSelector;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;
import java.util.List;
import com.mojang.datafixers.util.Pair;

/**
 * 功能：为 MultiNoise 群系源提供运行时气候参数表覆盖
 */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin implements NoiseBiomeSourceAccess {
    @Shadow
    @Final
    private Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;

    @Unique
    private Climate.ParameterList<Holder<Biome>> ptrlib$runtimeParameters;

    @Unique
    private SubBiomeSelector ptrlib$subBiomeSelector;

    @Inject(method = "parameters", at = @At("HEAD"), cancellable = true)
    private void ptrlib$useRuntimeParameters(CallbackInfoReturnable<Climate.ParameterList<Holder<Biome>>> cir) {
        if (this.ptrlib$runtimeParameters != null) {
            cir.setReturnValue(this.ptrlib$runtimeParameters);
        }
    }

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("RETURN"), cancellable = true)
    private void ptrlib$applySubBiome(int x, int y, int z, Climate.Sampler sampler,
                                     CallbackInfoReturnable<Holder<Biome>> cir) {
        if (this.ptrlib$subBiomeSelector != null) {
            cir.setReturnValue(this.ptrlib$subBiomeSelector.select(
                    cir.getReturnValue(), sampler.sample(x, y, z), x, y, z));
        }
    }

    @Inject(method = "collectPossibleBiomes", at = @At("RETURN"), cancellable = true)
    private void ptrlib$appendSubBiomes(CallbackInfoReturnable<Stream<Holder<Biome>>> cir) {
        if (this.ptrlib$subBiomeSelector != null) {
            cir.setReturnValue(Stream.concat(
                    cir.getReturnValue(), this.ptrlib$subBiomeSelector.possibleBiomes().stream()));
        }
    }

    @Override
    public Climate.ParameterList<Holder<Biome>> ptrlib$getNoiseParameters() {
        if (this.ptrlib$runtimeParameters != null) {
            return this.ptrlib$runtimeParameters;
        }
        return this.parameters.map(
                list -> list,
                holder -> holder.value().parameters()
        );
    }

    @Override
    public void ptrlib$setNoiseParameters(Climate.ParameterList<Holder<Biome>> newParameters) {
        this.ptrlib$runtimeParameters = newParameters;
    }

    @Override
    public void ptrlib$setSubBiomeSelector(SubBiomeSelector selector) {
        this.ptrlib$subBiomeSelector = selector;
    }

    @Override
    public void ptrlib$appendSubBiomeParameters(
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> original,
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> effective) {
        if (this.ptrlib$subBiomeSelector != null) {
            this.ptrlib$subBiomeSelector.appendParameters(original, effective);
        }
    }
}
