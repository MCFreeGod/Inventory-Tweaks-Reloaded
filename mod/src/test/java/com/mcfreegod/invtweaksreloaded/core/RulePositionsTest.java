package com.mcfreegod.invtweaksreloaded.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The rule-position parser is the part of the original mod players relied on
 * most (the "D LOCKED" and "A1-C4 blocks" lines), so it is covered directly.
 */
class RulePositionsTest {
    @Test
    void singleSlotIsRowMajor() {
        assertArrayEquals(new int[]{0}, RulePositions.parse("A1", 36, 9));
        assertArrayEquals(new int[]{35}, RulePositions.parse("D9", 36, 9));
        assertArrayEquals(new int[]{4}, RulePositions.parse("A5", 36, 9));
    }

    @Test
    void wholeRowLeftToRight() {
        assertArrayEquals(new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17}, RulePositions.parse("B", 36, 9));
    }

    @Test
    void wholeRowReversed() {
        assertArrayEquals(new int[]{17, 16, 15, 14, 13, 12, 11, 10, 9}, RulePositions.parse("Br", 36, 9));
    }

    @Test
    void wholeColumnBottomToTop() {
        // Column "1" fills from the last row upwards, matching the original grid.
        assertArrayEquals(new int[]{27, 18, 9, 0}, RulePositions.parse("1", 36, 9));
    }

    @Test
    void wholeColumnTopToBottomWhenReversed() {
        assertArrayEquals(new int[]{0, 9, 18, 27}, RulePositions.parse("1r", 36, 9));
    }

    @Test
    void rectangleIsRowMajor() {
        assertArrayEquals(new int[]{0, 1, 2, 9, 10, 11}, RulePositions.parse("A1-B3", 36, 9));
    }

    @Test
    void rectangleWithVerticalSuffixFillsColumns() {
        assertArrayEquals(new int[]{0, 9, 1, 10, 2, 11}, RulePositions.parse("A1-B3v", 36, 9));
    }

    @Test
    void rectangleIsClippedToContainer() {
        // A 9-slot container is one row; A1-B9 must not produce out-of-range slots.
        int[] slots = RulePositions.parse("A1-B9", 9, 9);
        assertNotNull(slots);
        for (int slot : slots) {
            assertEquals(true, slot >= 0 && slot < 9, "slot " + slot + " is out of range");
        }
    }

    @Test
    void invalidConstraintReturnsNull() {
        assertNull(RulePositions.parse("", 36, 9));
        assertNull(RulePositions.parse("A1-", 36, 9));
        assertNull(RulePositions.parse("--", 36, 9));
    }

    @Test
    void unknownLettersAreIgnoredLikeTheOriginal() {
        // The 1.12.2 parser skips characters it does not understand instead of
        // rejecting the whole line, so "Z9" degrades to column 9.
        assertArrayEquals(new int[]{35, 26, 17, 8}, RulePositions.parse("Z9", 36, 9));
    }

    @Test
    void typeDetection() {
        assertEquals(SortRule.RuleType.SLOT, RulePositions.type("A1", 9));
        assertEquals(SortRule.RuleType.ROW, RulePositions.type("B", 9));
        assertEquals(SortRule.RuleType.COLUMN, RulePositions.type("3", 9));
        assertEquals(SortRule.RuleType.ROW, RulePositions.type("A1-A9", 9));
        assertEquals(SortRule.RuleType.COLUMN, RulePositions.type("A1-C1", 9));
        assertEquals(SortRule.RuleType.RECTANGLE, RulePositions.type("A1-C4", 9));
    }

    @Test
    void lockRulesApplyToTheHotbar() {
        SortRules rules = SortRules.forInventory(36, 9);
        int[] locks = rules.lockPriorities();
        for (int i = 0; i < 27; i++) {
            assertEquals(0, locks[i], "main inventory slot " + i + " must stay unlocked");
        }
        for (int i = 27; i < 36; i++) {
            assertEquals(true, locks[i] > 0, "hotbar slot " + i + " must be locked");
        }
    }

    @Test
    void frozenAlsoLocks() {
        SortRules rules = SortRules.forInventory(36, 9);
        rules.parseLine("A1 FROZEN");
        assertEquals(true, rules.frozenSlots()[0]);
        assertEquals(true, rules.lockPriorities()[0] > 0);
    }

    @Test
    void keywordMatchersAreRegistered() {
        SortRules rules = SortRules.fromLines(
                java.util.List.of("A1 sword", "A2 food", "A3 blocks", "A4 pickaxe"), 36, 9);
        assertEquals(4, rules.rules().size());
    }

    @Test
    void unknownKeywordIsIgnoredNotFatal() {
        SortRules rules = SortRules.fromLines(java.util.List.of("A1 definitelyNotAKeyword"), 36, 9);
        // Unknown keywords fall through to a registry-name matcher, so a rule is
        // still created; the important part is that parsing never throws.
        assertEquals(true, rules.rules().size() <= 1);
    }
}