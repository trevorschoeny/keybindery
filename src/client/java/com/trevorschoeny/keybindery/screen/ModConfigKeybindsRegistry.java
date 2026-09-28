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
 * so the overlay button can resolve the mod-id at click time.
 *
 * <p>Populated by {@link com.trevorschoeny.keybindery.mixin.ModMenuConfigScreenMixin}:
 * every time ModMenu opens a config screen via {@code getConfigScreen(modId, parent)},
 * the returned Screen is mapped to its modId here. Weak keys — when the screen
 * is GC'd, the entry vanishes automatically.
 *
 * <p>The modal-state helpers (openFor/close/isOpen/activeModId) were removed
 * in Section 5 when the modal flow was replaced with direct navigation to
 * {@link KeybinderyKeyBindsScreen#openWithModFilterFor}.
 */
public final class ModConfigKeybindsRegistry {

    private ModConfigKeybindsRegistry() {}

    /** Screens whose mod-id is known (because they came from ModMenu). */
    private static final WeakHashMap<Screen, String> SCREEN_TO_MOD = new WeakHashMap<>();

    // ── Mixin entry points ─────────────────────────────────────────────────

    public static void recordConfigScreen(String modId, Screen screen) {
        if (modId == null || screen == null) return;
        SCREEN_TO_MOD.put(screen, modId);
    }

    public static @Nullable String modIdFor(Screen screen) {
        return screen == null ? null : SCREEN_TO_MOD.get(screen);
    }

    // ── Keybind lookup ─────────────────────────────────────────────────────

    /**
     * Returns the keymappings belonging to {@code modId}, identified by
     * translation-key prefix {@code key.<modId>.*}. Includes claimed
     * mappings — per Trev (2026-05-24), surfaces list EVERY keybind for
     * the mod regardless of whether the mod's own config UI already
     * exposes them. Used by {@link com.trevorschoeny.keybindery.mixin.YACLBuilderInjectMixin}
     * to gate auto-tab injection.
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
