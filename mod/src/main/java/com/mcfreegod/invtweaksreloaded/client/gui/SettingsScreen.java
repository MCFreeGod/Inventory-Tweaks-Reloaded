package com.mcfreegod.invtweaksreloaded.client.gui;

import com.mcfreegod.invtweaksreloaded.config.ITRConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The "..." screen. Deliberately small: the original had a settings screen, and
 * its most-used switches are the ones reproduced here. Everything else lives in
 * the config file.
 */
public final class SettingsScreen extends Screen {
    private static final int WIDTH = 220;
    private static final int HEIGHT = 20;
    private static final int GAP = 4;

    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("invtweaks.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = Math.max(32, height / 2 - 90);

        y = addToggle(x, y, "invtweaks.settings.chestbuttons", "invtweaks.settings.chestbuttons.tooltip",
                ITRConfig.SHOW_CHEST_BUTTONS);
        y = addToggle(x, y, "invtweaks.settings.middleclick", "invtweaks.settings.middleclick.tooltip",
                ITRConfig.ENABLE_MIDDLE_CLICK);
        y = addToggle(x, y, "invtweaks.settings.autorefill", "invtweaks.settings.autorefill.tooltip",
                ITRConfig.ENABLE_AUTO_REFILL);
        y = addToggle(x, y, "invtweaks.settings.beforebreak", "invtweaks.settings.beforebreak.tooltip",
                ITRConfig.AUTO_REFILL_BEFORE_BREAK);
        y = addToggle(x, y, "invtweaks.settings.sounds", "invtweaks.settings.advanced.sounds.tooltip",
                ITRConfig.ENABLE_SOUNDS);
        y = addToggle(x, y, "invtweaks.settings.autoequip", "invtweaks.settings.advanced.autoequip.tooltip",
                ITRConfig.ENABLE_AUTO_EQUIP_ARMOR);
        addToggle(x, y, "invtweaks.settings.sortdamage", "invtweaks.settings.sortdamage.tooltip",
                ITRConfig.INVERT_TOOL_DAMAGE);

        addRenderableWidget(Button.builder(Component.translatable("invtweaks.settings.exit"), button -> onClose())
                .bounds(x, height - 32, WIDTH, HEIGHT)
                .build());
    }

    private int addToggle(int x, int y, String labelKey, String tooltipKey, ModConfigSpec.BooleanValue value) {
        addRenderableWidget(new ToggleButton(x, y, WIDTH, HEIGHT, labelKey, tooltipKey, value));
        return y + HEIGHT + GAP;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, getTitle(), (width - font.width(getTitle())) / 2, 16, 0xFFFFFFFF);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        extractTransparentBackground(graphics);
    }

    @Override
    public void onClose() {
        ITRConfig.SPEC.save();
        minecraft.gui.setScreen(parent);
    }

    /** A button that shows the current value and flips it when pressed. */
    private static final class ToggleButton extends Button {
        private final String labelKey;
        private final ModConfigSpec.BooleanValue value;

        private ToggleButton(int x, int y, int width, int height, String labelKey, String tooltipKey,
                             ModConfigSpec.BooleanValue value) {
            super(x, y, width, height, Component.empty(), button -> {
            }, DEFAULT_NARRATION);
            this.labelKey = labelKey;
            this.value = value;
            setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            updateMessage();
        }

        private void updateMessage() {
            Component state = Component.translatable(value.get()
                    ? "invtweaks.settings.on"
                    : "invtweaks.settings.off");
            setMessage(Component.translatable(labelKey).append(": ").append(state));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            value.set(!value.get());
            updateMessage();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(graphics);
            extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }
    }
}