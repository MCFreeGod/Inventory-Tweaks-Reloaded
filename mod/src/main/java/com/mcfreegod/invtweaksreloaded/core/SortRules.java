package com.mcfreegod.invtweaksreloaded.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.core.component.DataComponents;
import org.jetbrains.annotations.Nullable;

/**
 * Parses the classic rule syntax ("D /LOCKED", "A1-C4 blocks" ...) and exposes
 * the three arrays the sorting engine needs.
 *
 * <p>Only the keywords that make sense for modern, data-driven items are
 * implemented. Unknown keywords are ignored instead of breaking the sort.
 */
public final class SortRules {
    private final int[] lockPriorities;
    private final boolean[] frozenSlots;
    private final List<SortRule> rules = new ArrayList<>();
    private final int size;
    private final int rowSize;
    private int parsedRuleCount;

    public SortRules(int size, int rowSize) {
        this.size = size;
        this.rowSize = rowSize;
        this.lockPriorities = new int[size];
        this.frozenSlots = new boolean[size];
    }

    /** Default ruleset for the player inventory: the hotbar is locked. */
    public static SortRules forInventory(int size, int rowSize) {
        SortRules rules = new SortRules(size, rowSize);
        if (size == 36 && rowSize == 9) {
            rules.parseLine("D /LOCKED");
        }
        return rules;
    }

    /** Chests start without locks but full auto-equip/merge handling. */
    public static SortRules forChest(int size, int rowSize) {
        return new SortRules(size, rowSize);
    }

    public static SortRules fromLines(List<String> lines, int size, int rowSize) {
        SortRules rules = new SortRules(size, rowSize);
        for (String line : lines) {
            rules.parseLine(line);
        }
        return rules;
    }

    public void parseLine(String rawLine) {
        String line = rawLine.trim().replaceAll("\\s+", " ");
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        String[] words = line.split(" ");
        if (words.length < 2) {
            return;
        }
        int[] slots = RulePositions.parse(words[0], size, rowSize);
        if (slots == null || slots.length == 0) {
            return;
        }
        SortRule.RuleType type = RulePositions.type(words[0], rowSize);
        String keyword = words[1];
        String normalized = keyword.toLowerCase(Locale.ROOT).replace("/", "");

        if (normalized.equals("locked")) {
            int priority = type.basePriority() - 1;
            for (int slot : slots) {
                if (slot >= 0 && slot < size) {
                    lockPriorities[slot] = Math.max(lockPriorities[slot], priority);
                }
            }
            return;
        }
        if (normalized.equals("frozen")) {
            for (int slot : slots) {
                if (slot >= 0 && slot < size) {
                    frozenSlots[slot] = true;
                    lockPriorities[slot] = Math.max(lockPriorities[slot], type.basePriority() - 1);
                }
            }
            return;
        }

        Predicate<ItemStack> matcher = matcher(keyword);
        if (matcher == null) {
            return;
        }
        int priority = 900_000 - parsedRuleCount++ * 1000;
        rules.add(new SortRule(slots, matcher, priority, type));
    }

    /** Adds a generated rule (used by horizontal/vertical sorting). */
    public void addGeneratedRule(int[] slots, Predicate<ItemStack> matcher, int priority) {
        rules.add(new SortRule(slots, matcher, priority, SortRule.RuleType.RECTANGLE));
    }

    public void addCatchAllRule(Predicate<ItemStack> matcher) {
        addCatchAllRule(allSlots(), matcher);
    }

    public void addCatchAllRule(int[] slots, Predicate<ItemStack> matcher) {
        rules.add(new SortRule(slots, matcher, 1, SortRule.RuleType.RECTANGLE));
    }

    public int[] allSlots() {
        int[] slots = new int[size];
        for (int i = 0; i < size; i++) {
            slots[i] = i;
        }
        return slots;
    }

    public int[] lockPriorities() {
        return lockPriorities;
    }

    public boolean[] frozenSlots() {
        return frozenSlots;
    }

    public int size() {
        return size;
    }

    public int rowSize() {
        return rowSize;
    }

    /** Rules in application order (highest priority first). */
    public List<SortRule> rules() {
        List<SortRule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparingInt(SortRule::priority).reversed());
        return sorted;
    }

    @Nullable
    public static Predicate<ItemStack> matcher(String keyword) {
        String key = keyword.toLowerCase(Locale.ROOT);
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.startsWith("#")) {
            try {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(key.substring(1)));
                return stack -> stack.is(tag);
            } catch (RuntimeException e) {
                return null;
            }
        }

        switch (key) {
            case "stuff":
            case "all":
            case "other":
                return stack -> true;
            case "sword":
            case "swords":
                return stack -> stack.is(ItemTags.SWORDS);
            case "pickaxe":
            case "pickaxes":
                return stack -> stack.is(ItemTags.PICKAXES);
            case "shovel":
            case "shovels":
                return stack -> stack.is(ItemTags.SHOVELS);
            case "axe":
            case "axes":
                return stack -> stack.is(ItemTags.AXES);
            case "hoe":
            case "hoes":
                return stack -> stack.is(ItemTags.HOES);
            case "shears":
                return stack -> stack.getItem() instanceof ShearsItem;
            case "fishingrod":
            case "fishing_rod":
                return stack -> stack.getItem() instanceof FishingRodItem;
            case "bow":
                return stack -> stack.getItem() instanceof BowItem;
            case "crossbow":
                return stack -> stack.getItem() instanceof CrossbowItem;
            case "trident":
                return stack -> stack.getItem() instanceof TridentItem;
            case "mace":
                return stack -> stack.getItem() instanceof MaceItem;
            case "shield":
            case "shields":
                return stack -> stack.getItem() instanceof ShieldItem;
            case "arrow":
            case "arrows":
                return stack -> stack.is(ItemTags.ARROWS);
            case "helmet":
            case "helmets":
            case "headarmor":
                return stack -> stack.is(ItemTags.HEAD_ARMOR);
            case "chestplate":
            case "chestplates":
            case "chestarmor":
                return stack -> stack.is(ItemTags.CHEST_ARMOR);
            case "leggings":
            case "legarmor":
                return stack -> stack.is(ItemTags.LEG_ARMOR);
            case "boots":
            case "footarmor":
                return stack -> stack.is(ItemTags.FOOT_ARMOR);
            case "armor":
                return stack -> stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR)
                        || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR)
                        || stack.has(DataComponents.EQUIPPABLE);
            case "food":
            case "ediblefood":
                return stack -> stack.has(DataComponents.FOOD);
            case "block":
            case "blocks":
                return stack -> stack.getItem() instanceof BlockItem;
            case "tool":
            case "tools":
                return stack -> stack.has(DataComponents.TOOL);
            case "weapon":
            case "weapons":
                return stack -> stack.is(ItemTags.SWORDS) || stack.has(DataComponents.WEAPON);
            case "equipment":
                return stack -> ItemOrder.categoryRank(stack) < 30;
            case "resource":
            case "resources":
            case "material":
            case "materials":
                return stack -> !(stack.getItem() instanceof BlockItem)
                        && !stack.has(DataComponents.FOOD)
                        && !stack.has(DataComponents.TOOL)
                        && !stack.has(DataComponents.WEAPON)
                        && !stack.has(DataComponents.EQUIPPABLE);
            case "torch":
                return stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("torch");
            default:
                break;
        }

        // Exact registry name (with or without the namespace).
        final String wanted = key;
        return stack -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id.toString().equals(wanted) || id.getPath().equals(wanted);
        };
    }
}