package com.mcfreegod.invtweaksreloaded.core;

import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** One "A1-C9 keyword" line from the original Inventory Tweaks rule syntax. */
public final class SortRule {
    private final int[] preferredSlots;
    private final Predicate<ItemStack> matcher;
    private final int priority;
    private final RuleType type;

    public SortRule(@Nullable int[] preferredSlots, Predicate<ItemStack> matcher, int priority, RuleType type) {
        this.preferredSlots = preferredSlots;
        this.matcher = matcher;
        this.priority = priority;
        this.type = type;
    }

    @Nullable
    public int[] preferredSlots() {
        return preferredSlots;
    }

    public boolean matches(ItemStack stack) {
        return matcher.test(stack);
    }

    public int priority() {
        return priority;
    }

    public RuleType type() {
        return type;
    }

    public enum RuleType {
        RECTANGLE(1_000_000),
        ROW(2_000_000),
        COLUMN(3_000_000),
        SLOT(4_000_000);

        private final int basePriority;

        RuleType(int basePriority) {
            this.basePriority = basePriority;
        }

        public int basePriority() {
            return basePriority;
        }
    }
}