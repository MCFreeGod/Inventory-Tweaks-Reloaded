package com.mcfreegod.invtweaksreloaded.core;

import com.mcfreegod.invtweaksreloaded.config.ITRConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The original Inventory Tweaks sorting algorithm, adapted to 26.3 slots.
 *
 * <p>The structure is deliberately close to the 1.12.2 implementation: rules
 * are applied by priority, locked/frozen slots block movement, identical stacks
 * merge, and the final pass repeatedly moves every remaining stack into the
 * first slot where it belongs. All movement goes through {@link ClickMover}.
 */
public final class SortingEngine {
    private final ContainerLayout layout;
    private final List<Slot> slots;
    private final ClickMover mover;
    private final int size;
    private final int rowSize;
    private final SortMode mode;
    private final SortRules rules;
    private final boolean inventorySection;

    private final int[] rulePriority;
    private final int[] keywordOrder;
    private final int[] lockPriorities;
    private final boolean[] frozenSlots;

    private SortingEngine(ContainerLayout layout, List<Slot> slots, SortMode mode, SortRules rules,
                          boolean inventorySection, AbstractContainerMenu menu) {
        this.layout = layout;
        this.slots = slots;
        this.size = slots.size();
        this.rowSize = rules.rowSize();
        // The cursor stack is parked in the player inventory, never in the
        // container being sorted: sorting a chest must not move the player's
        // held item into the chest.
        this.mover = new ClickMover(menu, layout.playerInventory());
        this.mode = mode;
        this.rules = rules;
        this.inventorySection = inventorySection;
        this.lockPriorities = rules.lockPriorities();
        this.frozenSlots = rules.frozenSlots();
        this.rulePriority = new int[Math.max(size, 1)];
        this.keywordOrder = new int[Math.max(size, 1)];
        for (int i = 0; i < size; i++) {
            rulePriority[i] = -1;
            ItemStack stack = slots.get(i).getItem();
            keywordOrder[i] = stack.isEmpty() ? -1 : ItemOrder.orderOf(stack);
        }
        // Original behaviour: a slot that already holds the kind of item its
        // rule asks for counts as sorted, so the default pass leaves it alone.
        for (SortRule rule : rules.rules()) {
            int priority = rule.priority();
            int[] preferred = rule.preferredSlots();
            if (preferred == null) {
                continue;
            }
            for (int slot : preferred) {
                if (slot < 0 || slot >= size || rulePriority[slot] >= priority) {
                    continue;
                }
                ItemStack stack = slots.get(slot).getItem();
                if (!stack.isEmpty() && rule.matches(stack)) {
                    rulePriority[slot] = priority;
                }
            }
        }
    }

    public static boolean sortContainer(AbstractContainerScreen<?> screen, SortMode mode) {
        ContainerLayout layout = ContainerLayout.of(screen.getMenu());
        return sortContainer(layout, screen.getMenu(), mode);
    }

    public static boolean sortContainer(ContainerLayout layout, AbstractContainerMenu menu, SortMode mode) {
        if (!layout.isChestLike() || layout.container().isEmpty()) {
            return false;
        }
        SortRules rules = SortRules.forChest(layout.container().size(), layout.rowSize());
        SortingEngine engine = new SortingEngine(layout, layout.container(), mode, rules, false, menu);
        return engine.sort();
    }

    public static boolean sortInventory(AbstractContainerScreen<?> screen) {
        return sortInventory(ContainerLayout.of(screen.getMenu()), screen.getMenu());
    }

    /**
     * The original middle-click action on a crafting grid: even out the stacks
     * in the grid so every slot holds the same amount.
     */
    public static boolean sortEvenStacks(ContainerLayout layout, AbstractContainerMenu menu, List<Slot> slots,
                                         int rowSize) {
        if (slots.isEmpty()) {
            return false;
        }
        SortRules rules = new SortRules(slots.size(), Math.max(1, rowSize));
        SortingEngine engine = new SortingEngine(layout, slots, SortMode.EVEN_STACKS, rules, false, menu);
        return engine.sort();
    }

