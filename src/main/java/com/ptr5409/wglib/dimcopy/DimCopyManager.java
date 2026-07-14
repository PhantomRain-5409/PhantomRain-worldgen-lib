package com.ptr5409.wglib.dimcopy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ptr5409.wglib.PhantomRainWorldgenLib;
import com.ptr5409.wglib.accessor.MinecraftServerAccessor;
import com.ptr5409.wglib.mixin.LevelResourceAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 功能：维度副本的创建、删除、种子解析与存档持久化
 */
public final class DimCopyManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final LevelResource DATA_FILE = LevelResourceAccessor.ptrlib$create("ptrlib_wg_dim_copies.json");
    private static final Random RANDOM = new Random();
    private static final Map<ResourceLocation, Entry> ENTRIES = new ConcurrentHashMap<>();

    private DimCopyManager() {
    }

    public static void bootstrap(MinecraftServer server) {
        ENTRIES.clear();
        Path path = server.getWorldPath(DATA_FILE);
        if (!Files.exists(path)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(e.getKey());
                if (id == null || !e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject obj = e.getValue().getAsJsonObject();
                ResourceLocation source = ResourceLocation.tryParse(obj.get("source").getAsString());
                if (source == null) {
                    continue;
                }
                long seed = obj.has("seed") ? obj.get("seed").getAsLong() : 0L;
                ENTRIES.put(id, new Entry(source, seed));
            }
        } catch (Exception ex) {
            PhantomRainWorldgenLib.LOGGER.error("Failed to load dim copy data", ex);
        }
    }

    public static void loadAll(MinecraftServer server) {
        for (Map.Entry<ResourceLocation, Entry> e : ENTRIES.entrySet()) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, e.getKey());
            if (server.getLevel(key) != null) {
                continue;
            }
            LevelStem stem = resolveStem(server, e.getValue().source());
            if (stem == null) {
                PhantomRainWorldgenLib.LOGGER.error("Cannot resolve stem for dim copy {} from {}", e.getKey(), e.getValue().source());
                continue;
            }
            ((MinecraftServerAccessor) server).ptrlib$createLevel(key, stem, e.getValue().seed());
        }
    }

    public static void save(MinecraftServer server) {
        JsonObject root = new JsonObject();
        for (Map.Entry<ResourceLocation, Entry> e : ENTRIES.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("source", e.getValue().source().toString());
            obj.addProperty("seed", e.getValue().seed());
            root.add(e.getKey().toString(), obj);
        }
        try {
            Path path = server.getWorldPath(DATA_FILE);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            PhantomRainWorldgenLib.LOGGER.error("Failed to save dim copy data", ex);
        }
    }

    public static void clear() {
        ENTRIES.clear();
    }


    /**
     * 更新已存在副本的种子并持久化
     */
    public static boolean setSeed(MinecraftServer server, ResourceLocation copyId, long seed) {
        Entry old = ENTRIES.get(copyId);
        if (old == null) {
            return false;
        }
        ENTRIES.put(copyId, new Entry(old.source(), seed));
        save(server);
        return true;
    }
    public static boolean isCopy(ResourceLocation id) {
        return ENTRIES.containsKey(id);
    }

    public static boolean isCopy(ResourceKey<Level> key) {
        return isCopy(key.location());
    }

    public static Long getSeed(ResourceKey<Level> key) {
        Entry entry = ENTRIES.get(key.location());
        return entry == null ? null : entry.seed();
    }

    /**
     * @return true 创建成功
     */
    public static boolean createCopy(MinecraftServer server, ResourceLocation sourceId, ResourceLocation copyId, boolean enableSeed, Long seedArg) {
        ResourceKey<Level> copyKey = ResourceKey.create(Registries.DIMENSION, copyId);
        if (server.getLevel(copyKey) != null || ENTRIES.containsKey(copyId)) {
            return false;
        }

        ResourceKey<Level> sourceKey = ResourceKey.create(Registries.DIMENSION, sourceId);
        ServerLevel sourceLevel = server.getLevel(sourceKey);
        LevelStem stem = resolveStem(server, sourceId);
        if (stem == null) {
            return false;
        }

        long seed;
        if (enableSeed) {
            seed = seedArg != null ? seedArg : RANDOM.nextLong();
        } else if (sourceLevel != null) {
            seed = sourceLevel.getSeed();
        } else {
            seed = server.getWorldData().worldGenOptions().seed();
        }

        // 若源本身是副本，记录其原始 stem 来源，便于重启后重建
        ResourceLocation stemSource = sourceId;
        Entry sourceEntry = ENTRIES.get(sourceId);
        if (sourceEntry != null) {
            stemSource = sourceEntry.source();
        }

        if (!((MinecraftServerAccessor) server).ptrlib$createLevel(copyKey, stem, seed)) {
            return false;
        }

        ENTRIES.put(copyId, new Entry(stemSource, seed));
        save(server);
        return true;
    }

    public static boolean deleteCopy(MinecraftServer server, ResourceLocation copyId) {
        if (!ENTRIES.containsKey(copyId)) {
            return false;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, copyId);
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            unloadAndDeleteData(level);
        } else {
            deleteWorldData(server, key);
        }
        ENTRIES.remove(copyId);
        save(server);
        return true;
    }

    private static LevelStem resolveStem(MinecraftServer server, ResourceLocation sourceId) {
        ResourceLocation resolved = sourceId;
        Entry entry = ENTRIES.get(sourceId);
        if (entry != null) {
            resolved = entry.source();
        }

        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, resolved);
        LevelStem registered = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(stemKey);
        if (registered != null) {
            return registered;
        }

        ServerLevel live = server.getLevel(ResourceKey.create(Registries.DIMENSION, sourceId));
        if (live != null) {
            ChunkGenerator generator = live.getChunkSource().getGenerator();
            return new LevelStem(live.dimensionTypeRegistration(), generator);
        }
        return null;
    }

    private static void unloadAndDeleteData(ServerLevel world) {
        ResourceKey<Level> key = world.dimension();
        MinecraftServer server = world.getServer();
        world.noSave = true;
        teleportOut(world);
        ((MinecraftServerAccessor) server).ptrlib$removeLevel(key);
        deleteWorldData(server, key);
    }

    private static void teleportOut(ServerLevel world) {
        MinecraftServer server = world.getServer();
        PlayerList players = server.getPlayerList();
        for (ServerPlayer player : players.getPlayers()) {
            if (Objects.equals(player.level().dimension(), world.dimension())) {
                players.respawn(player, true);
            }
        }
    }

    private static void deleteWorldData(MinecraftServer server, ResourceKey<Level> key) {
        Path root = server.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions")
                .resolve(key.location().getNamespace())
                .resolve(key.location().getPath());
        cleanSubDir(root.resolve("region"));
        cleanSubDir(root.resolve("entities"));
        cleanSubDir(root.resolve("poi"));
        cleanSubDir(root.resolve("data"));
    }

    private static void cleanSubDir(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    if (!d.equals(dir)) {
                        Files.deleteIfExists(d);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            PhantomRainWorldgenLib.LOGGER.warn("Failed to clean {}", dir, e);
        }
    }

    public record Entry(ResourceLocation source, long seed) {
    }
}
