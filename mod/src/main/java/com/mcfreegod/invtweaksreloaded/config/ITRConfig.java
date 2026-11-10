package com.mcfreegod.invtweaksreloaded.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Player-facing options. The defaults deliberately mirror the original
 * Inventory Tweaks property file, because the whole point of this mod is to
 * feel like the 1.12.2 version rather than a new sorter.
 */
public final class ITRConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_CHEST_BUTTONS;
    public static final ModConfigSpec.BooleanValue ENABLE_AUTO_REFILL;
    public static final ModConfigSpec.BooleanValue AUTO_REFILL_BEFORE_BREAK;
    public static final ModConfigSpec.IntValue AUTO_REFILL_DAMAGE_THRESHOLD;
    public static final ModConfigSpec.BooleanValue ENABLE_SOUNDS;
    public static final ModConfigSpec.BooleanValue INVERT_TOOL_DAMAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_MIDDLE_CLICK;
    public static final ModConfigSpec.BooleanValue ENABLE_AUTO_EQUIP_ARMOR;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Buttons and input").push("interface");
        SHOW_CHEST_BUTTONS = builder
                .comment("Show the three sorting buttons (and the settings button) in the top-right corner of container screens.")
                .define("showChestButtons", true);
        ENABLE_MIDDLE_CLICK = builder
                .comment("Middle-click a slot to sort that section (original Inventory Tweaks behaviour).")
                .define("enableMiddleClick", true);
        builder.pop();

        builder.comment("Sorting").push("sorting");
        INVERT_TOOL_DAMAGE = builder
                .comment("Sort damaged tools before pristine ones (original default: true).")
                .define("invertToolDamageSorting", true);
        ENABLE_AUTO_EQUIP_ARMOR = builder
                .comment("When sorting the player inventory, equip the best available armor.")
                .define("enableAutoEquipArmor", false);
        builder.pop();

        builder.comment("Automatic replacement of broken tools and used-up stacks").push("autoRefill");
        ENABLE_AUTO_REFILL = builder
                .comment("Replace a stack in the selected hotbar slot when it runs out, using a matching stack from the inventory.")
                .define("enableAutoRefill", true);
        AUTO_REFILL_BEFORE_BREAK = builder
                .comment("Swap a nearly-broken tool for a fresh one before it breaks instead of after.")
                .define("autoRefillBeforeBreak", false);
        AUTO_REFILL_DAMAGE_THRESHOLD = builder
                .comment("Remaining durability at or below this value triggers a before-break swap.")
                .defineInRange("autoRefillDamageThreshold", 5, 0, 10000);
        builder.pop();

        builder.comment("Feedback").push("feedback");
        ENABLE_SOUNDS = builder
                .comment("Play the vanilla button click sound when sorting or auto-refilling.")
                .define("enableSounds", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ITRConfig() {
    }
}