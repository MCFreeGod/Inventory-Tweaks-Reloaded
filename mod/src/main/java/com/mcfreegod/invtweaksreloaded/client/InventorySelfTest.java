package com.mcfreegod.invtweaksreloaded.client;

import com.mcfreegod.invtweaksreloaded.InvTweaksReloaded;
import com.mcfreegod.invtweaksreloaded.core.ContainerLayout;
import com.mcfreegod.invtweaksreloaded.core.ItemOrder;
import com.mcfreegod.invtweaksreloaded.core.SortingEngine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in end-to-end check of the click-based move engine.
 *
 * <p>Start a singleplayer world with {@code -Dinvtweaksreloaded.selftest=true}
 * and the mod fills the inventory with a known multiset, sorts it through the
 * real click packets, then compares the <em>server's</em> copy of the inventory
 * with what it started with. The invariant that matters most is that sorting
 * never loses, duplicates or destroys an item, and never touches the locked
 * hotbar.
 *
 * <p>Every server-side read and write is marshalled onto the server thread with
 * {@link MinecraftServer#execute}. Touching the menu from the client thread
 * makes the server's advancement listeners throw a
 * {@code ConcurrentModificationException}.
 *
 * <p>Disabled by default; the property is read once at class load.
 */
@EventBusSubscriber(modid = InvTweaksReloaded.MOD_ID, value = Dist.CLIENT)
public final class InventorySelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("invtweaksreloaded.selftest");
    private static final Logger LOGGER = LoggerFactory.getLogger("InventoryTweaksReloaded");

    /** Main inventory slots, i.e. everything except the locked hotbar. */
    private static final int FIRST_MAIN = 9;
    private static final int LAST_MAIN = 35;
    /** Terminal phase, reached after all refill scenarios have run. */
    private static final int DONE = 17;

    private static int phase;
    private static int wait;
    private static int ticks;

    private static volatile boolean serverTaskQueued;
    private static volatile boolean serverTaskDone;
    private static volatile Map<String, Integer> before = Map.of();
    private static volatile int failures;

    private InventorySelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || phase == DONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MinecraftServer server = mc.getSingleplayerServer();
        if (player == null || server == null) {
            return;
        }
        ServerPlayer serverPlayer = serverPlayer(server, player);
        if (serverPlayer == null) {
            return;
        }
        // Hard stop so a stuck phase can never hang the dev client forever.
        if (++ticks > 20 * 30) {
            LOGGER.error("[InventoryTweaksReloaded] Self test timed out in phase {}", phase);
            phase = 9;
            mc.stop();
            return;
        }

        switch (phase) {
            case 0 -> {
                if (wait == 0) {
                    logSortKey(mc);
                }
                // Let the world and both menu copies settle first.
                if (++wait < 20) {
                    return;
                }
                if (queueServerTask(server, () -> {
                    serverPlayer.setGameMode(GameType.SURVIVAL);
                    fillInventory(serverPlayer);
                    serverPlayer.inventoryMenu.broadcastChanges();
                    before = totals(serverPlayer.getInventory());
                    LOGGER.info("[InventoryTweaksReloaded] Self test: seeded {}", before);
                })) {
                    wait = 0;
                    phase = 1;
                }
            }
            case 1 -> {
                if (!serverTaskDone) {
                    return;
                }
                // Give the seeded stacks time to reach the client menu.
                if (++wait < 10) {
                    return;
                }
                wait = 0;
                phase = 2;
            }
            case 2 -> {
                runSort(mc);
                wait = 0;
                phase = 3;
            }
            case 3 -> {
                // The clicks travel client -> server -> back; wait for the round trip.
                if (++wait < 20) {
                    return;
                }
                if (queueServerTask(server, () -> verify(serverPlayer))) {
                    phase = 4;
                }
            }
            case 4 -> {
                if (!serverTaskDone) {
                    return;
                }
                // Wearing the tool down first is what makes this a real test:
                // AutoRefill remembers the stack *with its damage component*,
                // and the spare in the inventory has a different one.
                if (queueServerTask(server, () -> damageHeldTool(serverPlayer))) {
                    wait = 0;
                    phase = 5;
                }
            }
            case 5 -> {
                if (!serverTaskDone) {
                    return;
                }
                // Let the client observe the damaged tool, so AutoRefill records
                // it as "the stack that was here".
                if (++wait < 10) {
                    return;
                }
                if (queueServerTask(server, () -> breakHeldTool(serverPlayer))) {
                    wait = 0;
                    phase = 6;
                }
            }
            case 6 -> {
                if (!serverTaskDone) {
                    return;
                }
                // The client learns about the broken tool on the next sync.
                if (++wait < 10) {
                    return;
                }
                wait = 0;
                phase = 7;
            }
            case 7 -> {
                // AutoRefill ticks on ClientTickEvent.Post; give it a few ticks.
                if (++wait < 10) {
                    return;
                }
                if (queueServerTask(server, () -> verifyRefill(serverPlayer, "main inventory slot 34", 0))) {
                    phase = 8;
                }
            }
            case 8 -> {
                if (!serverTaskDone) {
                    return;
                }
                // Second scenario: the spare sits in ANOTHER hotbar slot. The
                // original mod searched the whole inventory for a replacement,
                // so a spare kept outside the main inventory has to work too.
                if (queueServerTask(server, () -> seedHotbarSpare(serverPlayer))) {
                    wait = 0;
                    phase = 9;
                }
            }
            case 9 -> {
                if (!serverTaskDone) {
                    return;
                }
                if (++wait < 10) {
                    return;
                }
                if (queueServerTask(server, () -> breakHeldTool(serverPlayer))) {
                    wait = 0;
                    phase = 10;
                }
            }
            case 10 -> {
                if (!serverTaskDone) {
                    return;
                }
                if (++wait < 10) {
                    return;
                }
                wait = 0;
                phase = 11;
            }
            case 11 -> {
                if (++wait < 10) {
                    return;
                }
                if (queueServerTask(server, () -> verifyRefill(serverPlayer, "hotbar slot 5", 0))) {
                    phase = 12;
                }
            }
            case 12 -> {
                if (!serverTaskDone) {
                    return;
                }
                // Third scenario: the spare is at ZERO remaining durability,
                // which is what "/give ... [minecraft:damage=<max>]" produces.
                // The held tool is worn to its last point and then broken.
                if (queueServerTask(server, () -> seedFullyWornSpare(serverPlayer))) {
                    wait = 0;
                    phase = 13;
                }
            }
            case 13 -> {
                if (!serverTaskDone) {
                    return;
                }
                if (++wait < 10) {
                    return;
                }
                if (queueServerTask(server, () -> breakHeldTool(serverPlayer))) {
                    wait = 0;
                    phase = 14;
                }
            }
            case 14 -> {
                if (!serverTaskDone) {
                    return;
                }
                if (++wait < 10) {
                    return;
                }
                wait = 0;
                phase = 15;
            }
            case 15 -> {
                if (++wait < 10) {
                    return;
                }
                if (queueServerTask(server, () -> verifyRefill(serverPlayer, "0-durability spare in slot 30",
                        new ItemStack(Items.WOODEN_AXE).getMaxDamage()))) {
                    phase = 16;
                }
            }
            case 16 -> {
                if (!serverTaskDone) {
                    return;
                }
                LOGGER.info("[InventoryTweaksReloaded] Self test {}",
                        failures == 0 ? "PASSED" : "FAILED (" + failures + " problem(s))");
                phase = DONE;
                mc.stop();
            }
            default -> {
            }
        }
    }

    private static boolean queueServerTask(MinecraftServer server, Runnable task) {
        if (serverTaskQueued) {
            return false;
        }
        serverTaskQueued = true;
        serverTaskDone = false;
        server.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                fail("server task threw " + e);
            } finally {
                serverTaskDone = true;
                // Release the slot so the next phase can queue its own task.
                serverTaskQueued = false;
            }
        });
        return true;
    }

    private static ServerPlayer serverPlayer(MinecraftServer server, LocalPlayer player) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer candidate : players) {
            if (candidate.getUUID().equals(player.getUUID())) {
                return candidate;
            }
        }
        return null;
    }

    private static void fillInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, ItemStack.EMPTY);
        }
        inventory.setItem(20, new ItemStack(Items.DIRT, 20));
        inventory.setItem(10, new ItemStack(Items.STONE, 5));
        inventory.setItem(25, new ItemStack(Items.DIRT, 10));
        inventory.setItem(30, new ItemStack(Items.IRON_PICKAXE));
        inventory.setItem(31, new ItemStack(Items.DIAMOND_SWORD));
        inventory.setItem(32, new ItemStack(Items.BREAD, 7));
        inventory.setItem(33, new ItemStack(Items.TORCH, 12));
        // A spare axe in the main inventory, used by the refill phase.
        inventory.setItem(34, new ItemStack(Items.WOODEN_AXE));
        // The held axe that is about to be destroyed.
        inventory.setItem(0, new ItemStack(Items.WOODEN_AXE));
        inventory.setSelectedSlot(0);
    }

    /**
     * Wears the held axe down, so the stack AutoRefill remembers carries a
     * non-zero {@code DAMAGE} component that no spare in the inventory shares.
     */
    private static void damageHeldTool(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(0);
        if (held.isEmpty()) {
            fail("held axe missing before the damage step");
            return;
        }
        held.setDamageValue(held.getMaxDamage() - 1);
        LOGGER.info("[InventoryTweaksReloaded] Self test: worn axe {} damage={}/{}",
                held, held.getDamageValue(), held.getMaxDamage());
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Destroys the held stack through the real durability path: exactly one
     * more {@code hurtAndBreak} than the stack can take, which is what
     * {@code Item.mineBlock} and {@code Item.hurtEnemy} do when a tool breaks.
     * {@code applyDamage} then shrinks the stack to nothing <em>in place</em>,
     * so the inventory keeps holding an empty stack object rather than
     * {@code ItemStack.EMPTY}. No manual broadcast here either - the server's
     * own tick loop syncs the slot, just like it does for a real block break.
     */
    private static void breakHeldTool(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(0);
        if (held.isEmpty()) {
            fail("held axe missing before the refill test");
            return;
        }
        LOGGER.info("[InventoryTweaksReloaded] Self test: breaking held {} via hurtAndBreak", held);
        held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        ItemStack after = inventory.getItem(0);
        LOGGER.info("[InventoryTweaksReloaded] Self test: hotbar slot 0 now {} (empty={})", after, after.isEmpty());
        if (!after.isEmpty()) {
            fail("the real durability path did not empty the held slot");
        }
    }

    /**
     * Prepares the second refill scenario: the axe in hand is worn down again
     * and a fresh spare is placed in hotbar slot 5, i.e. another hotbar slot
     * rather than the main inventory.
     */
    private static void seedHotbarSpare(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(0);
        if (held.isEmpty() || !held.is(Items.WOODEN_AXE)) {
            fail("expected the refilled axe in slot 0 before the hotbar scenario");
            return;
        }
        held.setDamageValue(held.getMaxDamage() - 1);
        inventory.setItem(5, new ItemStack(Items.WOODEN_AXE));
        player.inventoryMenu.broadcastChanges();
        LOGGER.info("[InventoryTweaksReloaded] Self test: hotbar scenario - held worn to {}/{} and spare axe in slot 5",
                held.getDamageValue(), held.getMaxDamage());
    }

    /**
     * Prepares the third refill scenario: the spare is a tool at ZERO
     * remaining durability, which is exactly what
     * {@code /give ... [minecraft:damage=<max>]} produces. The held tool is
     * worn to its last point; breaking it must pull the worn spare out.
     */
    private static void seedFullyWornSpare(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(0);
        if (held.isEmpty() || !held.is(Items.WOODEN_AXE)) {
            fail("expected the refilled axe in slot 0 before the worn-spare scenario");
            return;
        }
        held.setDamageValue(held.getMaxDamage() - 1);
        ItemStack spare = new ItemStack(Items.WOODEN_AXE);
        spare.setDamageValue(spare.getMaxDamage());
        inventory.setItem(30, spare);
        player.inventoryMenu.broadcastChanges();
        LOGGER.info("[InventoryTweaksReloaded] Self test: worn-spare scenario - held worn to {}/{} and a 0-durability spare in slot 30",
                held.getDamageValue(), held.getMaxDamage());
    }

    /** The held slot must hold an axe again, with the damage the spare had. */
    private static void verifyRefill(ServerPlayer player, String spareWasIn, int expectedDamage) {
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(0);
        if (held.isEmpty()) {
            fail("auto-refill did not put a tool back into the selected slot (spare was in " + spareWasIn + ")");
            return;
        }
        if (!held.is(Items.WOODEN_AXE)) {
            fail("auto-refill put the wrong item in the selected slot: " + held);
            return;
        }
        if (held.getDamageValue() != expectedDamage) {
            fail("auto-refill picked an axe with damage " + held.getDamageValue() + ", expected " + expectedDamage
                    + ": " + held);
            return;
        }
        LOGGER.info("[InventoryTweaksReloaded] Self test: refilled with {} (spare was in {})", held, spareWasIn);
    }

    /** Confirms the sort key is registered and sits in its own controls category. */
    private static void logSortKey(Minecraft mc) {
        for (KeyMapping key : mc.options.keyMappings) {
            if (key.getName().equals("key.invtweaksreloaded.sort")) {
                LOGGER.info("[InventoryTweaksReloaded] Self test: sort key bound to {} in category {}",
                        key.saveString(), key.getCategory().id());
                return;
            }
        }
        fail("the sort key is not registered in options.keyMappings");
    }

    private static void runSort(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        AbstractContainerMenu menu = player.inventoryMenu;
        ContainerLayout layout = ContainerLayout.of(menu);
        if (!layout.hasPlayerInventory()) {
            fail("player inventory layout not recognised");
            return;
        }
        LOGGER.info("[InventoryTweaksReloaded] Self test: sorting {} slots", layout.playerInventory().size());
        SortingEngine.sortInventory(layout, menu);
    }

    private static void verify(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        Map<String, Integer> after = totals(inventory);

        if (!before.equals(after)) {
            fail("item totals changed: before=" + before + " after=" + after);
        } else {
            LOGGER.info("[InventoryTweaksReloaded] Self test: totals preserved {}", after);
        }

        for (int i = 0; i <= 8; i++) {
            // Slot 0 is deliberately seeded with the axe used by the refill
            // phase, so it is the one hotbar slot that is allowed to hold
            // something.
            if (i == 0) {
                continue;
            }
            if (!inventory.getItem(i).isEmpty()) {
                fail("locked hotbar slot " + i + " changed to " + inventory.getItem(i));
            }
        }

        AbstractContainerMenu menu = player.inventoryMenu;
        int previous = -1;
        boolean seenEmpty = false;
        for (int i = FIRST_MAIN; i <= LAST_MAIN; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                seenEmpty = true;
                continue;
            }
            if (seenEmpty) {
                fail("main inventory has a gap before slot " + i);
                break;
            }
            int order = ItemOrder.orderOf(stack);
            if (previous > order) {
                fail("main inventory is not ordered at slot " + i + " (" + stack + ")");
                break;
            }
            previous = order;
        }

        LOGGER.info("[InventoryTweaksReloaded] Self test: server inventory = {}", describe(inventory));
    }

    private static Map<String, Integer> totals(Inventory inventory) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            totals.merge(id, stack.getCount(), Integer::sum);
        }
        return totals;
    }

    private static String describe(Inventory inventory) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                builder.append('[').append(i).append("]=").append(stack.getCount()).append('x')
                        .append(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()).append(' ');
            }
        }
        return builder.toString().trim();
    }

    private static void fail(String message) {
        failures++;
        LOGGER.error("[InventoryTweaksReloaded] Self test failure: {}", message);
    }
}
