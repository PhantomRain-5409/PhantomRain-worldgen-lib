package com.ptr5409.wglib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ptr5409.wglib.PhantomRainWorldgenLib;
import com.ptr5409.wglib.dimcopy.DimCopyManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 功能：注册 dim_copy / dim_delete 指令
 */
@Mod.EventBusSubscriber(modid = PhantomRainWorldgenLib.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DimCopyCommand {

    private DimCopyCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("dim_copy")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("dim_id", ResourceLocationArgument.id())
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(ctx -> copy(ctx, false, null))
                                .then(Commands.argument("enable", BoolArgumentType.bool())
                                        .executes(ctx -> copy(ctx, BoolArgumentType.getBool(ctx, "enable"), null))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(ctx -> copy(ctx,
                                                        BoolArgumentType.getBool(ctx, "enable"),
                                                        LongArgumentType.getLong(ctx, "seed"))))))));

        dispatcher.register(Commands.literal("dim_delete")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("dim_id", ResourceLocationArgument.id())
                        .executes(DimCopyCommand::delete)));
    }

    private static int copy(CommandContext<CommandSourceStack> ctx, boolean enable, Long seed) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation dimId = ResourceLocationArgument.getId(ctx, "dim_id");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        boolean ok = DimCopyManager.createCopy(source.getServer(), dimId, id, enable, seed);
        if (ok) {
            source.sendSuccess(() -> Component.translatable("wglib.commands.dim_copy.success", dimId.toString(), id.toString()), true);
            return 1;
        }
        source.sendFailure(Component.translatable("wglib.commands.dim_copy.failed"));
        return 0;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation dimId = ResourceLocationArgument.getId(ctx, "dim_id");
        boolean ok = DimCopyManager.deleteCopy(source.getServer(), dimId);
        if (ok) {
            source.sendSuccess(() -> Component.translatable("wglib.commands.dim_delete.success", dimId.toString()), true);
            return 1;
        }
        source.sendFailure(Component.translatable("wglib.commands.dim_delete.failed"));
        return 0;
    }
}
