package com.trevorschoeny.keybindery.screen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Records which mod each open config screen belongs to, so the overlay button
 * can find the mod ID when clicked.
 *
 * <p>Filled in by {@link com.trevorschoeny.keybindery.mixin.ModMenuConfigScreenMixin}:
 * each time ModMenu opens a config screen via {@code getConfigScreen(modId, parent)},
 * the returned Screen is mapped to its modId here. The keys are weak, so an
 * entry disappears when its screen is garbage collected.
 */
public final class ModConfigKeybindsRegistry {

    private ModConfigKeybindsRegistry() {}

    /** Screens whose mod ID is known (because they came from ModMenu). */
    private static final WeakHashMap<Screen, String> SCREEN_TO_MOD = new WeakHashMap<>();

    public static void recordConfigScreen(String modId, Screen screen) {
        if (modId == null || screen == null) return;
        SCREEN_TO_MOD.put(screen, modId);
    }

    public static @Nullable String modIdFor(Screen screen) {
        return screen == null ? null : SCREEN_TO_MOD.get(screen);
    }

    /**
     * Returns the key mappings belonging to {@code modId}, found by the
     * translation-key prefix {@code key.<modId>.*}. Includes claimed mappings:
     * these lists show every keybind for the mod, whether or not the mod's
     * own config UI already shows them. Used by
     * {@link com.trevorschoeny.keybindery.mixin.YACLBuilderInjectMixin} to
     * decide whether to add the tab.
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
