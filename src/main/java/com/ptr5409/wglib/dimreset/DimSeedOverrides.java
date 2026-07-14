package com.ptr5409.wglib.dimreset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ptr5409.wglib.PhantomRainWorldgenLib;
import com.ptr5409.wglib.mixin.LevelResourceAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 功能：非副本维度的种子覆盖（重置换种时使用），随世界存档持久化
 */
public final class DimSeedOverrides {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final LevelResource DATA_FILE = LevelResourceAccessor.ptrlib$create("ptrlib_wg_dim_seeds.json");
    private static final Map<ResourceLocation, Long> OVERRIDES = new ConcurrentHashMap<>();

    private DimSeedOverrides() {
    }

    public static void bootstrap(MinecraftServer server) {
        OVERRIDES.clear();
        Path path = server.getWorldPath(DATA_FILE);
        if (!Files.exists(path)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(e.getKey());
                if (id != null && e.getValue().isJsonPrimitive()) {
                    OVERRIDES.put(id, e.getValue().getAsLong());
                }
            }
        } catch (Exception ex) {
            PhantomRainWorldgenLib.LOGGER.error("Failed to load dim seed overrides", ex);
        }
    }

    public static void save(MinecraftServer server) {
        JsonObject root = new JsonObject();
        for (Map.Entry<ResourceLocation, Long> e : OVERRIDES.entrySet()) {
            root.addProperty(e.getKey().toString(), e.getValue());
        }
        try {
            Path path = server.getWorldPath(DATA_FILE);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            PhantomRainWorldgenLib.LOGGER.error("Failed to save dim seed overrides", ex);
        }
    }

    public static void clear() {
        OVERRIDES.clear();
    }

    public static Long get(ResourceKey<Level> key) {
        return OVERRIDES.get(key.location());
    }

    public static Long get(ResourceLocation id) {
        return OVERRIDES.get(id);
    }

    public static void set(MinecraftServer server, ResourceLocation id, long seed) {
        OVERRIDES.put(id, seed);
        save(server);
    }
}