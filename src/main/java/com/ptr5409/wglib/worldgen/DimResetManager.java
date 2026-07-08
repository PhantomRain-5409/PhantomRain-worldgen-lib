package com.ptr5409.wglib.worldgen;

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
        public ResourceLocation dimId;
        public boolean enableAuto;
        public boolean autoEmpty;
        public int intervalSec;
        public int warnSec;
        public boolean force;
        public int currentTimer;

        public ResetData(String configStr) {
            String[] parts = configStr.split(";", -1);
            this.dimId = new ResourceLocation(parts[0].trim());
            this.enableAuto = parts.length > 1 && !parts[1].trim().isEmpty() ? Boolean.parseBoolean(parts[1].trim()) : false;
            this.autoEmpty = parts.length > 2 && !parts[2].trim().isEmpty() ? Boolean.parseBoolean(parts[2].trim()) : false;
            this.intervalSec = (parts.length > 3 && !parts[3].trim().isEmpty() ? Integer.parseInt(parts[3].trim()) : 60) * 60;
            this.warnSec = parts.length > 4 && !parts[4].trim().isEmpty() ? Integer.parseInt(parts[4].trim()) : 10;
            this.force = parts.length > 5 && !parts[5].trim().isEmpty() ? Boolean.parseBoolean(parts[5].trim()) : true;
            this.currentTimer = this.intervalSec;
        }
    }

    private static final Map<ResourceLocation, ResetData> resetTasks = new HashMap<>();
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        resetTasks.clear();
        String raw = DimResetConfig.RESET_DIMENSIONS_RAW.get();
        if (raw == null || raw.trim().isEmpty()) return;

        for (String configStr : raw.split(",")) {
            if (configStr.trim().isEmpty()) continue;
            try {
                ResetData data = new ResetData(configStr.trim());
                resetTasks.put(data.dimId, data);
            } catch (Exception e) {
                PhantomRainWorldgenLib.LOGGER.error("Failed to parse dim reset config: {}", configStr, e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (++tickCounter < 20) return;
        tickCounter = 0;

        for (ResetData data : resetTasks.values()) {
            if (!data.enableAuto) continue;

            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, data.dimId);
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) continue;

            if (data.autoEmpty) {
                if (level.getPlayers(p -> true).isEmpty()) {
                    DimensionResetHelper.resetDimension(server, data.dimId, true, true);
                }
                continue;
            }

            data.currentTimer--;

            if (data.currentTimer <= data.warnSec && data.currentTimer > 0) {
                for (ServerPlayer player : level.getPlayers(p -> true)) {
                    player.sendSystemMessage(Component.translatable("wglib.message.dim_reset_warning", data.currentTimer)
                            .withStyle(ChatFormatting.RED));
                }
            }

            if (data.currentTimer <= 0) {
                if (data.force) {
                    DimensionResetHelper.resetDimension(server, data.dimId, true, false);
                } else {
                    if (level.getPlayers(p -> true).isEmpty()) {
                        DimensionResetHelper.resetDimension(server, data.dimId, false, false);
                    } else {
                        for (ServerPlayer player : level.getPlayers(p -> true)) {
                            player.sendSystemMessage(Component.translatable("wglib.message.dim_reset_cancelled")
                                    .withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }
                data.currentTimer = data.intervalSec;
            }
        }
    }

    public static void forceReset(MinecraftServer server, ResourceLocation dimId) {
        DimensionResetHelper.resetDimension(server, dimId, true, false);
        server.getPlayerList().getPlayers().forEach(p ->
                p.sendSystemMessage(Component.translatable("wglib.message.dim_force_reset", dimId.toString())
                        .withStyle(ChatFormatting.GOLD))
        );
    }

    public static boolean isRegistered(ResourceLocation dimId) {
        return resetTasks.containsKey(dimId);
    }
}
