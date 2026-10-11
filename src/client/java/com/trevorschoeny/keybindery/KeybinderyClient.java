package com.trevorschoeny.keybindery;

import com.trevorschoeny.keybindery.api.KeybinderyAPIHolder;
import com.trevorschoeny.keybindery.api.KeybinderyAPIImpl;
import com.trevorschoeny.keybindery.chord.ChordPersistence;
import com.trevorschoeny.keybindery.config.KeybinderyConfig;
import com.trevorschoeny.keybindery.screen.ControlsToolbarPanel;
import com.trevorschoeny.keybindery.screen.ModKeybindsOverlayPanel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side entry point for Keybindery. Everything Keybindery does happens
 * on the client. Among other things it sets up:
 * <ul>
 *   <li>Loading the config.</li>
 *   <li>The "Simultaneous Mode" toggle keybind, unbound by default. Pressing
 *       it flips {@link KeybinderyConfig#simultaneousMode} and saves.</li>
 *   <li>A per-tick check that drains the toggle's click queue, so the toggle
 *       fires on each press whichever screen is open.</li>
 * </ul>
 */
public class KeybinderyClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("keybindery");

    /** Keybindery's own keybind category, surfaced in the vanilla Controls screen. */
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("keybindery", "controls"));

    /** The Simultaneous Mode toggle keybind. Unbound by default, so players opt in. */
    public static KeyMapping SIMULTANEOUS_MODE_TOGGLE;

    /**
     * Demo keybind for the in-config keybind API. Bindable from Keybindery's own
     * YACL config screen via
     * {@link com.trevorschoeny.keybindery.api.KeybinderyAPI#createYACLChordOption}.
     * Shows the API working end to end: a YACL chord row that captures and
     * applies a multi-key chord. Logs each time the chord fires so the player
     * sees feedback. (Real consumer mods register their own keybinds and use the
     * API the same way; this one is only a live demo inside Keybindery itself.)
     */
    public static KeyMapping F1_DEMO_CHORD;

    @Override
    public void onInitializeClient() {
        KeybinderyConfig.load();

        // Install the real KeybinderyAPI implementation. Consumer mods calling
        // KeybinderyAPI.getInstance() now get the live implementation instead
        // of the stub. This only happens when Keybindery itself is loaded;
        // otherwise the stub stays and consumer code quietly does nothing, so
        // consumers never crash when Keybindery is missing.
        KeybinderyAPIHolder.install(new KeybinderyAPIImpl());

        SIMULTANEOUS_MODE_TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.keybindery.toggle_simultaneous_mode",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));

        F1_DEMO_CHORD = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.keybindery.f1_demo_chord",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));

        // Apply the saved chords from options.txt once the client has fully
        // started, after every mod's onInitializeClient has run and Fabric has
        // collected their keybinds into Options.keyMappings.
        // Until this fires, OptionsChordPersistenceMixin.save does nothing, so
        // it can't wipe chord lines that haven't been loaded into memory yet.
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                ChordPersistence.applyChordsFromOptionsTxt(client.options));

        // Register the MenuKit toolbar panel (chord-capture button and the sort
        // and filter dropdowns) on Keybindery's controls screen. The vanilla
        // EditBox for name search is a separate widget on the screen itself
        // (see KeybinderyKeyBindsScreen.init).
        ControlsToolbarPanel.install();

        // List each mod's keybinds on config screens opened from ModMenu, in
        // one of two ways:
        //  - YACL screens get a native "Keybinds" tab, injected at the HEAD of
        //    YACL's Builder.build() (see YACLBuilderInjectMixin).
        //  - Other screens get a top-right MenuKit overlay button that opens
        //    the controls screen filtered to that mod's keybinds
        //    (KeybinderyKeyBindsScreen.openWithModFilterFor).
        // Both depend on ModConfigKeybindsRegistry's modId-to-screen lookup,
        // filled in by ModMenuConfigScreenMixin. YACL screens fall back to
        // walking the stack when ModMenu is absent.
        ModKeybindsOverlayPanel.install();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Drain the click queue. Each pending click flips the mode.
            while (SIMULTANEOUS_MODE_TOGGLE.consumeClick()) {
                KeybinderyConfig cfg = KeybinderyConfig.get();
                cfg.simultaneousMode = !cfg.simultaneousMode;
                KeybinderyConfig.save();
                LOGGER.info("[Keybindery] Simultaneous Mode toggled {}",
                        cfg.simultaneousMode ? "ON" : "OFF");
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.literal(
                            "[Keybindery] Simultaneous Mode: " + (cfg.simultaneousMode ? "ON" : "OFF")));
                }
            }
            // Demo chord: log and show an action-bar message each time it
            // fires, so the player can see dispatch working without opening
            // the launcher log.
            while (F1_DEMO_CHORD.consumeClick()) {
                LOGGER.info("[Keybindery] F1 demo chord fired!");
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.literal(
                            "[Keybindery] F1 demo chord fired!"));
                }
            }
        });

        LOGGER.info("[Keybindery] Client initialized");
    }
}
