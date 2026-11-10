package com.mcfreegod.invtweaksreloaded.client;

import com.mcfreegod.invtweaksreloaded.config.ITRConfig;
import com.mcfreegod.invtweaksreloaded.core.ContainerLayout;
import com.mcfreegod.invtweaksreloaded.core.SortMode;
import com.mcfreegod.invtweaksreloaded.core.SortingEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * Single entry point for "sort this thing". Keeps the GUI buttons, the key
 * binding and the middle-click handler consistent, and centralises the guard
 * rails (creative screens, spectator, no menu).
 */
public final class SortDispatcher {
    private SortDispatcher() {
    }

    public static boolean isSupportedScreen(@Nullable Screen screen) {
        return screen instanceof AbstractContainerScreen<?>
                && !(screen instanceof CreativeModeInventoryScreen);
    }

    public static boolean canSort(@Nullable Screen screen) {
        if (!isSupportedScreen(screen)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return false;
        }
        if (mc.player.isSpectator() || mc.player.hasInfiniteMaterials()) {
            // Creative inventory is a special menu whose slots are not real
            // storage; sorting it would delete or duplicate items.
            return false;
        }
        return true;
    }

    /** Sorts the storage section of the open container, if there is one. */
    public static boolean sortContainer(@Nullable Screen screen, SortMode mode) {
        if (!canSort(screen) || !ITRConfig.SHOW_CHEST_BUTTONS.get()) {
            return false;
        }
        AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen<?>) screen;
        ContainerLayout layout = ContainerLayout.of(containerScreen.getMenu());
        if (!layout.isChestLike()) {
            return false;
        }
        boolean sorted = SortingEngine.sortContainer(layout, containerScreen.getMenu(), mode);
        if (sorted) {
            playClick();
        }
        return sorted;
    }

    /** Sorts the player inventory (R key). */
    public static boolean sortPlayerInventory(@Nullable Screen screen) {
        if (!canSort(screen)) {
            return false;
        }
        AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen<?>) screen;
        ContainerLayout layout = ContainerLayout.of(containerScreen.getMenu());
        if (!layout.hasPlayerInventory()) {
            return false;
        }
        boolean sorted = SortingEngine.sortInventory(layout, containerScreen.getMenu());
        if (sorted) {
            playClick();
        }
        return sorted;
    }

    /**
     * Sorts the player inventory with no screen open. The player inventory menu
     * is always open on both sides, so the same click packets work in the world.
     */
    public static boolean sortPlayerInventoryInWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return false;
        }
        if (mc.player.isSpectator() || mc.player.hasInfiniteMaterials()) {
            return false;
        }
        AbstractContainerMenu menu = mc.player.inventoryMenu;
        ContainerLayout layout = ContainerLayout.of(menu);
        if (!layout.hasPlayerInventory()) {
            return false;
        }
        boolean sorted = SortingEngine.sortInventory(layout, menu);
        if (sorted) {
            playClick();
        }
        return sorted;
    }

    /**
     * Middle-click behaviour from the original mod: sort the section under the
     * cursor. Chests cycle through the three algorithms when clicked repeatedly
     * within two seconds (the original timestamp rule), and a crafting grid is
     * evened out instead of sorted.
     */
    public static boolean sortHoveredSection(@Nullable Screen screen) {
        if (!canSort(screen)) {
            return false;
        }
        AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen<?>) screen;
        Slot hovered = containerScreen.hoveredSlot;
        if (hovered == null) {
            return false;
        }
        ContainerLayout layout = ContainerLayout.of(containerScreen.getMenu());

        if (layout.craftingInput().contains(hovered)) {
            int rowSize = layout.craftingInput().size() == 9 ? 3 : 2;
            boolean sorted = SortingEngine.sortEvenStacks(layout, containerScreen.getMenu(),
                    layout.craftingInput(), rowSize);
            if (sorted) {
                playClick();
            }
            return sorted;
        }

        if (layout.container().contains(hovered)) {
            long now = System.currentTimeMillis();
            SortMode next = SortMode.DEFAULT;
            boolean sameChest = SortModeCycle.lastScreen == containerScreen
                    && now - SortModeCycle.lastClick <= 2000L
                    && layout.rowSize() <= 9;
            if (sameChest) {
                next = SortModeCycle.lastMode.next();
            }
            SortModeCycle.lastScreen = containerScreen;
            SortModeCycle.lastMode = next;
            SortModeCycle.lastClick = now;
            return sortContainer(screen, next);
        }

        if (layout.playerInventory().contains(hovered)) {
            return sortPlayerInventory(screen);
        }
        return false;
    }

    public static void playClick() {
        if (!ITRConfig.ENABLE_SOUNDS.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Remembers which algorithm the middle-click button used last. */
    private static final class SortModeCycle {
        @Nullable
        private static Screen lastScreen;
        private static SortMode lastMode = SortMode.DEFAULT;
        private static long lastClick;

        private SortModeCycle() {
        }
    }
}