    public static boolean sortInventory(ContainerLayout layout, AbstractContainerMenu menu) {
        if (!layout.hasPlayerInventory()) {
            return false;
        }
        SortRules rules = SortRules.forInventory(layout.playerInventory().size(), 9);
        SortingEngine engine = new SortingEngine(layout, layout.playerInventory(), SortMode.INVENTORY, rules,
                true, menu);
        return engine.sort();
    }

    public boolean sort() {
        if (!mover.isUsable()) {
            return false;
        }
        if (!mover.dropCarried()) {
            return false;
        }

        // Mirrors the 1.12.2 flow: INVENTORY runs its pre-passes and then the
        // rule pass; the container algorithms run their own pass first; DEFAULT
        // goes straight to the final ordering pass.
        if (inventorySection) {
            clearCraftingGrid();
            mergeAndEquipArmor();
            sortWithRules();
        } else if (mode == SortMode.EVEN_STACKS) {
            sortEvenStacks();
        } else if (mode != SortMode.DEFAULT) {
            computeLineSortingRules(mode == SortMode.HORIZONTAL);
            sortWithRules();
        }

        defaultSorting();
        mover.dropCarried();
        return true;
    }

    // ------------------------------------------------------------------
    // Inventory-only pre-passes
    // ------------------------------------------------------------------

    private void clearCraftingGrid() {
        List<Slot> crafting = layout.craftingInput();
        if (crafting.isEmpty()) {
            return;
        }
        for (Slot craftingSlot : crafting) {
            if (!craftingSlot.hasItem()) {
                continue;
            }
            int empty = firstEmptyInventoryIndex();
            if (empty < 0) {
                return;
            }
            mover.move(craftingSlot, slots.get(empty));
        }
    }

    private int firstEmptyInventoryIndex() {
        for (int i = 0; i < size; i++) {
            if (!slots.get(i).hasItem()) {
                return i;
            }
        }
        return -1;
    }

    private void mergeAndEquipArmor() {
        boolean autoEquip = ITRConfig.ENABLE_AUTO_EQUIP_ARMOR.get();
        for (int i = size - 1; i >= 0; i--) {
            ItemStack from = slots.get(i).getItem();
            if (from.isEmpty()) {
                continue;
            }
            if (autoEquip && isArmor(from)) {
                if (moveArmor(i, from)) {
                    continue;
                }
            }
            mergeItem(i);
        }
    }

