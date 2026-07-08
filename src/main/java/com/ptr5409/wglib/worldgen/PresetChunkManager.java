package com.ptr5409.wglib.worldgen;

import com.ptr5409.wglib.PhantomRainWorldgenLib;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = PhantomRainWorldgenLib.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PresetChunkManager {

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path worldDimsDirectory = event.getServer().getWorldPath(LevelResource.ROOT).resolve("dimensions");
        Path presetSourceDir = FMLPaths.CONFIGDIR.get().resolve("ptrlib_wg").resolve("preset_dims");
        Path localDimsLock = event.getServer().getWorldPath(LevelResource.ROOT).resolve("dim_preset.lock");

        if (!Files.isDirectory(presetSourceDir)) return;
        if (Files.exists(localDimsLock)) return;

        try {
            Files.createDirectories(worldDimsDirectory);
            try (Stream<Path> copyStream = Files.walk(presetSourceDir)) {
                copyStream.forEach(subSrc -> copy(subSrc, worldDimsDirectory.resolve(presetSourceDir.relativize(subSrc))));
            }
            Files.createFile(localDimsLock);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void copy(Path src, Path dest) {
        try {
            if (Files.isDirectory(src)) {
                Files.createDirectories(dest);
            } else {
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
