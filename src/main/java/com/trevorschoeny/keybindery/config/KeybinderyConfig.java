package com.trevorschoeny.keybindery.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Keybindery's own mod settings. Backed by {@code config/keybindery.json}.
 * Surfaced via YACL ({@link KeybinderyConfigScreen}) and ModMenu
 * ({@link KeybinderyConfigModMenu}).
 *
 * <p>Per Trev's spec (2026-05-18), Section 1's two configs are:
 * <ul>
 *   <li>{@code simultaneousMode} — toggle, default OFF (= default chord mode:
 *       all keys must be held simultaneously).</li>
 *   <li>{@code simultaneousWindowMs} — int, default 100ms, visible only when
 *       {@code simultaneousMode} is ON. Time window for initial-press events
 *       to count as "simultaneous."</li>
 * </ul>
 *
 * <p>Per-keybind chord assignments are persisted separately, in vanilla's
 * {@code options.txt} via mixin (see {@code GameOptionsChordPersistenceMixin}).
 * Per Trev's Q5 directive: invade {@code options.txt}, not a sidecar.
 *
 * <p>Plain-old fields, not a record, because YACL bindings are field-based
 * and we want runtime mutation when the player toggles via the keybind.
 */
public final class KeybinderyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("keybindery");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("keybindery.json");

    private static KeybinderyConfig INSTANCE = new KeybinderyConfig();

    public static KeybinderyConfig get() { return INSTANCE; }

    // ── Fields ──────────────────────────────────────────────────────────────
    // Public + non-final so YACL can bind/mutate directly. Toggle keybind
    // also flips simultaneousMode at runtime.

    public boolean simultaneousMode = false;
    public int simultaneousWindowMs = 100;

    /**
     * Kill-switch for the F4 controls-screen overhaul. Default {@code false}
     * (Keybindery replaces vanilla's {@link net.minecraft.client.gui.screens.options.controls.KeyBindsScreen}
     * with its search/sort/filter-capable version). Set to {@code true} for
     * users running Controlling alongside Keybindery who prefer Controlling's
     * UX, or for players who just want vanilla's controls screen back.
     */
    public boolean disableControlsScreenReplacement = false;

    // ── Persistence ─────────────────────────────────────────────────────────

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            KeybinderyConfig loaded = GSON.fromJson(json, KeybinderyConfig.class);
            if (loaded != null) INSTANCE = loaded;
        } catch (IOException e) {
            LOGGER.warn("[Keybindery] Failed to load config from {}: {}", CONFIG_PATH, e.getMessage());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            LOGGER.warn("[Keybindery] Failed to save config to {}: {}", CONFIG_PATH, e.getMessage());
        }
    }
}
