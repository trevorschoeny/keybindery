package com.trevorschoeny.keybindery.mixin;

import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.chord.ChordController;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import com.trevorschoeny.keybindery.screen.CurrentModContext;
import com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen;
import com.trevorschoeny.keybindery.screen.ModConfigKeybindsRegistry;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Injects a "Keybinds" category into any YACL config screen built by a
 * mod with registered {@link KeyMapping}s. The mod identifies itself
 * either via {@link CurrentModContext} (set by
 * {@link ModMenuConfigScreenMixin} when ModMenu opens the screen) or via
 * stack-walking + classpath-match at {@code build()} time (fallback for
 * YACL screens opened outside ModMenu).
 *
 * <p>YACL's {@code BuilderImpl.categories} is a plain {@code ArrayList}
 * that {@code build()} freezes into an {@code ImmutableList} via
 * {@code ImmutableList.copyOf}. We {@code @Inject} at HEAD and append the
 * Keybinds category before the freeze happens. Precise injection per
 * §0030 — no {@code @Overwrite}.
 *
 * <p>Soft-dep — YACL is bundled JIJ as a hard runtime dependency, but if
 * a player runs an exotic configuration where YACL is absent, Fabric
 * silently skips this mixin (target class missing).
 */
@Mixin(targets = "dev.isxander.yacl3.impl.YetAnotherConfigLibImpl$BuilderImpl")
public abstract class YACLBuilderInjectMixin {

    @Shadow @Final private List<ConfigCategory> categories;

    @Inject(method = "build", at = @At("HEAD"))
    private void keybindery$injectKeybindsTab(CallbackInfoReturnable<YetAnotherConfigLib> cir) {
        String modId = CurrentModContext.get();
        if (modId == null) modId = keybindery$identifyByStackWalk();
        if (modId == null) return;
        List<KeyMapping> mappings = ModConfigKeybindsRegistry.keybindsFor(modId);
        if (mappings.isEmpty()) return;
        ConfigCategory category = keybindery$buildKeybindsCategory(mappings);
        categories.add(category);
    }

    @Unique
    private static @Nullable String keybindery$identifyByStackWalk() {
        try {
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames ->
                    frames
                            .map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(cls -> {
                                String name = cls.getName();
                                return !name.startsWith("dev.isxander.yacl3.")
                                        && !name.startsWith("com.trevorschoeny.keybindery.")
                                        && !name.startsWith("java.")
                                        && !name.startsWith("jdk.");
                            })
                            .map(YACLBuilderInjectMixin::keybindery$modIdForClass)
                            .filter(java.util.Objects::nonNull)
                            .findFirst()
                            .orElse(null));
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    private static @Nullable String keybindery$modIdForClass(Class<?> cls) {
        try {
            URL location = cls.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) return null;
            Path classPath = Paths.get(location.toURI()).toAbsolutePath();
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                for (Path modPath : mod.getOrigin().getPaths()) {
                    if (modPath.toAbsolutePath().equals(classPath)) {
                        return mod.getMetadata().getId();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Unique
    private static ConfigCategory keybindery$buildKeybindsCategory(List<KeyMapping> mappings) {
        // Capture the mod-id at build-time so the "Open Keybind Menu" button
        // at the bottom can open the controls screen pre-filtered to this
        // mod. The same value drove the keymapping list, so it's correct.
        final String modId = CurrentModContext.get();

        ConfigCategory.Builder catBuilder = ConfigCategory.createBuilder()
                .name(Component.literal("Keybinds"));
        for (KeyMapping km : mappings) {
            // One row per keymapping: name on the left (YACL renders it
            // from the Option's name), chord button + Conflicts + Reset
            // icons clustered on the right (rendered by ChordControllerWidget).
            // The chord button widget shrank when Conflicts/Reset became
            // icons, so the whole row now fits comfortably on one line.
            catBuilder.option(Option.<Chord>createBuilder()
                    .name(Component.translatable(km.getName()))
                    .binding(
                            // YACL's "reset to default" uses this as the
                            // target. Must be the keymapping's actual default
                            // (e.g. Jump → Space), not UNBOUND — otherwise
                            // Reset clears the binding instead of restoring it.
                            IChordKeyMapping.defaultChord(km),
                            () -> IChordKeyMapping.getChord(km),
                            chord -> IChordKeyMapping.updateFromChord(km, chord))
                    .customController(option -> new ChordController(option, km))
                    .build());
        }
        // Escape-hatch button at the end of the tab — opens the full
        // controls screen (no filter). Per Trev (2026-05-24), this isn't
        // mod-scoped: the user is already viewing this mod's keybinds in
        // the tab, so clicking through to the menu means they want to see
        // EVERYTHING and search/filter from there.
        //
        // Standard YACL ButtonOption — label on the left, button verb on
        // the right.
        catBuilder.option(ButtonOption.createBuilder()
                .name(Component.literal("Keybind Menu"))
                .text(Component.literal("Open"))
                .action((screen, opt) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.gui.setScreen(new com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen(
                            mc.gui.screen(), mc.options));
                })
                .build());
        return catBuilder.build();
    }
}
