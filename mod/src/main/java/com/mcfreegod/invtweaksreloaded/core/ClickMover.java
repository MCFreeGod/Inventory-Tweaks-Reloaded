package com.mcfreegod.invtweaksreloaded.core;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Moves items by sending the same container clicks a player would send.
 *
 * <p>No server-side component is required: the vanilla server already knows
 * how to apply {@code PICKUP} clicks, so this works in singleplayer, on a
 * vanilla server, and on servers running inventory plugins. It also means the
 * mod never desynchronises a menu that another mod owns.
 */
public final class ClickMover {
    private static final int MAX_CLICKS = 4096;

    private final Minecraft mc;
    private final AbstractContainerMenu menu;
    /**
     * Where a carried stack is parked before sorting starts. The original mod
     * always used the player inventory, never the container being sorted, so a
     * sort can never push the cursor stack into someone else's chest.
     */
    private final List<Slot> putDownTargets;
    private int clicks;

    public ClickMover(AbstractContainerMenu menu) {
        this(menu, List.of());
    }

    public ClickMover(AbstractContainerMenu menu, List<Slot> putDownTargets) {
        this.mc = Minecraft.getInstance();
        this.menu = menu;
        this.putDownTargets = putDownTargets;
    }

    public boolean isUsable() {
        return mc.player != null && mc.gameMode != null && mc.player.containerMenu == menu;
    }

    /**
     * Moves the whole stack in {@code src} to {@code dst}, merging when both
     * stacks are identical. Returns true when {@code src} ended up empty (or
     * its remainder was merged into a matching destination).
     */
    public boolean move(Slot src, Slot dst) {
        if (src == dst) {
            return true;
        }
        if (!isUsable() || clicks >= MAX_CLICKS) {
            return false;
        }
        ItemStack from = src.getItem();
        if (from.isEmpty()) {
            return false;
        }
        if (!dst.mayPlace(from)) {
            return false;
        }
        if (!putDownCarried()) {
            return false;
        }

        ItemStack to = dst.getItem();
        if (to.isEmpty()) {
            click(src, 0);
            if (menu.getCarried().isEmpty()) {
                // Some slots refuse the pickup (e.g. result slots). Nothing moved.
                return false;
            }
            click(dst, 0);
            if (!menu.getCarried().isEmpty()) {
                // Destination had less room than expected; put the remainder back.
                click(src, 0);
                return false;
            }
            return true;
        }

        if (stackable(from, to)) {
            click(src, 0);
            click(dst, 0);
            if (!menu.getCarried().isEmpty()) {
                click(src, 0);
            }
            return true;
        }

        return swap(src, dst);
    }

    /** Moves exactly {@code amount} items from {@code src} into {@code dst}. */
    public boolean moveSome(Slot src, Slot dst, int amount) {
        if (src == dst || amount <= 0 || !isUsable() || clicks >= MAX_CLICKS) {
            return false;
        }
        ItemStack from = src.getItem();
        if (from.isEmpty()) {
            return false;
        }
        ItemStack to = dst.getItem();
        if (!to.isEmpty() && !stackable(from, to)) {
            return false;
        }
        if (!putDownCarried()) {
            return false;
        }
        click(src, 0);
        int moved = 0;
        while (moved < amount && !menu.getCarried().isEmpty()) {
            int before = dst.getItem().getCount();
            click(dst, 1);
            int after = dst.getItem().getCount();
            if (after == before) {
                break;
            }
            moved++;
        }
        if (!menu.getCarried().isEmpty()) {
            click(src, 0);
        }
        return moved > 0;
    }

    public boolean dropCarried() {
        if (!isUsable() || menu.getCarried().isEmpty()) {
            return true;
        }
        return putDownCarried();
    }

    private boolean swap(Slot src, Slot dst) {
        ItemStack from = src.getItem().copy();
        ItemStack to = dst.getItem().copy();
        Slot intermediate = findIntermediate(src, dst, from, to);
        if (intermediate == null) {
            return false;
        }

        click(dst, 0);
        click(intermediate, 0);
        click(src, 0);
        click(dst, 0);
        click(intermediate, 0);
        click(src, 0);

        if (!menu.getCarried().isEmpty()) {
            putDownCarried();
        }
        return ItemStack.isSameItemSameComponents(src.getItem(), to)
                && ItemStack.isSameItemSameComponents(dst.getItem(), from);
    }

    @Nullable
    private Slot findIntermediate(Slot src, Slot dst, ItemStack srcStack, ItemStack dstStack) {
        List<Slot> slots = menu.slots;
        for (Slot slot : slots) {
            if (slot == src || slot == dst || !slot.isActive() || slot.hasItem()) {
                continue;
            }
            if (slot.mayPlace(srcStack) && slot.mayPlace(dstStack)) {
                return slot;
            }
        }
        return null;
    }

    /**
     * If the player is carrying a stack (cursor stack) it has to go somewhere
     * before the sorting clicks make sense. Original Inventory Tweaks does the
     * same thing.
     */
    private boolean putDownCarried() {
        if (menu.getCarried().isEmpty()) {
            return true;
        }
        if (!putDownTargets.isEmpty()) {
            // Sorting a container: the held stack has to go back into the
            // player inventory. If that is full the sort is aborted instead of
            // dumping the stack into the container (1.12.2 behaviour).
            return putDownInto(putDownTargets);
        }
        return putDownInto(menu.slots);
    }

    private boolean putDownInto(List<Slot> candidates) {
        for (Slot slot : candidates) {
            if (!slot.isActive() || slot.hasItem() || !slot.mayPlace(menu.getCarried())) {
                continue;
            }
            click(slot, 0);
            if (menu.getCarried().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void click(Slot slot, int button) {
        clicks++;
        mc.gameMode.handleContainerInput(menu.containerId, slot.index, button, ContainerInput.PICKUP, mc.player);
    }

    public static boolean stackable(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty()
                && ItemStack.isSameItemSameComponents(a, b)
                && a.getMaxStackSize() > 1;
    }
}