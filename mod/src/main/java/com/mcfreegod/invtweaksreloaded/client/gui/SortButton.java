package com.mcfreegod.invtweaksreloaded.client.gui;

import com.mcfreegod.invtweaksreloaded.core.SortMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * One of the three sorting buttons. The icon matches the original mod:
 * <ul>
 *   <li>default: two horizontal bars with a diagonal arrow (top-to-bottom)</li>
 *   <li>horizontal: two horizontal bars (fill rows)</li>
 *   <li>vertical: two vertical bars (fill columns)</li>
 * </ul>
 */
public final class SortButton extends IconButton {
    private final SortMode mode;

    public SortButton(int x, int y, SortMode mode, Component tooltip, Runnable action) {
        super(x, y, tooltip, action);
        this.mode = mode;
    }

    @Override
    protected void drawSymbol(GuiGraphicsExtractor graphics, int x, int y, int color) {
        switch (mode) {
            case HORIZONTAL -> {
                line(graphics, x + 3, y + 3, x + SIZE - 3, y + 4, color);
                line(graphics, x + 3, y + 6, x + SIZE - 3, y + 7, color);
            }
            case VERTICAL -> {
                line(graphics, x + 3, y + 3, x + 4, y + SIZE - 3, color);
                line(graphics, x + 6, y + 3, x + 7, y + SIZE - 3, color);
            }
            default -> {
                line(graphics, x + 3, y + 3, x + SIZE - 3, y + 4, color);
                line(graphics, x + 5, y + 4, x + 6, y + 5, color);
                line(graphics, x + 4, y + 5, x + 5, y + 6, color);
                line(graphics, x + 3, y + 6, x + SIZE - 3, y + 7, color);
            }
        }
    }
}