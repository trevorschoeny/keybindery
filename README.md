# Keybindery

A chord-keybind library and a reworked Controls screen for Fabric. Bind multi-key combinations to any key mapping, vanilla or modded. Find and rebind a mod's keybinds from inside that mod's own config screen. Search, sort, and filter the controls screen.

## Features

### Keybinds button in the pause menu

The pause menu has a Keybinds button, next to the row of square buttons, that opens the Key Binds menu.

### A better Key Binds menu

The vanilla Key Binds menu gets a search box, a search-by-chord button, and sort and filter dropdowns along the top. Each row has a Conflicts icon, which jumps to the binds it clashes with, and a Reset icon. Long keybind names scroll.

### Chord keybinds

You can bind a combination like `Ctrl + Shift + K` or `X + Z` to any keybind in the game, vanilla or modded. Single-key bindings keep working as they did.

### Simultaneous Mode

Normally a chord fires while all its keys are held down. Turn on Simultaneous Mode and the keys also have to go down together, within a window you set between 50 and 500 milliseconds (100 by default). Holding Ctrl for a while and then tapping K stops counting as `Ctrl + K`. It's off by default. Turn it on in Keybindery's settings, or bind a key to switch it on and off.

### Keybinds in each mod's config

Every mod's keybinds show up in that mod's own config screen: on a "Keybinds" tab if it uses YACL, or behind a small "Keybinds" button otherwise. This works for any mod listed in Mod Menu. You don't have to dig through the Key Binds menu for one mod's bindings.

### An API for other mods

Through Keybindery's API, a mod can build keybind capture straight into its own settings. In a YACL config, one call adds a full keybind row with the chord button, Conflicts icon and Reset icon. A mod with its own custom settings screen can read and set chords directly and draw the rows itself. See Using it as a mod developer below.

### On servers

Keybindery installs on both client and server. On a server it does nothing by itself. It's there only because mods that require it, like Inventory Plus, load on servers too.

## Install

Drop the jar in your `mods/` folder along with Fabric API. YACL is bundled inside the jar.

Requires:

- Minecraft 26.3 (this build)
- Fabric Loader 0.19.5 or newer
- Fabric API
- MenuKit 7.0.0 or newer, below 8.0.0

[YACL](https://modrinth.com/mod/yacl) 3.9.7 comes bundled, so there is no separate install.

Recommended: [Mod Menu](https://modrinth.com/mod/modmenu). The per-mod keybind lists use it to work out which keybinds belong to which mod.

## Using it as a player

### Bind a chord to any keybind

Open the Key Binds menu (Options, Controls, Key Binds). Click any row's chord button and press the chord you want: hold all the keys together, then release. The chord saves.

To clear a binding, right-click the chord button during normal browsing, or press Delete or Backspace mid-capture. Press Escape mid-capture to cancel without changing anything.

### Turn on Simultaneous Mode

Open Keybindery's settings through Mod Menu and turn on Simultaneous Mode. A Simultaneous Window setting appears under it: larger is more forgiving about how close together you press the keys, smaller is stricter. To switch the mode on the fly, bind the Simultaneous Mode key in the Keybindery section of Controls. Each press flips it and shows the new state above your hotbar.

### Open your keybinds from the pause menu

Press Escape in game. The Keybinds button is at the right end of the row of square buttons under Advancements and Statistics. Click it to open the Key Binds menu. Done takes you back to the pause menu.

### Find a mod's keybinds quickly

Open any mod's config screen through Mod Menu. If the mod uses YACL, there's a "Keybinds" tab at the top. Otherwise look for a small "Keybinds" button in the top-right corner. Clicking it opens the controls screen filtered to that mod's keybinds.

### Search and filter the controls screen

At the top of the Key Binds menu:

- Search by name: type a name to narrow the list.
- Search by key chord: click and press any chord. The list shows every keybind that shares a key with that chord, which is the quickest way to find conflicts.
- Sort: by category (default), alphabetical, or by key chord.
- Filter: only conflicting binds, only unbound binds, or no filter.

Press Escape while capturing in Search by key chord to clear the filter.

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

That one call adds the label, chord button, Conflicts icon, and Reset icon to the category. It also marks the mapping as claimed, so Keybindery won't list it a second time on a fallback Keybinds tab.

### Mark a keybind as surfaced in your own UI

If you render keybind rows yourself instead of through `createYACLChordOption`, tell Keybindery to skip auto-listing them:

```java
KeybinderyAPI.getInstance().markClaimed(myKey);
```

You can call it more than once.

### Stub behavior

`keybindery-api` is a separate jar with no runtime dependency on Keybindery. If a player doesn't have Keybindery installed, every API call goes to a stub that does nothing: chords read as `UNBOUND`, writes do nothing, and `createYACLChordOption` returns a single disabled "Keybindery not installed" label. Your mod's UI still renders.

## Compatibility

Vanilla single-key bindings keep working. Mods that don't opt into chords behave exactly as before.

Keybindery changes `KeyMapping` through narrow mixin injections and never overwrites a vanilla method, so other mods that touch the same code still run. That's how it works alongside input mods such as Controlify.

Cross-mod conflict detection uses one rule: any shared key counts. A mod that defaults a keybind to `S` is flagged as conflicting with vanilla's walk-back `S`. The only overlap not flagged is vanilla against vanilla, since Mojang designed those defaults to coexist.

Chords are saved alongside vanilla's `options.txt`. Removing Keybindery later restores single-key behavior for every bind.

## License

MIT. See [LICENSE](https://github.com/trevorschoeny/keybindery/blob/main/LICENSE).

## Credits

Inspired by [Amecs](https://modrinth.com/mod/amecs) for multi-key bindings and [Controlling](https://modrinth.com/mod/controlling) for controls-screen search.
