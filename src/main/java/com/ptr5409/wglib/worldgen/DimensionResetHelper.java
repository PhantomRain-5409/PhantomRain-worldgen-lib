package com.ptr5409.wglib.worldgen;

import com.ptr5409.wglib.PhantomRainWorldgenLib;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class DimensionResetHelper {

    public static void resetDimension(MinecraftServer server, ResourceLocation dimId, boolean kickPlayers, boolean isAutoEmpty) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) return;

        // 1. 强制清出玩家到主世界
        if (kickPlayers) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
                for (ServerPlayer player : level.getPlayers(p -> true)) {
                    player.teleportTo(overworld, overworld.getSharedSpawnPos().getX(), overworld.getSharedSpawnPos().getY(), overworld.getSharedSpawnPos().getZ(), 0, 0);
                    player.sendSystemMessage(Component.translatable("wglib.message.kicked_out_for_reset")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }

        // 2. 保存世界，将所有数据刷入缓存
        level.noSave = false;
        level.save(null, true, true);

        // 3. 借鉴其他模组思路：清理内存中等待写入的区块缓存
        clearIOWorkerCache(level);

        // 4. 清理该维度内所有残留实体
        List<Entity> entities = new ArrayList<>();
        level.getAllEntities().forEach(entities::add);
        for (Entity entity : entities) {
            entity.discard();
        }

        // 5. 判断并构建目标维度路径（支持原版下界与末地特判）
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path worldDimDir;
        if ("minecraft".equals(dimId.getNamespace())) {
            if ("the_nether".equals(dimId.getPath())) {
                worldDimDir = worldRoot.resolve("DIM-1");
            } else if ("the_end".equals(dimId.getPath())) {
                worldDimDir = worldRoot.resolve("DIM1");
            } else {
                worldDimDir = worldRoot.resolve("dimensions").resolve(dimId.getNamespace()).resolve(dimId.getPath());
            }
        } else {
            worldDimDir = worldRoot.resolve("dimensions").resolve(dimId.getNamespace()).resolve(dimId.getPath());
        }

        // 6. 删除该维度磁盘上的文件夹及内容
        try {
            if (Files.exists(worldDimDir)) {
                deleteDirectoryContents(worldDimDir);
            }
        } catch (IOException e) {
            PhantomRainWorldgenLib.LOGGER.error("Failed to delete dimension files: {}", dimId, e);
        }

        // 7. 重新拷贝预设文件
        copyPresetFiles(dimId, worldDimDir);
    }

    private static void clearIOWorkerCache(ServerLevel level) {
        try {
            Object scanAccess = level.getChunkSource().chunkScanner();

            if (scanAccess != null) {
                Field storageField = scanAccess.getClass().getDeclaredField("storage");
                storageField.setAccessible(true);
                Object storage = storageField.get(scanAccess);

                if (storage != null) {
                    Field cacheField;
                    try {
                        cacheField = storage.getClass().getDeclaredField("regionCache");
                    } catch (NoSuchFieldException e) {
                        cacheField = storage.getClass().getDeclaredField("cache");
                    }
                    cacheField.setAccessible(true);
                    Map<?, ?> regionCache = (Map<?, ?>) cacheField.get(storage);

                    if (regionCache != null) {
                        regionCache.clear(); // 清空内存缓存
                    }
                }
            }
        } catch (Exception e) {
            PhantomRainWorldgenLib.LOGGER.warn("Could not clear IOWorker cache via reflection.", e);
        }
    }

    private static void copyPresetFiles(ResourceLocation dimId, Path worldDimDir) {
        Path presetSourceDir = FMLPaths.CONFIGDIR.get().resolve("ptrlib_wg").resolve("preset_dims");
        Path specificPresetDir = presetSourceDir.resolve(dimId.getNamespace()).resolve(dimId.getPath());

        if (!Files.isDirectory(specificPresetDir)) {
            return;
        }

        try {
            try (Stream<Path> copyStream = Files.walk(specificPresetDir)) {
                copyStream.forEach(subSrc -> {
                    try {
                        Path dest = worldDimDir.resolve(specificPresetDir.relativize(subSrc).toString());
                        if (Files.isDirectory(subSrc)) {
                            Files.createDirectories(dest);
                        } else {
                            Files.createDirectories(dest.getParent());
                            Files.copy(subSrc, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        PhantomRainWorldgenLib.LOGGER.error("Error copying preset file: {}", subSrc, e);
                    }
                });
            }
        } catch (IOException e) {
            PhantomRainWorldgenLib.LOGGER.error("Failed to copy preset files for: {}", dimId, e);
        }
    }

    private static void deleteDirectoryContents(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (!d.equals(dir)) Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
