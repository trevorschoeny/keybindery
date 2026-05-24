package com.trevorschoeny.keybindery;

import com.trevorschoeny.keybindery.api.KeybinderyAPIHolder;
import com.trevorschoeny.keybindery.api.KeybinderyAPIImpl;
import com.trevorschoeny.keybindery.chord.ChordPersistence;
import com.trevorschoeny.keybindery.config.KeybinderyConfig;
import com.trevorschoeny.keybindery.screen.ControlsToolbarPanel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

    /**
     * F1 demo keybind. Bindable from Keybindery's own YACL config screen via
     * {@link com.trevorschoeny.keybindery.api.KeybinderyAPI#createYACLChordOption}.
     * Demonstrates the F1 API end-to-end — a YACL chord row that captures and
     * applies a multi-key chord. Logs each time the chord fires so the player
     * sees feedback. (Real consumer mods register their own keybinds and use
     * F1 the same way; this one's just a live demo inside Keybindery itself.)
     */
    public static KeyMapping F1_DEMO_CHORD;

    @Override
    public void onInitializeClient() {
        KeybinderyConfig.load();

        // F1 — install the real KeybinderyAPI implementation. Consumer mods
        // calling KeybinderyAPI.getInstance() now get the live impl instead
        // of the stub. Per Trev's silent-fail directive, this only happens
        // when keybindery main is loaded; otherwise the stub remains and
        // consumer code no-ops gracefully.
        KeybinderyAPIHolder.install(new KeybinderyAPIImpl());

        SIMULTANEOUS_MODE_TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keybindery.toggle_simultaneous_mode",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));

        F1_DEMO_CHORD = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keybindery.f1_demo_chord",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));

        // Apply persisted chord state from options.txt as soon as the
        // client is fully started — after all mods' onInitializeClient
        // ran and Fabric aggregated their keybinds into Options.keyMappings.
        // Until this fires, OptionsChordPersistenceMixin.save no-ops so we
        // don't wipe chord lines we haven't yet loaded into memory.
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                ChordPersistence.applyChordsFromOptionsTxt(client.options));

        // F4 — register the MK-hosted toolbar panel (chord-capture button +
        // sort/filter dropdowns) onto Keybindery's controls screen. The
        // vanilla EditBox for name search lives as a sibling renderable
        // widget on the screen itself (see KeybinderyKeyBindsScreen.init).
        ControlsToolbarPanel.install();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Drain the click queue. Each pending click flips the mode.
            while (SIMULTANEOUS_MODE_TOGGLE.consumeClick()) {
                KeybinderyConfig cfg = KeybinderyConfig.get();
                cfg.simultaneousMode = !cfg.simultaneousMode;
                KeybinderyConfig.save();
                LOGGER.info("[Keybindery] Simultaneous Mode toggled {}",
                        cfg.simultaneousMode ? "ON" : "OFF");
                if (client.player != null) {
                    client.player.displayClientMessage(Component.literal(
                            "[Keybindery] Simultaneous Mode: " + (cfg.simultaneousMode ? "ON" : "OFF")), true);
                }
            }
            // F1 demo: log + action-bar message each time the demo chord
            // fires so visible feedback proves end-to-end dispatch works
            // without making the player open the launcher log.
            while (F1_DEMO_CHORD.consumeClick()) {
                LOGGER.info("[Keybindery] F1 demo chord fired!");
                if (client.player != null) {
                    client.player.displayClientMessage(Component.literal(
                            "[Keybindery] F1 demo chord fired!"), true);
                }
            }
        });

        LOGGER.info("[Keybindery] Client initialized");
    }
}
