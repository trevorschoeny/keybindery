package com.trevorschoeny.keybindery.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu entrypoint: surfaces Keybindery's YACL config screen in the mods
 * list. ModMenu is a soft dep — declared {@code compileOnly} in build.gradle
 * and not required at runtime. If ModMenu isn't installed, Fabric never
 * resolves this entrypoint and the class is never loaded.
 */
public final class KeybinderyConfigModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return KeybinderyConfigScreen::build;
    }
}
