package com.mcfreegod.invtweaksreloaded.core;

import com.mcfreegod.invtweaksreloaded.config.ITRConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The ordering that gives the classic Inventory Tweaks layout: gear first,
 * then food, then blocks, then loose materials.
 *
 * <p>Within a category the creative-inventory order is used. That order is
 * familiar to every player, covers modded items automatically, and is much
 * more stable than trying to maintain a 1.12-era XML item tree for 26.3.
 */
public final class ItemOrder {
    // Category bands. Gaps are intentional: they leave room for future
    // sub-categories without renumbering saved layouts.
    private static final int SWORD = 0;
    private static final int SHIELD = 1;
    private static final int BOW = 2;
    private static final int CROSSBOW = 3;
    private static final int TRIDENT = 4;
    private static final int MACE = 5;
    private static final int ARROW = 6;
    private static final int PICKAXE = 10;
    private static final int SHOVEL = 11;
    private static final int AXE = 12;
    private static final int HOE = 13;
    private static final int SHEARS = 14;
    private static final int FISHING_ROD = 15;
    private static final int GENERIC_TOOL = 16;
    private static final int HELMET = 20;
    private static final int CHESTPLATE = 21;
    private static final int LEGGINGS = 22;
    private static final int BOOTS = 23;
    private static final int OTHER_ARMOR = 24;
    private static final int FOOD = 30;
    private static final int BLOCK = 40;
    private static final int RESOURCE = 50;
    private static final int OTHER = 60;

    private static Map<Item, Integer> creativeRanks = Map.of();
    private static boolean ranksReady;

    private ItemOrder() {
    }

    /** Called when a world is joined, so creative tabs are available. */
    public static void invalidate() {
        ranksReady = false;
        creativeRanks = Map.of();
    }

    public static int compare(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0;
        }
        if (b.isEmpty()) {
            return -1;
        }
        if (a.isEmpty()) {
            return 1;
        }

        int category = Integer.compare(categoryRank(a), categoryRank(b));
        if (category != 0) {
            return category;
        }

        int order = Integer.compare(orderInCategory(a), orderInCategory(b));
        if (order != 0) {
            return order;
        }

        if (a.getItem() != b.getItem()) {
            return registryName(a).compareTo(registryName(b));
        }

