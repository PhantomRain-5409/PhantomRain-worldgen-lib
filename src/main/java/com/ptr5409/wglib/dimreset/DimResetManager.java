package com.ptr5409.wglib.dimreset;

import com.ptr5409.wglib.PhantomRainWorldgenLib;
import com.ptr5409.wglib.config.DimResetConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = PhantomRainWorldgenLib.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DimResetManager {

    private static class ResetData {
        public final ResourceLocation dimId;
        public final ResourceKey<Level> dimKey;
        public final boolean enableAuto;
        public final boolean autoEmpty;
        public final int intervalSec;
        public final int warnSec;
        public final boolean force;
        public int currentTimer;

        public ResetData(String configStr) {
            String[] parts = configStr.split(";", -1);
            this.dimId = new ResourceLocation(parts[0].trim());
            this.dimKey = ResourceKey.create(Registries.DIMENSION, this.dimId);
            this.enableAuto = parseBool(parts, 1, false);
            this.autoEmpty = parseBool(parts, 2, false);
            this.intervalSec = parseInt(parts, 3, 60) * 60;
            this.warnSec = parseInt(parts, 4, 10);
            this.force = parseBool(parts, 5, true);
            this.currentTimer = this.intervalSec;
        }

        private static boolean parseBool(String[] parts, int index, boolean def) {
            if (parts.length <= index) return def;
            String v = parts[index].trim();
            return v.isEmpty() ? def : Boolean.parseBoolean(v);
        }

        private static int parseInt(String[] parts, int index, int def) {
            if (parts.length <= index) return def;
            String v = parts[index].trim();
            return v.isEmpty() ? def : Integer.parseInt(v);
        }
    }

    private static final Map<ResourceLocation, ResetData> resetTasks = new HashMap<>();
    private static int tickCounter = 0;
    private static boolean hasAutoTasks = false;

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        resetTasks.clear();
        hasAutoTasks = false;
        String raw = DimResetConfig.RESET_DIMENSIONS_RAW.get();
        if (raw == null || raw.isBlank()) return;

        for (String configStr : raw.split(",")) {
            String trimmed = configStr.trim();
            if (trimmed.isEmpty()) continue;
            try {
                ResetData data = new ResetData(trimmed);
                resetTasks.put(data.dimId, data);
                if (data.enableAuto) {
                    hasAutoTasks = true;
                }
            } catch (Exception e) {
                PhantomRainWorldgenLib.LOGGER.error("Failed to parse dim reset config: {}", configStr, e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !hasAutoTasks) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        for (ResetData data : resetTasks.values()) {
            if (!data.enableAuto) continue;

            ServerLevel level = server.getLevel(data.dimKey);
            if (level == null) continue;

            if (data.autoEmpty) {
                if (level.players().isEmpty()) {
                    DimensionResetHelper.resetDimension(server, data.dimId, true, true);
                }
                continue;
            }

            data.currentTimer--;

            if (data.currentTimer <= data.warnSec && data.currentTimer > 0) {
                Component warn = Component.translatable("wglib.message.dim_reset_warning", data.currentTimer)
                        .withStyle(ChatFormatting.RED);
                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(warn);
                }
            }

            if (data.currentTimer <= 0) {
                if (data.force) {
                    DimensionResetHelper.resetDimension(server, data.dimId, true, false);
                } else if (level.players().isEmpty()) {
                    DimensionResetHelper.resetDimension(server, data.dimId, false, false);
                } else {
                    Component cancel = Component.translatable("wglib.message.dim_reset_cancelled")
                            .withStyle(ChatFormatting.YELLOW);
                    for (ServerPlayer player : level.players()) {
                        player.sendSystemMessage(cancel);
                    }
                }
                data.currentTimer = data.intervalSec;
            }
        }
    }

    public static void forceReset(MinecraftServer server, ResourceLocation dimId) {
        forceReset(server, dimId, false, null);
    }

    public static Long forceReset(MinecraftServer server, ResourceLocation dimId, boolean enableSeed, Long seed) {
        Long applied = DimensionResetHelper.resetDimension(server, dimId, true, false, enableSeed, seed);
        Component msg = Component.translatable("wglib.message.dim_force_reset", dimId.toString())
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
        return applied;
    }

    public static boolean isRegistered(ResourceLocation dimId) {
        return resetTasks.containsKey(dimId);
    }
}