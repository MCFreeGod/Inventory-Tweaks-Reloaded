package com.mcfreegod.invtweaksreloaded.client;

import com.mcfreegod.invtweaksreloaded.InvTweaksReloaded;
import com.mcfreegod.invtweaksreloaded.client.gui.SettingsButton;
import com.mcfreegod.invtweaksreloaded.client.gui.SettingsScreen;
import com.mcfreegod.invtweaksreloaded.client.gui.SortButton;
import com.mcfreegod.invtweaksreloaded.config.ITRConfig;
import com.mcfreegod.invtweaksreloaded.core.ContainerLayout;
import com.mcfreegod.invtweaksreloaded.core.SortMode;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Everything that has to react to the player: the key binding, the button
 * layout and the middle-click shortcut.
 */
@EventBusSubscriber(modid = InvTweaksReloaded.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    /**
     * Original default: the R key. 26.3 key mappings store SDL scancodes, not
     * GLFW keycodes, so this has to be {@link InputConstants#KEY_R} (21) and
     * not the old 82.
     */
    private static final int SORT_KEY = InputConstants.KEY_R;

    private static KeyMapping sortKey;

    /** Edge detection for the key while no screen is open. */
    private static boolean worldKeyDown;
    /**
     * Edge detection for the key inside container screens. {@code KeyPressed}
     * also fires for auto-repeat, and the event carries no action, so a flag is
     * the only way to sort once per physical press.
     */
    private static boolean screenKeyDown;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        // A category of its own puts the binding in a separate section at the
        // end of the controls screen, the way 1.7.10 and 1.12.2 showed it,
        // instead of burying it among the vanilla inventory keys.
        KeyMapping.Category category = new KeyMapping.Category(
                Identifier.fromNamespaceAndPath(InvTweaksReloaded.MOD_ID, "invtweaksreloaded"));
        event.registerCategory(category);
        sortKey = new KeyMapping("key.invtweaksreloaded.sort", InputConstants.Type.KEYBOARD, SORT_KEY,
                category);
        event.register(sortKey);
    }

    /**
     * In-world sorting. The original mod polls the key every tick, so pressing R
     * with no screen open sorts the player's inventory. The server accepts these
     * clicks because the player inventory menu is always open on its side.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (sortKey == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            // Inside a screen the key is handled by onKeyPressed; clear the edge
            // state so a key released while a screen was closing cannot stick.
            worldKeyDown = false;
            screenKeyDown = false;
            return;
        }
        boolean down = sortKey.isDown();
        if (down && !worldKeyDown) {
            worldKeyDown = true;
            SortDispatcher.sortPlayerInventoryInWorld();
        } else if (!down) {
            worldKeyDown = false;
        }
    }

    /**
     * The R key inside container screens. The vanilla key handling only feeds
     * {@link KeyMapping#click} while no screen is open, so a screen-open sort
     * has to come from the screen event.
     */
    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (sortKey == null || screenKeyDown) {
            return;
        }
        Screen screen = event.getScreen();
        if (!SortDispatcher.isSupportedScreen(screen)) {
            return;
        }
        // A modded container screen may put a search field in the frame; typing
        // an "r" there must not sort anything.
        if (screen.getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) {
            return;
        }
        if (!sortKey.matches(event.getKeyEvent())) {
            return;
        }
        screenKeyDown = true;
        SortDispatcher.sortPlayerInventory(screen);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (sortKey != null && sortKey.matches(event.getKeyEvent())) {
            screenKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }
        if (containerScreen instanceof CreativeModeInventoryScreen) {
            return;
        }
        if (!ITRConfig.SHOW_CHEST_BUTTONS.get() || !SortDispatcher.canSort(screen)) {
            return;
        }

        screenKeyDown = false;

        ContainerLayout layout = ContainerLayout.of(containerScreen.getMenu());
        if (layout.isChestLike()) {
            addChestButtons(event, containerScreen);
        } else {
            addSettingsButton(event, containerScreen);
        }
    }

    /**
     * The classic layout: four 10x10 buttons in the top-right corner of the
     * frame, 12 pixels apart. Left to right this is default, vertical,
     * horizontal, settings - the same order and offsets as 1.12.2, which put
     * them at {@code xSize - 16 - 37}, {@code -25}, {@code -13} and {@code -1}.
     */
    private static void addChestButtons(ScreenEvent.Init.Post event, AbstractContainerScreen<?> screen) {
        int right = screen.leftPos + screen.imageWidth;
        int top = screen.topPos + 5;

        event.addListener(new SortButton(right - 53, top, SortMode.DEFAULT,
                Component.translatable("invtweaks.button.chest1.tooltip"),
                () -> SortDispatcher.sortContainer(screen, SortMode.DEFAULT)));
        event.addListener(new SortButton(right - 41, top, SortMode.VERTICAL,
                Component.translatable("invtweaks.button.chest2.tooltip"),
                () -> SortDispatcher.sortContainer(screen, SortMode.VERTICAL)));
        event.addListener(new SortButton(right - 29, top, SortMode.HORIZONTAL,
                Component.translatable("invtweaks.button.chest3.tooltip"),
                () -> SortDispatcher.sortContainer(screen, SortMode.HORIZONTAL)));
        addSettingsButton(event, screen);
    }

    private static void addSettingsButton(ScreenEvent.Init.Post event, AbstractContainerScreen<?> screen) {
        int right = screen.leftPos + screen.imageWidth;
        int top = screen.topPos + 5;
        event.addListener(new SettingsButton(right - 17, top,
                Component.translatable("invtweaks.button.settings.tooltip"),
                () -> Minecraft.getInstance().gui.setScreen(new SettingsScreen(screen))));
    }

    /**
     * Middle-click sorting. Handled before the screen so the click never turns
     * into a "pick block" or a slot interaction.
     */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!ITRConfig.ENABLE_MIDDLE_CLICK.get()) {
            return;
        }
        if (event.getButton() != InputConstants.MOUSE_BUTTON_MIDDLE) {
            return;
        }
        Screen screen = event.getScreen();
        if (!SortDispatcher.isSupportedScreen(screen) || !SortDispatcher.canSort(screen)) {
            return;
        }
        if (SortDispatcher.sortHoveredSection(screen)) {
            event.setCanceled(true);
        }
    }
}