        return compareSameItem(a, b);
    }

    public static int orderOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return categoryRank(stack) * 1_000_000 + Math.min(orderInCategory(stack), 999_999);
    }

    private static int compareSameItem(ItemStack a, ItemStack b) {
        int customName = Boolean.compare(hasCustomName(a), hasCustomName(b));
        if (customName != 0) {
            return -customName;
        }
        int name = a.getHoverName().getString().compareTo(b.getHoverName().getString());
        if (name != 0) {
            return name;
        }

        int enchantmentCount = Integer.compare(b.getEnchantments().size(), a.getEnchantments().size());
        if (enchantmentCount != 0) {
            return enchantmentCount;
        }
        int enchantmentLevels = Integer.compare(totalEnchantmentLevels(b), totalEnchantmentLevels(a));
        if (enchantmentLevels != 0) {
            return enchantmentLevels;
        }
        int enchantmentId = highestEnchantmentId(a).compareTo(highestEnchantmentId(b));
        if (enchantmentId != 0) {
            return enchantmentId;
        }

        int maxDamage = Integer.compare(maxDamageSortValue(b), maxDamageSortValue(a));
        if (maxDamage != 0) {
            return maxDamage;
        }

        if (a.isDamageableItem() && b.isDamageableItem()) {
            boolean invert = ITRConfig.INVERT_TOOL_DAMAGE.get();
            int damage = invert
                    ? Integer.compare(a.getDamageValue(), b.getDamageValue())
                    : Integer.compare(b.getDamageValue(), a.getDamageValue());
            if (damage != 0) {
                return damage;
            }
        }

        int count = Integer.compare(b.getCount(), a.getCount());
        if (count != 0) {
            return count;
        }
        return registryName(a).compareTo(registryName(b));
    }

    private static boolean hasCustomName(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_NAME);
    }

    private static int totalEnchantmentLevels(ItemStack stack) {
        int total = 0;
        for (var entry : stack.getEnchantments().entrySet()) {
            total += entry.getIntValue();
        }
        return total;
    }

    private static String highestEnchantmentId(ItemStack stack) {
        ItemEnchantments enchantments = stack.getEnchantments();
        String best = "";
        int bestLevel = 0;
        for (var entry : enchantments.entrySet()) {
            int level = entry.getIntValue();
            String id = enchantmentId(entry.getKey());
            if (level > bestLevel || level == bestLevel && id.compareTo(best) < 0) {
                bestLevel = level;
                best = id;
            }
        }
        return best;
    }

    private static String enchantmentId(Holder<Enchantment> enchantment) {
        Optional<ResourceKey<Enchantment>> key = enchantment.unwrapKey();
        return key.map(k -> k.identifier().toString()).orElse("");
    }

    private static int maxDamageSortValue(ItemStack stack) {
        int max = stack.getMaxDamage();
        return max <= 0 ? Integer.MAX_VALUE : max;
    }

    public static int categoryRank(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.is(ItemTags.SWORDS)) {
            return SWORD;
        }
        if (item instanceof ShieldItem) {
            return SHIELD;
        }
        if (item instanceof BowItem) {
            return BOW;
        }
        if (item instanceof CrossbowItem) {
            return CROSSBOW;
        }
        if (item instanceof TridentItem) {
            return TRIDENT;
        }
        if (item instanceof MaceItem) {
            return MACE;
        }
        if (stack.is(ItemTags.ARROWS)) {
            return ARROW;
        }
        if (stack.is(ItemTags.PICKAXES)) {
            return PICKAXE;
        }
        if (stack.is(ItemTags.SHOVELS)) {
            return SHOVEL;
        }
        if (stack.is(ItemTags.AXES)) {
            return AXE;
        }
        if (stack.is(ItemTags.HOES)) {
            return HOE;
        }
        if (item instanceof ShearsItem) {
            return SHEARS;
        }
        if (item instanceof FishingRodItem) {
            return FISHING_ROD;
        }
        if (stack.is(ItemTags.HEAD_ARMOR)) {
            return HELMET;
        }
        if (stack.is(ItemTags.CHEST_ARMOR)) {
            return CHESTPLATE;
        }
        if (stack.is(ItemTags.LEG_ARMOR)) {
            return LEGGINGS;
        }
        if (stack.is(ItemTags.FOOT_ARMOR)) {
            return BOOTS;
        }
        if (isEquippableArmor(stack)) {
            return OTHER_ARMOR;
        }
        if (stack.has(DataComponents.FOOD)) {
            return FOOD;
        }
        if (item instanceof BlockItem) {
            return BLOCK;
        }
        if (stack.has(DataComponents.TOOL)) {
            return GENERIC_TOOL;
        }
        return RESOURCE;
    }

    private static boolean isEquippableArmor(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return false;
        }
        EquipmentSlot slot = equippable.slot();
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private static int orderInCategory(ItemStack stack) {
        ensureCreativeRanks();
        Integer rank = creativeRanks.get(stack.getItem());
        if (rank != null) {
            return rank;
        }
        // Missing from the creative tabs (usually a modded item): fall back to
        // registry order so the result is deterministic.
        return 2_000_000 + BuiltInRegistries.ITEM.getId(stack.getItem());
    }

    private static String registryName(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void ensureCreativeRanks() {
        if (ranksReady) {
            return;
        }
        ranksReady = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        try {
            boolean hasPermissions = mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get();
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), hasPermissions,
                    mc.level.registryAccess());
            Map<Item, Integer> ranks = new HashMap<>();
            int rank = 0;
            for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                    continue;
                }
                for (ItemStack stack : tab.getDisplayItems()) {
                    ranks.putIfAbsent(stack.getItem(), rank++);
                }
            }
            if (!ranks.isEmpty()) {
                creativeRanks = ranks;
            }
        } catch (RuntimeException e) {
            // Not fatal: the registry fallback still produces a usable order.
            ranksReady = false;
        }
    }

    /** Numeric armor value of a stack, used to pick the best piece to equip. */
    public static double armorValue(ItemStack stack, EquipmentSlot slot) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
        return modifiers.compute(Attributes.ARMOR, 0.0, slot);
    }
}