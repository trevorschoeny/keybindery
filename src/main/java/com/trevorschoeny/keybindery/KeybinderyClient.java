package com.trevorschoeny.keybindery;

import com.trevorschoeny.keybindery.config.KeybinderyConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side entry point for Keybindery.
 *
 * <p>Section 1 wires up:
 * <ul>
 *   <li>Config load on init.</li>
 *   <li>Toggle keybind "Simultaneous Mode" — default unbound. When pressed,
 *       flips {@link KeybinderyConfig#simultaneousMode} and saves.</li>
 *   <li>Per-tick check that drains the toggle keybind's click queue so the
 *       toggle fires on each press regardless of which screen is open.</li>
 * </ul>
 *
 * <p>Per the establishing ADR (§0030 in @ Trevlar Mods canon), this mod is
 * client-only.
 */
public class KeybinderyClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("keybindery");

    /** Keybindery's own keybind category, surfaced in the vanilla Controls screen. */
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("keybindery", "controls"));

    /** The Simultaneous Mode toggle keybind. Default unbound — players opt in. */
    public static KeyMapping SIMULTANEOUS_MODE_TOGGLE;

    @Override
    public void onInitializeClient() {
        KeybinderyConfig.load();

        SIMULTANEOUS_MODE_TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keybindery.toggle_simultaneous_mode",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Drain the click queue. Each pending click flips the mode.
            // consumeClick() returns true once per pending click; the loop
            // handles rapid double-fires (rare in practice but correct).
            while (SIMULTANEOUS_MODE_TOGGLE.consumeClick()) {
                KeybinderyConfig cfg = KeybinderyConfig.get();
                cfg.simultaneousMode = !cfg.simultaneousMode;
                KeybinderyConfig.save();
                LOGGER.info("[Keybindery] Simultaneous Mode toggled {}",
                        cfg.simultaneousMode ? "ON" : "OFF");
            }
        });

        LOGGER.info("[Keybindery] Client initialized");
    }
}