    private static boolean isArmor(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return false;
        }
        EquipmentSlot slot = equippable.slot();
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private boolean moveArmor(int fromIndex, ItemStack from) {
        var equippable = from.get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return false;
        }
        EquipmentSlot armorType = equippable.slot();
        for (Slot armorSlot : layout.armor()) {
            if (armorSlotFor(armorSlot) != armorType || !armorSlot.mayPlace(from)) {
                continue;
            }
            ItemStack current = armorSlot.getItem();
            boolean move;
            if (current.isEmpty()) {
                move = true;
            } else if (isArmor(current)) {
                double currentValue = ItemOrder.armorValue(current, armorType);
                double newValue = ItemOrder.armorValue(from, armorType);
                move = currentValue < newValue
                        || currentValue == newValue && current.getDamageValue() > from.getDamageValue();
            } else {
                move = true;
            }
            if (move) {
                return mover.move(slots.get(fromIndex), armorSlot);
            }
        }
        return false;
    }

    /** Maps the four InventoryMenu armor slots (36..39) to their equipment slot. */
    private static EquipmentSlot armorSlotFor(Slot slot) {
        return switch (slot.getContainerSlot()) {
            case 39 -> EquipmentSlot.HEAD;
            case 38 -> EquipmentSlot.CHEST;
            case 37 -> EquipmentSlot.LEGS;
            case 36 -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private void mergeItem(int fromIndex) {
        ItemStack from = slots.get(fromIndex).getItem();
        if (from.isEmpty()) {
            return;
        }
        for (int j = 0; j < size; j++) {
            if (lockPriorities[j] <= 0) {
                continue;
            }
            ItemStack to = slots.get(j).getItem();
            if (to.isEmpty() || !ClickMover.stackable(from, to)) {
                continue;
            }
            move(fromIndex, j, Integer.MAX_VALUE);
            markAsNotMoved(j);
            if (slots.get(fromIndex).getItem().isEmpty()) {
                return;
            }
            from = slots.get(fromIndex).getItem();
        }
    }

    // ------------------------------------------------------------------
    // Rule pass
    // ------------------------------------------------------------------

    private void sortWithRules() {
        for (SortRule rule : rules.rules()) {
            int priority = rule.priority();
            int[] preferred = rule.preferredSlots();
            if (preferred == null) {
                continue;
            }
            for (int i = 0; i < size; i++) {
                ItemStack from = slots.get(i).getItem();
                if (!hasToBeMoved(i, priority) || lockPriorities[i] >= priority || !rule.matches(from)) {
                    continue;
                }
                int stackToMove = i;
                for (int k : preferred) {
                    int result = move(stackToMove, k, priority);
                    if (result == -1) {
                        continue;
                    }
                    ItemStack moved = slots.get(result).getItem();
                    if (result == k || !rule.matches(moved) || i < result) {
                        continue;
                    }
                    stackToMove = result;
                }
            }
        }

        for (int i = 0; i < size; i++) {
            if (hasToBeMoved(i, 1) && lockPriorities[i] > 0) {
                markAsMoved(i, 1);
            }
        }
    }

    // ------------------------------------------------------------------
    // Default pass
    // ------------------------------------------------------------------

    private void defaultSorting() {
        List<Integer> remaining = new ArrayList<>();
        List<Integer> nextRemaining = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (hasToBeMoved(i, 1)) {
                remaining.add(i);
                nextRemaining.add(i);
            }
        }
        int iterations = 0;
        while (!remaining.isEmpty() && iterations++ < 50) {
            Iterator<Integer> iterator = remaining.iterator();
            outer:
            while (iterator.hasNext()) {
                int i = iterator.next();
                if (hasToBeMoved(i, 1)) {
                    for (int j = 0; j < size; j++) {
                        if (move(i, j, 1) == -1) {
                            continue;
                        }
                        nextRemaining.remove(Integer.valueOf(j));
                        continue outer;
                    }
                } else {
                    nextRemaining.remove(Integer.valueOf(i));
                }
            }
            remaining.clear();
            remaining.addAll(nextRemaining);
        }
    }

    private boolean hasToBeMoved(int slot, int priority) {
        return !slots.get(slot).getItem().isEmpty() && rulePriority[slot] <= priority;
    }

    private void markAsMoved(int slot, int priority) {
        rulePriority[slot] = priority;
    }

    private void markAsNotMoved(int slot) {
        rulePriority[slot] = -1;
    }

    private boolean canSwapSlots(int i, int j, int priority) {
        return lockPriorities[j] <= priority
                && (rulePriority[j] < priority
                || rulePriority[j] == priority && isOrderedBefore(i, j));
    }

    private boolean isOrderedBefore(int i, int j) {
        return ItemOrder.compare(slots.get(i).getItem(), slots.get(j).getItem()) < 0;
    }

    /**
     * Moves the stack at {@code i} into {@code j} when that is allowed at the
     * given priority. Returns the slot the moved stack ended up in, {@code -1}
     * when nothing moved, or {@code j} when the move was a no-op.
     */
    private int move(int i, int j, int priority) {
        if (i < 0 || j < 0 || i >= size || j >= size) {
            return -1;
        }
        ItemStack from = slots.get(i).getItem();
        ItemStack to = slots.get(j).getItem();
        if (from.isEmpty() || frozenSlots[i] || frozenSlots[j]) {
            return -1;
        }
        if (lockPriorities[i] > priority) {
            return -1;
        }
        if (i == j) {
            markAsMoved(i, priority);
            return j;
        }

        if (to.isEmpty() && lockPriorities[j] <= priority) {
            rulePriority[i] = -1;
            keywordOrder[i] = -1;
            rulePriority[j] = priority;
            keywordOrder[j] = ItemOrder.orderOf(from);
            return mover.move(slots.get(i), slots.get(j)) ? j : -1;
        }

        if (canSwapSlots(i, j, priority) || canMergeStacks(from, to)) {
            keywordOrder[j] = keywordOrder[i];
            rulePriority[j] = priority;
            rulePriority[i] = -1;
            boolean success = mover.move(slots.get(i), slots.get(j));
            if (!success) {
                return -1;
            }
            ItemStack remains = slots.get(i).getItem();
            if (!remains.isEmpty()) {
                int dropSlot = i;
                if (lockPriorities[j] > lockPriorities[i]) {
                    for (int k = 0; k < size; k++) {
                        if (!slots.get(k).hasItem() && lockPriorities[k] == 0) {
                            dropSlot = k;
                            break;
                        }
                    }
                }
                if (dropSlot != i && !mover.move(slots.get(i), slots.get(dropSlot))) {
                    return -1;
                }
                rulePriority[dropSlot] = -1;
                keywordOrder[dropSlot] = ItemOrder.orderOf(remains);
                return dropSlot;
            }
            return j;
        }
        return -1;
    }

    private static boolean canMergeStacks(ItemStack from, ItemStack to) {
        return ClickMover.stackable(from, to)
                && from.getCount() <= from.getMaxStackSize()
                && to.getCount() < to.getMaxStackSize();
    }

    // ------------------------------------------------------------------
    // Even stacks (crafting grid middle-click in the original mod)
    // ------------------------------------------------------------------

    private void sortEvenStacks() {
        Map<StackType, int[]> counts = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            StackType type = new StackType(stack);
            int[] count = counts.computeIfAbsent(type, k -> new int[]{0, 0});
            count[0] += stack.getCount();
            count[1]++;
        }

        for (Map.Entry<StackType, int[]> entry : counts.entrySet()) {
            StackType type = entry.getKey();
            int total = entry.getValue()[0];
            int stackCount = entry.getValue()[1];
            int perSlot = total / stackCount;
            if (perSlot <= 0) {
                continue;
            }
            List<Integer> small = new ArrayList<>();
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                ItemStack stack = slots.get(i).getItem();
                if (stack.isEmpty() || !type.matches(stack)) {
                    continue;
                }
                if (stack.getCount() > perSlot) {
                    large.add(i);
                } else if (stack.getCount() < perSlot) {
                    small.add(i);
                }
            }
            while (!small.isEmpty() && !large.isEmpty()) {
                int largeIndex = large.get(0);
                int smallIndex = small.get(0);
                int largeSize = slots.get(largeIndex).getItem().getCount();
                int smallSize = slots.get(smallIndex).getItem().getCount();
                int amount = Math.min(perSlot - smallSize, largeSize - perSlot);
                if (amount > 0) {
                    mover.moveSome(slots.get(largeIndex), slots.get(smallIndex), amount);
                }
                largeSize = slots.get(largeIndex).getItem().getCount();
                smallSize = slots.get(smallIndex).getItem().getCount();
                if (largeSize == perSlot) {
                    large.remove(0);
                }
                if (smallSize == perSlot) {
                    small.remove(0);
                } else if (amount <= 0) {
                    break;
                }
            }
            while (large.size() > 1) {
                int first = large.remove(0);
                int second = large.get(0);
                int firstSize = slots.get(first).getItem().getCount();
                if (firstSize > perSlot) {
                    mover.moveSome(slots.get(first), slots.get(second), firstSize - perSlot);
                }
            }
        }
        for (int i = 0; i < size; i++) {
            markAsMoved(i, 1);
        }
    }

    // ------------------------------------------------------------------
    // Horizontal / vertical layout
    // ------------------------------------------------------------------

    private void computeLineSortingRules(boolean horizontal) {
        if (rowSize > 9 || size < rowSize || rowSize <= 0) {
            return;
        }
        Map<StackType, Integer> stats = computeContainerStats();
        int distinctItems = stats.size();
        if (distinctItems == 0) {
            return;
        }
        int columnSize = size / rowSize;
        int availableSlots = size;
        int remainingStacks = 0;
        for (Integer stacks : stats.values()) {
            remainingStacks += stacks;
        }

        List<StackType> itemOrder = new ArrayList<>();
        List<StackType> unordered = new ArrayList<>(stats.keySet());
        boolean placedLarge;
        do {
            placedLarge = false;
            for (StackType type : new ArrayList<>(unordered)) {
                if (stats.get(type) > (horizontal ? rowSize : columnSize)) {
                    placedLarge = true;
                    itemOrder.add(type);
                    unordered.remove(type);
                    break;
                }
            }
        } while (placedLarge);
        unordered.sort(null);
        itemOrder.addAll(unordered);

        int spaceWidth;
        int spaceHeight;
        if (horizontal) {
            spaceHeight = 1;
            spaceWidth = rowSize / ((distinctItems + columnSize - 1) / columnSize);
        } else {
            spaceWidth = 1;
            spaceHeight = columnSize / ((distinctItems + rowSize - 1) / rowSize);
        }
        if (spaceWidth <= 0 || spaceHeight <= 0) {
            spaceWidth = 1;
            spaceHeight = 1;
        }

        char row = 'a';
        char maxRow = (char) (row - 1 + columnSize);
        char column = '1';
        char maxColumn = (char) (column - 1 + rowSize);
        int generated = 0;

        for (StackType type : itemOrder) {
            int thisSpaceWidth = spaceWidth;
            int thisSpaceHeight = spaceHeight;
            while (stats.get(type) > thisSpaceHeight * thisSpaceWidth) {
                if (horizontal) {
                    if (column + thisSpaceWidth < maxColumn) {
                        thisSpaceWidth = maxColumn - column + 1;
                        continue;
                    }
                    if (row + thisSpaceHeight >= maxRow) {
                        break;
                    }
                    thisSpaceHeight++;
                    continue;
                }
                if (row + thisSpaceHeight < maxRow) {
                    thisSpaceHeight = maxRow - row + 1;
                    continue;
                }
                if (column + thisSpaceWidth >= maxColumn) {
                    break;
                }
                thisSpaceWidth++;
            }
            if (horizontal && column + thisSpaceWidth == maxColumn) {
                thisSpaceWidth++;
            } else if (!horizontal && row + thisSpaceHeight == maxRow) {
                thisSpaceHeight++;
            }
            String constraint = String.format("%c%c-%c%c", row, column,
                    (char) (row - 1 + thisSpaceHeight), (char) (column - 1 + thisSpaceWidth));
            if (!horizontal) {
                constraint = constraint + 'v';
            }
            int[] positions = RulePositions.parse(constraint, size, rowSize);
            if (positions != null && positions.length > 0) {
                int priority = Math.max(2, 800_000 - generated++ * 1000);
                rules.addGeneratedRule(positions, type::matches, priority);
            }

            availableSlots -= thisSpaceHeight * thisSpaceWidth;
            remainingStacks -= stats.get(type);
            if (availableSlots < remainingStacks) {
                break;
            }
            if (horizontal) {
                if (column + thisSpaceWidth + spaceWidth <= maxColumn + 1) {
                    column = (char) (column + thisSpaceWidth);
                } else {
                    column = '1';
                    row = (char) (row + thisSpaceHeight);
                }
            } else if (row + thisSpaceHeight + spaceHeight <= maxRow + 1) {
                row = (char) (row + thisSpaceHeight);
            } else {
                row = 'a';
                column = (char) (column + thisSpaceWidth);
            }
            if (row > maxRow || column > maxColumn) {
                break;
            }
        }

        String defaultRule = horizontal
                ? maxRow + "1-a" + maxColumn
                : "a" + maxColumn + "-" + maxRow + "1v";
        int[] all = RulePositions.parse(defaultRule, size, rowSize);
        if (all != null && all.length == size) {
            rules.addCatchAllRule(all, stack -> true);
        } else {
            rules.addCatchAllRule(stack -> true);
        }
    }

    private Map<StackType, Integer> computeContainerStats() {
        Map<StackType, Integer> stats = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            StackType type = new StackType(stack);
            stats.merge(type, 1, Integer::sum);
        }
        return stats;
    }

    }