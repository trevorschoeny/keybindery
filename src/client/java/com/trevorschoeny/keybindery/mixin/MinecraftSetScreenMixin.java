package com.trevorschoeny.keybindery.mixin;

import com.trevorschoeny.keybindery.config.KeybinderyConfig;
import com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * F4 — replace vanilla {@link KeyBindsScreen} with Keybindery's
 * {@link KeybinderyKeyBindsScreen} at the moment the screen is opened.
 * Pattern borrowed verbatim from Controlling (jaredlll08): one
 * {@link ModifyVariable @ModifyVariable} on {@link Gui#setScreen}
 * at HEAD intercepts the {@code Screen} argument; if it's exactly
 * {@code KeyBindsScreen} (not a subclass — important, since our own screen
 * IS a subclass), construct ours and return it instead.
 *
 * <p>Per §0003 (Keybindery canon): this is a HEAD argument-modify, not
 * an @Overwrite, so other mods injecting into setScreen still see and
 * can further modify the screen instance. Mod-coexistence preserved.
 *
 * <p>Gate: when {@link KeybinderyConfig#disableControlsScreenReplacement}
 * is true (kill-switch), the swap is skipped — useful for players running
 * Controlling alongside who prefer Controlling's UX.
 */
@Mixin(Gui.class)   // 26.2: setScreen moved Minecraft → Gui (screen/overlay manager)
public abstract class MinecraftSetScreenMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen keybindery$swapKeyBindsScreen(Screen opened) {
        if (opened == null) return opened;
        // Exact-class check — we don't recursively swap our own subclass.
        if (opened.getClass() != KeyBindsScreen.class) return opened;
        if (KeybinderyConfig.get().disableControlsScreenReplacement) return opened;

        OptionsSubScreenAccessor accessor = (OptionsSubScreenAccessor) opened;
        return new KeybinderyKeyBindsScreen(
                accessor.keybindery$getLastScreen(),
                accessor.keybindery$getOptions());
    }
}
