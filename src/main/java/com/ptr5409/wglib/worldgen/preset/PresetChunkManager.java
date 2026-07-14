package com.ptr5409.wglib.worldgen.preset;

import com.ptr5409.wglib.PhantomRainWorldgenLib;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

@Mod.EventBusSubscriber(modid = PhantomRainWorldgenLib.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PresetChunkManager {

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path worldDimsDirectory = event.getServer().getWorldPath(LevelResource.ROOT).resolve("dimensions");
        Path presetSourceDir = FMLPaths.CONFIGDIR.get().resolve("ptrlib_wg").resolve("preset_dims");
        Path localDimsLock = event.getServer().getWorldPath(LevelResource.ROOT).resolve("dim_preset.lock");

        if (!Files.isDirectory(presetSourceDir) || Files.exists(localDimsLock)) {
            return;
        }

        try {
            Files.createDirectories(worldDimsDirectory);
            Files.walkFileTree(presetSourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path dest = worldDimsDirectory.resolve(presetSourceDir.relativize(dir));
                    Files.createDirectories(dest);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path dest = worldDimsDirectory.resolve(presetSourceDir.relativize(file));
                    Path parent = dest.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
            Files.createFile(localDimsLock);
        } catch (IOException e) {
            PhantomRainWorldgenLib.LOGGER.error("Failed to import preset dimensions", e);
        }
    }
}