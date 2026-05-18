package com.trevorschoeny.keybindery;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side entry point for Keybindery.
 *
 * <p>Section 1a (scaffolding only): nothing wires up yet. Subsequent sections
 * register the chord-state manager, the Simultaneous Mode toggle keybind,
 * the YACL config screen, and the F4 controls-screen swap mixin.
 *
 * <p>Per the establishing ADR (§0030 in @ Trevlar Mods canon), this mod is
 * client-only. The fabric.mod.json declares "environment": "client", so no
 * server entry point is needed.
 */
public class KeybinderyClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("keybindery");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Keybindery] Client initialized");
    }
}
