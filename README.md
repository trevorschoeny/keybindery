# Keybindery

A chord-keybind library and controls-screen overhaul for Fabric. Bind multi-key combinations to any keymapping, vanilla or modded. Find and rebind a mod's keybinds from inside that mod's own config screen. Search, sort, and filter the controls screen.

## Features

You can bind a combination like `Ctrl + Shift + K` or `X + Z` to any keybind in the game, vanilla or modded. Single-key bindings keep working as they did.

Every mod's keybinds appear on a "Keybinds" tab in that mod's config screen if it uses YACL, or behind a small "Keybinds" button on other config screens, so you don't have to dig through the Key Binds menu for one mod's bindings.

A Keybinds button in the pause menu, next to the row of square buttons, opens the Key Binds menu in one click.

The vanilla Key Binds menu gets a search box, a search-by-chord button, and sort and filter dropdowns along the top. Each row has a Conflicts icon, which jumps to the binds it clashes with, and a Reset icon. Long keybind names scroll.

Keybindery runs on the client only. Servers don't need it.

## Install

Drop the jar in your `mods/` folder along with Fabric API. YACL is bundled inside the jar.

Requires:
- Minecraft 26.2 (this build)
- Fabric Loader 0.19.5 or newer
- Fabric API
- MenuKit 6.0.0 or newer, below 7.0.0

[YACL](https://modrinth.com/mod/yacl) 3.9.6 comes bundled, so there is no separate install.

Recommended: [Mod Menu](https://modrinth.com/mod/modmenu). The auto-listed-mod-keybinds feature uses it to work out which keybinds belong to which mod.

## Using it as a player

### Bind a chord to any keybind

Open the Key Binds menu (Options, Controls, Key Binds). Click any row's chord button and press the chord you want: hold all the keys together, then release. The chord saves.

To clear a binding, right-click the chord button during normal browsing, or press Delete or Backspace mid-capture. Press Escape mid-capture to cancel without changing anything.

### Open your keybinds from the pause menu

Press Escape in game. The Keybinds button is at the right end of the row of square buttons under Advancements and Statistics. Click it to open the Key Binds menu. Done takes you back to the pause menu.

### Find a mod's keybinds quickly

Open any mod's config screen through Mod Menu. If the mod uses YACL, there is a "Keybinds" tab at the top. Otherwise look for a small "Keybinds" button in the top-right corner of the config screen. Clicking it opens the controls screen filtered to that mod's keybinds.

### Search and filter the controls screen

At the top of the Key Binds menu:

- Search keybinds: type a name to narrow the list.
- Search Keybind: click and press any chord. The list shows every keybind that shares a key with that chord, which is the quickest way to find conflicts.
- Sort: by category (default), alphabetical, or by bound key.
- Filter: only conflicting binds, only unbound binds, or no filter.

Press Escape while capturing in the Search Keybind field to clear the filter.

### Conflict and Reset icons on each row

The warning icon opens the controls screen filtered to every keybind this one conflicts with. It is grayed out when the row has no conflicts.

The reset icon returns the keybind to its default. It is grayed out when the keybind is already at default.

## Using it as a mod developer

Keybindery ships a small API jar (`keybindery-api`) you depend on at compile time. Players still install the regular Keybindery mod.

### Read or write a chord

```java
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;

KeyMapping myKey = ...; // your existing KeyMapping
KeybinderyAPI api = KeybinderyAPI.getInstance();

Chord chord = api.getChord(myKey);     // current chord, or Chord.UNBOUND
api.setChord(myKey, newChord);          // overwrite
```

### Add chord-binding rows to your YACL config

```java
ConfigCategory keybindsCategory = ConfigCategory.createBuilder()
        .name(Component.literal("Controls"))
        .options(KeybinderyAPI.getInstance().createYACLChordOption(
                myKey, Component.literal("My action"), OptionDescription.EMPTY))
        .build();
```

That one call adds the label, chord button, Conflicts icon, and Reset icon to the category. The mapping is marked as claimed, so Keybindery's auto-list won't duplicate it on a fallback Keybinds tab.

### Mark a keybind as surfaced in your own UI

If you render keybind rows yourself instead of through `createYACLChordOption`, tell Keybindery to skip auto-listing them:

```java
KeybinderyAPI.getInstance().markClaimed(myKey);
```

You can call it more than once.

### Stub behavior

`keybindery-api` is a separate jar with no runtime dependency on Keybindery. If a player runs without Keybindery installed, every API call falls through to a no-op stub: chords read as `UNBOUND`, writes do nothing, and `createYACLChordOption` returns a single disabled "Keybindery not installed" label. Your mod's UI still renders.

## Compatibility

Vanilla single-key bindings keep working. Mods that don't opt into chords behave exactly as before.

Keybindery changes `KeyMapping` through narrow mixin injections and never overwrites a vanilla method, so other mods that touch the same code still run. This is how it coexists with input mods such as Controlify.

Cross-mod conflict detection uses one rule: any shared key counts. A mod that defaults a keybind to `S` is flagged as conflicting with vanilla's walk-back `S`. The only overlap not flagged is vanilla against vanilla, since Mojang designed those defaults to coexist.

Chords are saved alongside vanilla's `options.txt`. Removing Keybindery later restores single-key behavior for every bind.

## License

MIT. See [LICENSE](https://github.com/trevorschoeny/keybindery/blob/main/LICENSE).

## Credits

Inspired by [Amecs](https://modrinth.com/mod/amecs) for multi-key bindings and [Controlling](https://modrinth.com/mod/controlling) for controls-screen search.
