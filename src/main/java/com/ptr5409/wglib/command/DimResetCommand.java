package com.ptr5409.wglib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.ptr5409.wglib.PhantomRainWorldgenLib;
import com.ptr5409.wglib.worldgen.DimResetManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PhantomRainWorldgenLib.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DimResetCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("dim_reset")
                .requires(source -> source.hasPermission(2)) // 需要OP权限
                .then(Commands.argument("dimension_id", ResourceLocationArgument.id())
                        .executes(context -> {
                            // 使用 ResourceLocationArgument 获取资源位置
                            ResourceLocation dimId = ResourceLocationArgument.getId(context, "dimension_id");

                            if (!DimResetManager.isRegistered(dimId)) {
                                context.getSource().sendFailure(Component.literal("Dimension not registered in config!"));
                                return 0;
                            }

                            DimResetManager.forceReset(context.getSource().getServer(), dimId);
                            return 1;
                        })
                )
        );
    }
}
