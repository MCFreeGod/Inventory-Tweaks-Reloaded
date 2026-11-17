package com.mcfreegod.invtweaksreloaded.client;

import com.mcfreegod.invtweaksreloaded.client.gui.SettingsScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only wiring that has to run while the mod is being constructed.
 *
 * <p>Kept out of the main mod class so a dedicated server never loads a class
 * that references {@code Screen}.
 */
public final class ClientSetup {
    private ClientSetup() {
    }

    /**
     * Adds a "Config" button to the mod list that opens the same screen as the
     * "..." button in container screens.
     *
     * <p>Without this, turning the container buttons off would leave the
     * settings screen unreachable: it was only ever opened from a button drawn
     * inside a container screen.
     */
    public static void registerConfigScreen(ModContainer container) {
        // The cast picks the (Class, instance) overload; without it the
        // lambda's target type is ambiguous between that and (Class, Supplier).
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (modContainer, parent) -> new SettingsScreen(parent));
    }
}