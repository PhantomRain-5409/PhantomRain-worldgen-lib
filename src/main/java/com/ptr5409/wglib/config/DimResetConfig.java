package com.ptr5409.wglib.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class DimResetConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> RESET_DIMENSIONS_RAW;

    static {
        BUILDER.push("Dimension Reset Settings");
        RESET_DIMENSIONS_RAW = BUILDER.comment(
                "List of dimensions to reset. Use comma to separate multiple dimensions.",
                "Format: dimId;enable_auto;auto_empty;interval_min;warn_sec;force",
                "All keys except dimId are optional. Leave empty to use default values.",
                "- dimId: The dimension ID (e.g., twilightforest:twilight_forest). Vanilla dimensions like minecraft:the_nether and minecraft:the_end are also supported.",
                "- enable_auto: Enable scheduled auto reset (default: false). If false, only manual reset via command is possible.",
                "- auto_empty: Auto reset when no players are present (default: false).",
                "- interval_min: Reset interval in minutes (default: 60).",
                "- warn_sec: Warning time in seconds before reset (default: 10).",
                "- force: Forcefully kick players out before reset (default: true).",
                "Example:",
                "# twilightforest:twilight_forest;true;false;60;10;true,minecraft:the_nether;true;false;120;30;true,minecraft:the_end;true;false;240;30;true",
                "--------------------------------------------------",
                "需要重置的维度列表。多个维度之间请使用逗号分隔。",
                "格式: dimId;enable_auto;auto_empty;interval_min;warn_sec;force",
                "除了 dimId 外所有参数均可选。留空将使用默认值。",
                "- dimId: 维度ID (例如: twilightforest:twilight_forest)。支持原版维度，如 minecraft:the_nether 和 minecraft:the_end。",
                "- enable_auto: 开启定时自动重置 (默认: false)。若为 false，则只能通过指令手动重置。",
                "- auto_empty: 维度内无玩家时自动重置 (默认: false)。",
                "- interval_min: 重置间隔，单位为分钟 (默认: 60)。",
                "- warn_sec: 重置前的警告时间，单位为秒 (默认: 10)。",
                "- force: 重置前强制将玩家踢出该维度 (默认: true)。",
                "示例:",
                "# twilightforest:twilight_forest;true;false;60;10;true,minecraft:the_nether;true;false;120;30;true,minecraft:the_end;true;false;240;30;true"
        ).define("reset_dimensions", "");
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}