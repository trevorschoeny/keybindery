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
 * Keybindery's own mod settings, saved in {@code config/keybindery.json} and
 * shown through YACL ({@link KeybinderyConfigScreen}) and ModMenu
 * ({@link KeybinderyConfigModMenu}).
 *
 * <p>The chord settings:
 * <ul>
 *   <li>{@code simultaneousMode}: toggle, default off (the default chord mode,
 *       where all keys must be held down together).</li>
 *   <li>{@code simultaneousWindowMs}: int, default 100ms, shown only when
 *       {@code simultaneousMode} is on. How close together the first presses
 *       must be to count as "simultaneous".</li>
 * </ul>
 *
 * <p>Per-keybind chords are saved separately, in vanilla's {@code options.txt}
 * via a mixin (see {@code OptionsChordPersistenceMixin}), rather than in a file
 * of their own.
 *
 * <p>Plain fields, not a record, because YACL binds to fields and the toggle
 * keybind changes them at runtime.
 */
public final class KeybinderyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("keybindery");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("keybindery.json");

    private static KeybinderyConfig INSTANCE = new KeybinderyConfig();

    public static KeybinderyConfig get() { return INSTANCE; }

    // Public and non-final so YACL can bind and change them directly. The
    // toggle keybind also flips simultaneousMode at runtime.

    public boolean simultaneousMode = false;
    public int simultaneousWindowMs = 100;

    /**
     * Turns off Keybindery's controls screen. Default {@code false} (Keybindery
     * replaces vanilla's {@link net.minecraft.client.gui.screens.options.controls.KeyBindsScreen}
     * with its version that can search, sort and filter). Set to {@code true}
     * for players running Controlling alongside Keybindery who prefer
     * Controlling's screen, or who just want vanilla's controls screen back.
     */
    public boolean disableControlsScreenReplacement = false;

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
