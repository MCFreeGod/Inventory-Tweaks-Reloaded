package com.mcfreegod.invtweaksreloaded.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The "..." button that opens the settings screen. Present in container
 * screens that are not chests, exactly like the original mod.
 */
public final class SettingsButton extends IconButton {
    public SettingsButton(int x, int y, Component tooltip, Runnable action) {
        super(x, y, tooltip, action);
    }

    @Override
    protected void drawSymbol(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x + 2, y + 5, x + 3, y + 6, color);
        graphics.fill(x + 4, y + 5, x + 6, y + 6, color);
        graphics.fill(x + 7, y + 5, x + 8, y + 6, color);
    }
}