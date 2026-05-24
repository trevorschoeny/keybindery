package com.trevorschoeny.keybindery.mixin;

import com.terraformersmc.modmenu.ModMenu;
import com.trevorschoeny.keybindery.screen.CurrentModContext;
import com.trevorschoeny.keybindery.screen.ModConfigKeybindsRegistry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks ModMenu's {@code getConfigScreen(modId, parent)} return to record
 * the {@code (modId, screen)} pair into {@link ModConfigKeybindsRegistry}.
 * This is the cleanest cross-mod hook for identifying "which mod owns this
 * config screen" — ModMenu has the mod ID directly; no stack-walking needed.
 *
 * <p>Soft-dep — ModMenu is {@code modCompileOnly}. If a player runs without
 * ModMenu, this mixin's target class is missing and the mixin is silently
 * skipped (Fabric mixin's standard behavior for absent classes). Keybindery's
 * modal still works for any other code path that calls
 * {@link ModConfigKeybindsRegistry#recordConfigScreen} — though none exist
 * today; ModMenu is the only entry point.
 *
 * <p>Precise injection per §0030: single {@code @Inject(at=RETURN)} reading
 * the screen argument; vanilla flow unchanged.
 */
@Mixin(ModMenu.class)
public abstract class ModMenuConfigScreenMixin {

    @Inject(method = "getConfigScreen", at = @At("HEAD"))
    private static void keybindery$setContext(
            String modId, Screen parent, CallbackInfoReturnable<Screen> cir) {
        CurrentModContext.set(modId);
    }

    @Inject(method = "getConfigScreen", at = @At("RETURN"))
    private static void keybindery$captureModConfig(
            String modId, Screen parent, CallbackInfoReturnable<Screen> cir) {
        Screen returned = cir.getReturnValue();
        if (returned != null) {
            ModConfigKeybindsRegistry.recordConfigScreen(modId, returned);
        }
        CurrentModContext.clear();
    }
}
