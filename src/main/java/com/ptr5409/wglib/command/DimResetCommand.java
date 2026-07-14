package com.ptr5409.wglib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ptr5409.wglib.PhantomRainWorldgenLib;
import com.ptr5409.wglib.dimreset.DimResetManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 功能：注册 dim_reset 指令（支持可选换种）
 */
@Mod.EventBusSubscriber(modid = PhantomRainWorldgenLib.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DimResetCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("dim_reset")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("dimension_id", ResourceLocationArgument.id())
                        .executes(ctx -> reset(ctx, false, null))
                        .then(Commands.argument("enable", BoolArgumentType.bool())
                                .executes(ctx -> reset(ctx, BoolArgumentType.getBool(ctx, "enable"), null))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> reset(ctx,
                                                BoolArgumentType.getBool(ctx, "enable"),
                                                LongArgumentType.getLong(ctx, "seed")))))));
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, boolean enable, Long seed) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation dimId = ResourceLocationArgument.getId(ctx, "dimension_id");

        if (!DimResetManager.isRegistered(dimId)) {
            source.sendFailure(Component.translatable("wglib.commands.dim_reset.not_registered"));
            return 0;
        }

        Long applied = DimResetManager.forceReset(source.getServer(), dimId, enable, seed);
        if (enable && applied != null) {
            source.sendSuccess(() -> Component.translatable("wglib.commands.dim_reset.success_new_seed", applied.toString()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("wglib.commands.dim_reset.success", dimId.toString()), true);
        }
        return 1;
    }
}