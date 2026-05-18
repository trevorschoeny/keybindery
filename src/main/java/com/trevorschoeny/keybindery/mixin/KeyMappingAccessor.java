package com.trevorschoeny.keybindery.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/**
 * Accessor mixin for {@link KeyMapping}'s private fields. Needed by the chord
 * system for the same reasons MenuKit needed them (clickCount cleanup, key
 * read/write, MAP rebuild on chord registration).
 *
 * <p>Migrated from MenuKit's {@code KeyMappingAccessor} (2026-05-18) with
 * the {@code menuKit$} prefix renamed to {@code keybindery$}.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("clickCount")
    int keybindery$getClickCount();

    @Accessor("clickCount")
    void keybindery$setClickCount(int count);

    @Accessor("key")
    InputConstants.Key keybindery$getKey();

    @Mutable
    @Accessor("key")
    void keybindery$setKey(InputConstants.Key key);

    @Accessor("MAP")
    static Map<InputConstants.Key, List<KeyMapping>> keybindery$getMap() {
        throw new AssertionError("mixin");
    }
}
