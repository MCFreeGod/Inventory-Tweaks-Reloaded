package com.mcfreegod.invtweaksreloaded.core;

import net.minecraft.world.item.ItemStack;

/**
 * Identity of an item type for layout sorting. Count is ignored, components
 * are not: two potions of different types are different layouts, exactly like
 * the item-id + damage pairs of the original mod.
 */
public final class StackType implements Comparable<StackType> {
    private final ItemStack sample;
    private final int order;

    public StackType(ItemStack stack) {
        this.sample = stack.copyWithCount(1);
        this.order = ItemOrder.orderOf(stack);
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(sample, stack);
    }

    public int order() {
        return order;
    }

    @Override
    public int compareTo(StackType other) {
        return Integer.compare(order, other.order);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StackType other && ItemStack.isSameItemSameComponents(sample, other.sample);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(sample);
    }
}