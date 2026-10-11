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
 * Hooks the return of ModMenu's {@code getConfigScreen(modId, parent)} to
 * record the {@code (modId, screen)} pair in {@link ModConfigKeybindsRegistry}.
 * This is the simplest way to tell which mod owns a config screen: ModMenu has
 * the mod ID directly, so no stack walking is needed.
 *
 * <p>ModMenu is a soft dependency ({@code modCompileOnly}). Without ModMenu,
 * this mixin's target class is missing and the mixin is silently skipped
 * (Fabric's usual behavior for absent classes). The registry then stays
 * empty, and the overlay button and YACL stack-walk fallback still find
 * keybinds their own way.
 *
 * <p>Narrow injections only: HEAD sets the current mod and RETURN reads the
 * returned screen; ModMenu's own flow is unchanged.
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
