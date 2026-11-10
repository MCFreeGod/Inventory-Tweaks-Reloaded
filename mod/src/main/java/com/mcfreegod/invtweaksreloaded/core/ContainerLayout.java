package com.mcfreegod.invtweaksreloaded.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * A read-only description of where the useful slot groups are in the open menu.
 *
 * <p>Inventory Tweaks never touches slot contents directly. Every group below is
 * a list of vanilla {@link Slot}s in visual order, and all movement goes through
 * simulated container clicks. That is what keeps the mod compatible with
 * servers, with other inventory mods, and with containers whose backing object
 * is not a plain {@code Container}.
 *
 * <p>The storage grid is derived from slot coordinates instead of assuming
 * "everything before the player inventory is a chest". That covers 3x3
 * dispensers, 5x9 modded chests and anything else that lays its slots out as a
 * full rectangle, and it refuses to touch menus whose slots are scattered
 * around (machines with input/output clusters), where sorting would be
 * destructive.
 */
public final class ContainerLayout {
    /** Player inventory in visual order: 27 main slots followed by 9 hotbar slots. */
    private final List<Slot> playerInventory;
    private final List<Slot> mainInventory;
    private final List<Slot> hotbar;
    private final List<Slot> armor;
    private final List<Slot> craftingInput;
    /** Non-player storage slots in row-major order, empty when not a full grid. */
    private final List<Slot> container;
    private final int rowSize;
    private final boolean grid;

    private ContainerLayout(List<Slot> playerInventory, List<Slot> mainInventory, List<Slot> hotbar,
                            List<Slot> armor, List<Slot> craftingInput, List<Slot> container, int rowSize,
                            boolean grid) {
        this.playerInventory = playerInventory;
        this.mainInventory = mainInventory;
        this.hotbar = hotbar;
        this.armor = armor;
        this.craftingInput = craftingInput;
        this.container = container;
        this.rowSize = rowSize;
        this.grid = grid;
    }

    public static ContainerLayout of(AbstractContainerMenu menu) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return empty();
        }

        Inventory inventory = player.getInventory();
        List<Slot> main = new ArrayList<>();
        List<Slot> hotbar = new ArrayList<>();
        List<Slot> armor = new ArrayList<>();
        List<Slot> crafting = new ArrayList<>();
        List<Slot> storage = new ArrayList<>();

        for (Slot slot : menu.slots) {
            if (slot.container == inventory) {
                int index = slot.getContainerSlot();
                if (index >= 0 && index < 9) {
                    hotbar.add(slot);
                } else if (index < 36) {
                    main.add(slot);
                } else if (index < 40) {
                    armor.add(slot);
                }
                // index 40 is the offhand; it is deliberately not sorted.
                continue;
            }
            if (slot instanceof ResultSlot || slot.container instanceof CraftingContainer) {
                crafting.add(slot);
                continue;
            }
            storage.add(slot);
        }

        main.sort((a, b) -> Integer.compare(a.getContainerSlot(), b.getContainerSlot()));
        hotbar.sort((a, b) -> Integer.compare(a.getContainerSlot(), b.getContainerSlot()));
        armor.sort((a, b) -> Integer.compare(a.getContainerSlot(), b.getContainerSlot()));

        List<Slot> playerInv = new ArrayList<>(main.size() + hotbar.size());
        playerInv.addAll(main);
        playerInv.addAll(hotbar);

        // Result slots of a crafting grid must never be part of the sortable
        // crafting input (taking from them would craft items).
        crafting.removeIf(slot -> slot instanceof ResultSlot);

        Grid grid = Grid.of(storage);

        return new ContainerLayout(Collections.unmodifiableList(playerInv),
                Collections.unmodifiableList(main),
                Collections.unmodifiableList(hotbar),
                Collections.unmodifiableList(armor),
                Collections.unmodifiableList(crafting),
                grid.slots(),
                grid.rowSize(),
                grid.isValid() && main.size() == 27 && hotbar.size() == 9);
    }

    private static ContainerLayout empty() {
        return new ContainerLayout(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0, false);
    }

    public List<Slot> playerInventory() {
        return playerInventory;
    }

    public List<Slot> mainInventory() {
        return mainInventory;
    }

    public List<Slot> hotbar() {
        return hotbar;
    }

    public List<Slot> armor() {
        return armor;
    }

    public List<Slot> craftingInput() {
        return craftingInput;
    }

    /** Storage slots in row-major order; empty unless {@link #isChestLike()}. */
    public List<Slot> container() {
        return container;
    }

    public int rowSize() {
        return rowSize;
    }

    public boolean isChestLike() {
        return grid;
    }

    public boolean hasPlayerInventory() {
        return mainInventory.size() == 27 && hotbar.size() == 9;
    }

    /**
     * Groups storage slots into rows by their y coordinate and verifies that
     * the result is a complete rectangle.
     */
    private record Grid(List<Slot> slots, int rowSize, boolean isValid) {
        static Grid of(List<Slot> storage) {
            if (storage.isEmpty()) {
                return new Grid(List.of(), 0, false);
            }
            List<int[]> positions = new ArrayList<>(storage.size());
            for (Slot slot : storage) {
                positions.add(new int[]{slot.x, slot.y});
            }
            GridShape shape = GridShape.of(positions);
            if (shape == null) {
                return new Grid(List.of(), 0, false);
            }
            List<Slot> ordered = new ArrayList<>(storage.size());
            for (int index : shape.order()) {
                ordered.add(storage.get(index));
            }
            return new Grid(Collections.unmodifiableList(ordered), shape.rowSize(), true);
        }
    }

    /**
     * The coordinate maths behind {@link Grid}, split out so it can be tested
     * without a running game.
     *
     * <p>Slots are clustered into rows by y (with a small tolerance, because a
     * modded screen may offset a row by a pixel), each row is sorted by x, and
     * every row must have the same width. Anything else - a machine with
     * input/output clusters, a single lone slot, an L-shape - is rejected, so
     * the mod never scrambles a layout it does not understand.
     */
    record GridShape(int[] order, int rowSize) {
        private static final int ROW_TOLERANCE = 2;

        @Nullable
        static GridShape of(List<int[]> positions) {
            if (positions.isEmpty()) {
                return null;
            }

            Map<Integer, List<Integer>> rows = new LinkedHashMap<>();
            List<Integer> rowKeys = new ArrayList<>();
            for (int index = 0; index < positions.size(); index++) {
                int y = positions.get(index)[1];
                Integer matched = null;
                for (int existing : rowKeys) {
                    if (Math.abs(existing - y) <= ROW_TOLERANCE) {
                        matched = existing;
                        break;
                    }
                }
                if (matched == null) {
                    rowKeys.add(y);
                    rows.computeIfAbsent(y, key -> new ArrayList<>()).add(index);
                } else {
                    rows.get(matched).add(index);
                }
            }
            rowKeys.sort(Integer::compare);

            int expectedWidth = -1;
            int[] order = new int[positions.size()];
            int cursor = 0;
            for (int key : rowKeys) {
                List<Integer> row = rows.get(key);
                row.sort((a, b) -> Integer.compare(positions.get(a)[0], positions.get(b)[0]));
                if (expectedWidth == -1) {
                    expectedWidth = row.size();
                } else if (row.size() != expectedWidth) {
                    return null;
                }
                for (int index : row) {
                    order[cursor++] = index;
                }
            }

            // A one-wide column is not a sortable grid: it would turn every
            // furnace and every single-slot machine screen into a "chest".
            if (expectedWidth < 2) {
                return null;
            }
            return new GridShape(order, expectedWidth);
        }
    }
}