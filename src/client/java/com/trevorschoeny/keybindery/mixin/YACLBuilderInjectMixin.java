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
 * Adds a "Keybinds" category to any YACL config screen built by a mod that
 * has registered {@link KeyMapping}s. The mod is identified either through
 * {@link CurrentModContext} (set by {@link ModMenuConfigScreenMixin} when
 * ModMenu opens the screen) or, for YACL screens opened outside ModMenu, by
 * walking the stack and matching the caller's class path at {@code build()}.
 *
 * <p>YACL's {@code BuilderImpl.categories} is a plain {@code ArrayList} that
 * {@code build()} freezes into an {@code ImmutableList} with
 * {@code ImmutableList.copyOf}. This injects at HEAD and appends the Keybinds
 * category before that happens. No {@code @Overwrite}.
 *
 * <p>YACL is bundled (Jar-in-Jar) as a hard runtime dependency, but if some
 * unusual setup has no YACL, Fabric silently skips this mixin because its
 * target class is missing.
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
        // The mod id at build time, the same value that chose the key mappings.
        // (The Keybind Menu button below opens the unfiltered menu, so it
        // doesn't use this.)
        final String modId = CurrentModContext.get();

        ConfigCategory.Builder catBuilder = ConfigCategory.createBuilder()
                .name(Component.literal("Keybinds"));
        for (KeyMapping km : mappings) {
            // One row per key mapping: the name on the left (YACL renders it
            // from the Option's name), and the chord button with the Conflicts
            // and Reset icons on the right (rendered by ChordControllerWidget).
            // Conflicts and Reset are icons, so the whole row fits on one line.
            catBuilder.option(Option.<Chord>createBuilder()
                    .name(Component.translatable(km.getName()))
                    .binding(
                            // YACL's "reset to default" uses this as the
                            // target. It must be the key mapping's actual default
                            // (Jump is Space, for example), not UNBOUND, or Reset
                            // would clear the binding instead of restoring it.
                            IChordKeyMapping.defaultChord(km),
                            () -> IChordKeyMapping.getChord(km),
                            chord -> IChordKeyMapping.updateFromChord(km, chord))
                    .customController(option -> new ChordController(option, km))
                    .build());
        }
        // A button at the end of the tab that opens the full controls screen,
        // with no filter. It isn't limited to this mod: the player is already
        // looking at this mod's keybinds in the tab, so going to the menu
        // means they want to see everything and search or filter from there.
        //
        // A standard YACL ButtonOption: label on the left, button on the right.
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
