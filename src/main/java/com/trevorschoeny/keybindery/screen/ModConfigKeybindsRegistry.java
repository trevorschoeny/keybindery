package com.trevorschoeny.keybindery.screen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Per-screen registry tracking which mod's config screen is currently open
 * + which mod's keybinds the modal should display when invoked.
 *
 * <p>Populated by {@link com.trevorschoeny.keybindery.mixin.ModMenuConfigScreenMixin}:
 * every time ModMenu opens a config screen via {@code getConfigScreen(modId, parent)},
 * the returned Screen is mapped to its modId here. Weak keys — when the screen
 * is GC'd, the entry vanishes automatically.
 *
 * <p>The modal opens via {@link #openFor(String)} (called from the top-right
 * "Keybinds" button) and closes via {@link #close()}. Modal visibility is
 * supplier-driven (MenuKit §0026 — elements are lenses, not stores) — the
 * modal panel reads {@link #isOpen()} every frame.
 */
public final class ModConfigKeybindsRegistry {

    private ModConfigKeybindsRegistry() {}

    /** Screens whose mod-id is known (because they came from ModMenu). */
    private static final WeakHashMap<Screen, String> SCREEN_TO_MOD = new WeakHashMap<>();

    /** Active modal state. Null when closed. */
    private static @Nullable String activeModId = null;

    // ── Mixin entry points ─────────────────────────────────────────────────

    public static void recordConfigScreen(String modId, Screen screen) {
        if (modId == null || screen == null) return;
        SCREEN_TO_MOD.put(screen, modId);
    }

    public static @Nullable String modIdFor(Screen screen) {
        return screen == null ? null : SCREEN_TO_MOD.get(screen);
    }

    // ── Modal state ────────────────────────────────────────────────────────

    public static void openFor(String modId) { activeModId = modId; }
    public static void close() { activeModId = null; }
    public static boolean isOpen() { return activeModId != null; }
    public static @Nullable String activeModId() { return activeModId; }

    // ── Keybind lookup ─────────────────────────────────────────────────────

    /**
     * Returns the keymappings belonging to {@code modId}, identified by
     * translation-key prefix {@code key.<modId>.*}. Includes claimed mappings
     * — per Trev (2026-05-24), the modal lists EVERY keybind for the mod
     * regardless of whether the mod's own config UI already surfaces them.
     */
    public static List<KeyMapping> keybindsFor(String modId) {
        List<KeyMapping> out = new ArrayList<>();
        if (modId == null) return out;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyMappings == null) return out;
        String prefix = "key." + modId + ".";
        for (KeyMapping km : mc.options.keyMappings) {
            String name = km.getName();
            if (name != null && name.startsWith(prefix)) out.add(km);
        }
        return out;
    }
}
