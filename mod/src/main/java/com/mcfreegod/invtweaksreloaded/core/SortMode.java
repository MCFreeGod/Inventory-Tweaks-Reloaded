package com.mcfreegod.invtweaksreloaded.core;

/**
 * The classic Inventory Tweaks sorting modes. DEFAULT sorts by item order,
 * VERTICAL/HORIZONTAL arrange item groups into columns/rows, and INVENTORY is
 * the special "tidy my whole inventory" pass.
 */
public enum SortMode {
    DEFAULT,
    HORIZONTAL,
    VERTICAL,
    EVEN_STACKS,
    INVENTORY;

    /** Cycles DEFAULT -> HORIZONTAL -> VERTICAL -> DEFAULT, like the original buttons. */
    public SortMode next() {
        return switch (this) {
            case DEFAULT -> HORIZONTAL;
            case HORIZONTAL -> VERTICAL;
            default -> DEFAULT;
        };
    }
}