package com.mcfreegod.invtweaksreloaded.core;

import com.mcfreegod.invtweaksreloaded.config.ITRConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Automatic replacement of a broken tool or an empty stack in the selected
 * hotbar slot.
 *
 * <p>This is the original mod's headline quality-of-life feature: keep mining
 * and the next pickaxe appears in your hand. The implementation watches the
 * selected slot and, when the stack disappears (or is about to break), swaps in
 * the best matching stack from the rest of the inventory using a normal
 * hotbar-swap click, which the server validates like any other.
 */
public final class AutoRefill {

    /**
     * Hotbar plus main inventory, i.e. every slot a replacement may come from.
     * Indices 0..8 are the hotbar, 9..35 the main inventory.
     */
    private static final int INVENTORY_SIZE = 36;

    private int lastSelected = -1;
    private ItemStack lastStack = ItemStack.EMPTY;
    private boolean lastHadStack;

    /** Resets the tracked state, e.g. when the world changes. */
    public void reset() {
        lastSelected = -1;
        lastStack = ItemStack.EMPTY;
        lastHadStack = false;
    }

    public void tick() {
        if (!ITRConfig.ENABLE_AUTO_REFILL.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) {
            return;
        }
        if (player.isSpectator() || player.hasInfiniteMaterials()) {
            return;
        }
        if (mc.gui.screen() != null) {
            // Never fight with an open container: the player is moving things.
            remember(player);
            return;
        }

        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        ItemStack current = inventory.getSelectedItem();

        if (selected != lastSelected) {
            remember(player);
            return;
        }
        if (lastSelected < 0) {
            remember(player);
            return;
        }

        boolean refillBeforeBreak = ITRConfig.AUTO_REFILL_BEFORE_BREAK.get();
        boolean stackGone = lastHadStack && current.isEmpty();
        boolean aboutToBreak = refillBeforeBreak
                && !lastStack.isEmpty()
                && lastStack.isDamageableItem()
                && current.isDamageableItem()
                && sameItem(lastStack, current)
                && remainingDurability(current) <= ITRConfig.AUTO_REFILL_DAMAGE_THRESHOLD.get()
                && remainingDurability(lastStack) > ITRConfig.AUTO_REFILL_DAMAGE_THRESHOLD.get();

        if (stackGone || aboutToBreak) {
            if (replace(mc, player, selected, lastStack.copy())) {
                SortDispatcherSounds.click();
            }
        }

        remember(player);
    }

    private void remember(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        lastSelected = inventory.getSelectedSlot();
        lastStack = inventory.getSelectedItem().copy();
        lastHadStack = !lastStack.isEmpty();
    }

    /**
     * Finds the best candidate and swaps it into the hotbar slot. Returns true
     * when something was moved.
     */
    private boolean replace(Minecraft mc, LocalPlayer player, int hotbarIndex, ItemStack wanted) {
        Inventory inventory = player.getInventory();
        int candidate = findCandidate(inventory, hotbarIndex, wanted);
        if (candidate < 0) {
            return false;
        }
        AbstractContainerMenu menu = player.inventoryMenu;
        Slot source = findSlotForInventoryIndex(menu, candidate);
        Slot target = findSlotForInventoryIndex(menu, hotbarIndex);
        if (source == null || target == null) {
            return false;
        }
        // SWAP with the hotbar button is the vanilla "press 1-9" behaviour and
        // is valid both in and out of a container screen.
        mc.gameMode.handleContainerInput(menu.containerId, source.index, hotbarIndex, ContainerInput.SWAP, player);
        return true;
    }

    private int findCandidate(Inventory inventory, int hotbarIndex, ItemStack wanted) {
        boolean damageable = wanted.isDamageableItem();
        boolean refillBeforeBreak = ITRConfig.AUTO_REFILL_BEFORE_BREAK.get();
        int threshold = ITRConfig.AUTO_REFILL_DAMAGE_THRESHOLD.get();
        int best = -1;
        int bestScore = Integer.MAX_VALUE;

        // The original mod searched the whole inventory (slots 0..35), so a
        // spare kept in another hotbar slot is a valid replacement too. The
        // selected slot is skipped: it is either already empty (the stack ran
        // out) or it holds the worn tool that is about to be replaced.
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (i == hotbarIndex) {
                continue;
            }
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!sameItem(stack, wanted)) {
                continue;
            }
            int score;
            if (damageable) {
                int remaining = remainingDurability(stack);
                // With before-break refilling a spare that is itself about to
                // break would only trade one worn tool for another; the
                // original skipped those too.
                if (refillBeforeBreak && remaining <= threshold) {
                    continue;
                }
                // Prefer the most durable. A spare with no durability left is
                // accepted too: it still works one more time, and refusing it
                // would leave the player empty-handed.
                score = -remaining;
            } else {
                // Prefer the largest stack so the hotbar stays full longest.
                score = -stack.getCount();
            }
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    @Nullable
    private static Slot findSlotForInventoryIndex(AbstractContainerMenu menu, int inventoryIndex) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() == inventoryIndex) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Whether {@code candidate} can stand in for {@code wanted}.
     *
     * <p>Non-damageable items have to match their components exactly: a
     * splash potion is not a healing potion, and a dyed block is not another
     * dyed block.
     *
     * <p>Tools and weapons are different. Their {@code DAMAGE} component changes
     * on every use, so two identical iron pickaxes never compare equal as
     * component sets. For those, the item type and every other component - the
     * enchantments, the custom name, the attribute modifiers - have to match,
     * and only the damage counter is ignored.
     */
    private static boolean sameItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty() || !a.is(b.getItem())) {
            return false;
        }
        if (!a.isDamageableItem() && !b.isDamageableItem()) {
            return ItemStack.isSameItemSameComponents(a, b);
        }
        return sameExceptDamage(a, b);
    }

    /**
     * Compares every data component except {@code DAMAGE}, so an enchanted,
     * renamed or customised tool only matches a tool with the same enchantments
     * and the same name.
     */
    private static boolean sameExceptDamage(ItemStack a, ItemStack b) {
        ItemStack strippedA = a.copyWithCount(1);
        ItemStack strippedB = b.copyWithCount(1);
        strippedA.remove(DataComponents.DAMAGE);
        strippedB.remove(DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(strippedA, strippedB);
    }

    private static int remainingDurability(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    /** Sounds are gated by the config, so the helper lives here. */
    private static final class SortDispatcherSounds {
        private static void click() {
            if (!ITRConfig.ENABLE_SOUNDS.get()) {
                return;
            }
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}