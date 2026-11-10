package com.mcfreegod.invtweaksreloaded.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * The 10x10 icon buttons Inventory Tweaks put in the top-right corner of
 * container screens.
 *
 * <p>The original drew these on top of the vanilla {@code widgets.png} button
 * texture. That texture no longer exists in 26.3 (buttons became sprites), so
 * the frame is drawn with plain fills instead. The result is the same tiny
 * gray button with a light top-left edge, and it needs no texture asset.
 */
public abstract class IconButton extends AbstractWidget {
    public static final int SIZE = 10;

    private static final int BORDER = 0xFF000000;
    private static final int BODY = 0xFF8B8B8B;
    private static final int BODY_HOVER = 0xFFA6A6A6;
    private static final int HIGHLIGHT = 0xFFC6C6C6;
    private static final int SHADOW = 0xFF555555;
    private static final int SYMBOL = 0xFF202020;
    private static final int SYMBOL_HOVER = 0xFFFFFFFF;

    private final Runnable action;

    protected IconButton(int x, int y, Component tooltip, Runnable action) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.action = action;
        if (tooltip != null) {
            setTooltip(Tooltip.create(tooltip));
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered();
        int body = hovered ? BODY_HOVER : BODY;
        int symbolColor = hovered ? SYMBOL_HOVER : SYMBOL;

        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + SIZE, y + SIZE, BORDER);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, body);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + 2, HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 2, y + SIZE - 1, HIGHLIGHT);
        graphics.fill(x + 1, y + SIZE - 2, x + SIZE - 1, y + SIZE - 1, SHADOW);
        graphics.fill(x + SIZE - 2, y + 1, x + SIZE - 1, y + SIZE - 1, SHADOW);

        drawSymbol(graphics, x, y, symbolColor);
        handleCursor(graphics);
    }

    /** Draws the icon inside the 10x10 frame at {@code (x, y)}. */
    protected abstract void drawSymbol(GuiGraphicsExtractor graphics, int x, int y, int color);

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /** Helper for the 1px-wide strokes the original icons were made of. */
    protected static void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y1, ARGB.opaque(color));
    }
}