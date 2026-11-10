package com.mcfreegod.invtweaksreloaded.client;

import com.mcfreegod.invtweaksreloaded.core.AutoRefill;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Drives the per-tick auto-refill watcher. */
@EventBusSubscriber(modid = com.mcfreegod.invtweaksreloaded.InvTweaksReloaded.MOD_ID, value = Dist.CLIENT)
public final class AutoRefillEvents {
    private static final AutoRefill AUTO_REFILL = new AutoRefill();

    private AutoRefillEvents() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        AUTO_REFILL.tick();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        AUTO_REFILL.reset();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AUTO_REFILL.reset();
    }
}