package com.ptr5409.wglib.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 功能：按已安装模组选择性启用兼容 Mixin
 */
public class WgMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("ptrlib_wg");
    private static final String TERRABLENDER_ID = "terrablender";
    private boolean terrablenderLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        this.terrablenderLoaded = isModLoaded(TERRABLENDER_ID);
        if (this.terrablenderLoaded) {
            LOGGER.info("[BiomeReplace] TerraBlender detected, enabling TB biome replace hooks");
        }
    }

    private static boolean isModLoaded(String modId) {
        try {
            LoadingModList list = LoadingModList.get();
            if (list == null) {
                return false;
            }
            for (ModInfo info : list.getMods()) {
                if (modId.equals(info.getModId())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (simple.startsWith("Tb")) {
            return this.terrablenderLoaded;
        }
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
