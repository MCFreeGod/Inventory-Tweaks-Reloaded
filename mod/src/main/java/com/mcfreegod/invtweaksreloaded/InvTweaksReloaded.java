package com.mcfreegod.invtweaksreloaded;

import com.mcfreegod.invtweaksreloaded.config.ITRConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(InvTweaksReloaded.MOD_ID)
public final class InvTweaksReloaded {
    public static final String MOD_ID = "invtweaksreloaded";
    public static final Logger LOG = LoggerFactory.getLogger("InventoryTweaksReloaded");

    public InvTweaksReloaded(IEventBus modEventBus, ModContainer modContainer) {
        // Client-only mod. Everything that touches the game lives behind
        // Dist.CLIENT event subscribers so a dedicated server can load the jar
        // without pulling in client classes.
        modContainer.registerConfig(ModConfig.Type.CLIENT, ITRConfig.SPEC);
    }
}