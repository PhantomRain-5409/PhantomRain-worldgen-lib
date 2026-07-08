package com.ptr5409.wglib;

import com.ptr5409.wglib.config.DimResetConfig;
import com.ptr5409.wglib.worldgen.WGChunkGenerators;
import com.ptr5409.wglib.worldgen.WGPlacementTypes;
import com.ptr5409.wglib.worldgen.WGStructurePieceTypes;
import com.ptr5409.wglib.worldgen.WGStructureTypes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(PhantomRainWorldgenLib.MOD_ID)
public class PhantomRainWorldgenLib {
    public static final String MOD_ID = "ptrlib_wg";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public PhantomRainWorldgenLib() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册原有的 Worldgen 内容
        WGStructureTypes.STRUCTURE_TYPES.register(modBus);
        WGStructurePieceTypes.PIECE_TYPES.register(modBus);
        WGPlacementTypes.STRUCTURE_PLACEMENT_TYPES.register(modBus);
        WGChunkGenerators.CHUNK_GENERATORS.register(modBus);

        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, DimResetConfig.SPEC, "ptrlib_wg/ptrlib_wg-common.toml");

        createPresetDirectory();
    }

    private void createPresetDirectory() {
        Path presetSourceDir = FMLPaths.CONFIGDIR.get().resolve(MOD_ID).resolve("preset_dims");
        if (!Files.exists(presetSourceDir)) {
            try {
                Files.createDirectories(presetSourceDir);
            } catch (IOException ignored) {
            }
        }
    }
}
