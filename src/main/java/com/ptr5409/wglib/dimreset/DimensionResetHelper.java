package com.ptr5409.wglib.dimreset;

import com.ptr5409.wglib.PhantomRainWorldgenLib;
import com.ptr5409.wglib.dimcopy.DimCopyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class DimensionResetHelper {

    private static volatile Field cachedStorageField;
    private static volatile Field cachedRegionCacheField;
    private static volatile boolean reflectionResolved;
    private static volatile boolean reflectionFailed;

    public static void resetDimension(MinecraftServer server, ResourceLocation dimId, boolean kickPlayers, boolean isAutoEmpty) {
        resetDimension(server, dimId, kickPlayers, isAutoEmpty, false, null);
    }

    /**
     * enableSeed 为 true 时更换种子；seed 为 null 则随机。返回实际种子，未换种返回 null。
     */
    public static Long resetDimension(MinecraftServer server, ResourceLocation dimId, boolean kickPlayers, boolean isAutoEmpty, boolean enableSeed, Long seed) {
        Long appliedSeed = null;
        if (enableSeed) {
            appliedSeed = seed != null ? seed : ThreadLocalRandom.current().nextLong();
            if (DimCopyManager.isCopy(dimId)) {
                DimCopyManager.setSeed(server, dimId, appliedSeed);
            } else {
                DimSeedOverrides.set(server, dimId, appliedSeed);
            }
        }

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) return appliedSeed;

        // 1. 踢出玩家（一次性取出生点，避免循环内重复计算）
        if (kickPlayers) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
                BlockPos spawn = overworld.getSharedSpawnPos();
                double sx = spawn.getX() + 0.5;
                double sy = spawn.getY();
                double sz = spawn.getZ() + 0.5;
                Component kickMsg = Component.translatable("wglib.message.kicked_out_for_reset")
                        .withStyle(ChatFormatting.RED);
                // 复制列表，避免传送过程中修改集合
                List<ServerPlayer> inDim = new ArrayList<>(level.players());
                for (ServerPlayer player : inDim) {
                    player.teleportTo(overworld, sx, sy, sz, 0, 0);
                    player.sendSystemMessage(kickMsg);
                }
            }
        }

        // 2. 即将删除全部区块数据：禁止回写，并清 IO 缓存（避免昂贵的全量 save）
        level.noSave = true;
        clearIOWorkerCache(level);

        // 3. 丢弃残留实体（复制快照后 discard，防止并发修改）
        List<Entity> entities = new ArrayList<>();
        level.getAllEntities().forEach(entities::add);
        for (Entity entity : entities) {
            if (!(entity instanceof ServerPlayer)) {
                entity.discard();
            }
        }

        // 4. 维度存档路径
        Path worldDimDir = resolveDimensionDir(server, dimId);

        // 5. 删除磁盘数据
        try {
            if (Files.exists(worldDimDir)) {
                deleteDirectoryContents(worldDimDir);
            }
        } catch (IOException e) {
            PhantomRainWorldgenLib.LOGGER.error("Failed to delete dimension files: {}", dimId, e);
        }

        // 6. 重新拷贝预设
        copyPresetFiles(dimId, worldDimDir);
        return appliedSeed;
    }

    private static Path resolveDimensionDir(MinecraftServer server, ResourceLocation dimId) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        if ("minecraft".equals(dimId.getNamespace())) {
            if ("the_nether".equals(dimId.getPath())) {
                return worldRoot.resolve("DIM-1");
            }
            if ("the_end".equals(dimId.getPath())) {
                return worldRoot.resolve("DIM1");
            }
        }
        return worldRoot.resolve("dimensions").resolve(dimId.getNamespace()).resolve(dimId.getPath());
    }

    private static void clearIOWorkerCache(ServerLevel level) {
        if (reflectionFailed) return;
        try {
            resolveReflection(level);
            if (reflectionFailed || cachedStorageField == null || cachedRegionCacheField == null) return;

            Object scanAccess = level.getChunkSource().chunkScanner();
            if (scanAccess == null) return;

            Object storage = cachedStorageField.get(scanAccess);
            if (storage == null) return;

            Object cacheObj = cachedRegionCacheField.get(storage);
            if (cacheObj instanceof Map<?, ?> regionCache) {
                regionCache.clear();
            }
        } catch (Exception e) {
            PhantomRainWorldgenLib.LOGGER.warn("Could not clear IOWorker cache via reflection.", e);
            reflectionFailed = true;
        }
    }

    private static void resolveReflection(ServerLevel level) {
        if (reflectionResolved) return;
        synchronized (DimensionResetHelper.class) {
            if (reflectionResolved) return;
            try {
                Object scanAccess = level.getChunkSource().chunkScanner();
                if (scanAccess == null) {
                    reflectionFailed = true;
                    reflectionResolved = true;
                    return;
                }
                Field storageField = scanAccess.getClass().getDeclaredField("storage");
                storageField.setAccessible(true);
                Object storage = storageField.get(scanAccess);
                if (storage == null) {
                    reflectionFailed = true;
                    reflectionResolved = true;
                    return;
                }
                Field cacheField;
                try {
                    cacheField = storage.getClass().getDeclaredField("regionCache");
                } catch (NoSuchFieldException e) {
                    cacheField = storage.getClass().getDeclaredField("cache");
                }
                cacheField.setAccessible(true);
                cachedStorageField = storageField;
                cachedRegionCacheField = cacheField;
            } catch (Exception e) {
                PhantomRainWorldgenLib.LOGGER.warn("IOWorker reflection setup failed.", e);
                reflectionFailed = true;
            } finally {
                reflectionResolved = true;
            }
        }
    }

    private static void copyPresetFiles(ResourceLocation dimId, Path worldDimDir) {
        Path specificPresetDir = FMLPaths.CONFIGDIR.get()
                .resolve("ptrlib_wg").resolve("preset_dims")
                .resolve(dimId.getNamespace()).resolve(dimId.getPath());

        if (!Files.isDirectory(specificPresetDir)) {
            return;
        }

        try (Stream<Path> copyStream = Files.walk(specificPresetDir)) {
            copyStream.forEach(subSrc -> {
                try {
                    Path dest = worldDimDir.resolve(specificPresetDir.relativize(subSrc));
                    if (Files.isDirectory(subSrc)) {
                        Files.createDirectories(dest);
                    } else {
                        Path parent = dest.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.copy(subSrc, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    PhantomRainWorldgenLib.LOGGER.error("Error copying preset file: {}", subSrc, e);
                }
            });
        } catch (IOException e) {
            PhantomRainWorldgenLib.LOGGER.error("Failed to copy preset files for: {}", dimId, e);
        }
    }

    private static void deleteDirectoryContents(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (!d.equals(dir)) {
                    Files.delete(d);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}