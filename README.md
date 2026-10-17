# Keybindery

Keybindery lets any keybind in the game use a combination of keys, and makes your keybinds easier to find and change. You can bind `Ctrl + Shift + K` or `X + Z` to any action, vanilla or modded, search and filter the Key Binds menu, and edit a mod's keybinds from inside that mod's own settings.

I also built it as a library, so my other mods can put keybind buttons in their own settings screens, and other modders can do the same.

It runs on the client. You can also install it on a server, where it does nothing by itself, so that mods that require it, like Inventory Plus, can load there too.

## Features

### Chord keybinds

- Bind a combination like `Ctrl + Shift + K` or `X + Z` to any keybind, vanilla or modded. Mouse buttons can be part of a chord too.
- Single-key keybinds keep working as they did.
- Simultaneous Mode, which is off by default, makes the keys of a chord go down together within a short window you set.

### A better Key Binds menu

- A search box, a Search by key chord button, and Sort and Filter dropdowns sit along the top.
- Each row has a Conflicts icon that shows every keybind sharing a key with it, and a Reset icon.
- Categories fold up with a click, and keybinds that vanilla would put under Miscellaneous are grouped by the mod they come from.

### Keybinds button in the pause menu

- A small button at the end of the pause menu's row of icon buttons opens the Key Binds menu.

### Keybinds in each mod's settings

- A mod with a YACL config screen gets a Keybinds tab that lists its keybinds.
- Other config screens opened from Mod Menu get a Keybinds button that opens the Key Binds menu filtered to that mod.

### Keybinds inside other mods

- Mods built on Keybindery can put chord keybind buttons right in their own settings. Inventory Plus shows its keybinds next to the settings they belong to, and Sandboxes gives each sandbox its own key.

## Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api), for registering keybinds and hooking into the game's client events.
- [MenuKit](https://modrinth.com/mod/menukit) 7.2.0 or newer, below 8.0.0. It draws the toolbar at the top of the Key Binds menu and the Keybinds button on other mods' config screens.

[YACL](https://modrinth.com/mod/yacl) 3.9.7 comes bundled inside the jar, so there is nothing extra to install for it.

## Pairs well with

- [Mod Menu](https://modrinth.com/mod/modmenu). Keybindery's own settings open from Mod Menu, and the Keybinds button on other mods' config screens needs it.

## Compatibility

- Minecraft 26.3 with Fabric Loader 0.19.5 or newer.
- Required on the client and optional on the server.
- Keybindery changes `KeyMapping` through narrow mixin injections and never overwrites a vanilla method, so other mods that touch the same code still run. That's how it works alongside input mods such as Controlify.
- If you'd rather use another mod's controls screen, like Controlling's, or vanilla's, you can turn off Keybindery's replacement in its settings.
- Chords are saved in vanilla's `options.txt`. If you remove Keybindery later, every keybind goes back to a single key.

## How to use

### Open the Key Binds menu

- Press Escape in game. At the right end of the row of small square buttons there is a button with a K key icon. Hovering over it shows "Keybinds".
- Click it to open the Key Binds menu. Done takes you back to the pause menu.
- The menu also opens the usual way, through Options, Controls, Key Binds.

### Bind a chord to a keybind

- Each row is a wide button with the keybind's name on the left and its current key on the right.
- Click the row, then hold down every key of the chord and release them. The chord is the set of keys you held at once, up to six keys, and it saves when the last key comes up.
- Mouse buttons pressed while the row is waiting for keys become part of the chord.
- While the row is waiting for keys, Escape, Delete, or Backspace clears the keybind so it's unbound.
- A chord fires when all of its keys are held down. Every keybind that matches fires, so if `Y` is bound to one action and `Shift + Y` to another, pressing `Shift + Y` fires both.
- Long keybind names scroll inside the row.

### Conflicts and Reset

- Two keybinds conflict when they share any key. A keybind bound to `S` conflicts with vanilla's Walk Backwards on `S`, and `X + Z` conflicts with `X`. The one overlap that isn't flagged is two vanilla keybinds that are both still at their default keys.
- A conflicting keybind shows its key in yellow brackets.
- The warning icon next to each row filters the list to every keybind that shares a key with that row. Hovering over it shows "Show Conflicts". It is grayed out when the row has no conflicts.
- The reset icon puts the keybind back on its default key. It is grayed out when the keybind is already at its default.

### Search, sort, and filter

The toolbar at the top of the Key Binds menu has two rows.

- **Search by name.** Type part of a keybind's name or its category to narrow the list. Case doesn't matter. Separate terms with `|` to show keybinds that match any of them, as in `Inventory Plus | Hotbar Pockets`.
- **Search by key chord.** Click the button, hold a chord, and release. The list shows every bound keybind that shares a key with that chord. Right-click the button to clear it. Escape, Delete, or Backspace while it's waiting for keys also clears it.
- **Sort.** Category (the default), Alphabetical, or Key Chord. Alphabetical and Key Chord show one list without category headings.
- **Filter.** None (the default), Conflicts, or Not Bound.
- Changing the search, sort, or filter scrolls the list back to the top.
- The menu opens with Category sort and no filter each time.

### Fold categories

- With Category sort, each category heading has an arrow. Click a heading to fold its keybinds away, and click it again to unfold them.
- The Collapse All button folds every category in view. When they're all folded, it changes to Expand All.
- Folded categories stay folded until you close the game.
- Keybinds that vanilla would put under Miscellaneous are grouped by the mod they come from instead, under a heading made from the mod's ID.

### Use vanilla's controls screen instead

Keybindery's settings have a Controls Screen tab with one setting.

| Setting | Default | What it does |
|---|---|---|
| Disable controls-screen replacement | Off | When on, the game uses vanilla's Key Binds screen instead of Keybindery's. Close and reopen the screen for it to take effect. |

### Simultaneous Mode

Normally a chord fires while all of its keys are held, however long ago each one went down. With Simultaneous Mode on, the first presses of all its keys also have to land within a set window, so holding Ctrl for a while and then tapping K no longer counts as `Ctrl + K`.

Open Keybindery's settings from Mod Menu. Simultaneous Mode is on the Chord Detection tab.

| Setting | Default | Range | What it does |
|---|---|---|---|
| Simultaneous Mode | Off | On or off | Requires a chord's keys to go down together. |
| Simultaneous Window (ms) | 100 | 50 to 500, in steps of 10 | The most time allowed between the first and last key of a chord. Larger is more forgiving, smaller is stricter. It can only be changed while Simultaneous Mode is on. |

To switch the mode while playing, bind the Simultaneous Mode key.

| Key | Category | Default | What it does |
|---|---|---|---|
| Simultaneous Mode | Keybindery | Unbound | Turns Simultaneous Mode on or off and shows the new state above your hotbar. |

### Find a mod's keybinds from its settings

Keybindery matches keybinds to a mod by the mod's ID in the keybind's name, which is the usual convention for Fabric mods. A mod with no keybinds gets neither the tab nor the button.

- **YACL config screens** get a Keybinds tab at the end of their tabs, with one row per keybind. Click a row, then hold and release a chord to bind it. Escape cancels and keeps the old key, Delete or Backspace clears it, and right-clicking the row also clears it. The warning icon opens the Key Binds menu filtered to that keybind's conflicts, and the reset icon puts it back on its default. Changes apply when you save the config screen, like any other YACL setting.
- The last row of the tab, Keybind Menu, has an Open button that opens the full Key Binds menu with no filter.
- **Other config screens** opened from Mod Menu get a Keybinds button in the top-right corner. It opens the Key Binds menu with the search box filled in with that mod's category, so you can see the filter and clear it.

## For mod developers

Other mods can use Keybindery to put chord keybind buttons in their own settings screens and to create key bindings while the game runs.

### What the API provides

- Read and set the chord on any `KeyMapping`.
- Add a keybind row to a YACL config with one call. It adds a label, then a row with the chord button, the warning icon for conflicts, and the reset icon.
- Open the Key Binds menu filtered to one mod or several, and check whether a mod has any keybinds.
- Check whether every key of a mapping's chord is held right now, with `isHeld`. It works while a screen is open, unlike `KeyMapping.isDown()`.
- Register key bindings while the game runs, one for each thing the player makes, like a sandbox. These aren't in the Key Binds menu, and the mod shows and saves them itself.
- Get a vanilla button that captures a chord, for any screen or list.
- For MenuKit screens, `ChordButton` is a MenuKit element with the chord button, the warning icon, and the reset icon in one row. It lives in the Keybindery jar rather than the API jar, so a mod that uses it requires Keybindery.
- Without Keybindery installed, every API call does nothing safely, so a mod can support it without requiring it.

### Example

```java
ConfigCategory keybindsCategory = ConfigCategory.createBuilder()
        .name(Component.literal("Controls"))
        .options(KeybinderyAPI.getInstance().createYACLChordOption(
                myKey, Component.literal("My action"), OptionDescription.EMPTY))
        .build();
```

### Getting started

The public interface and the `Chord` class are in a separate jar, `keybindery-api`. Compile against it. Keybindery bundles `keybindery-api` inside its own jar, so players install Keybindery and nothing else. A mod that uses `ChordButton` depends on Keybindery itself.

### Read or set a chord

```java
KeybinderyAPI api = KeybinderyAPI.getInstance();

Chord chord = api.getChord(myKey);     // current chord, or Chord.UNBOUND
api.setChord(myKey, newChord);          // overwrite
```

`setChord` saves the game's options right away, so the chord survives a restart.

### Open the Key Binds menu for a mod

```java
KeybinderyAPI api = KeybinderyAPI.getInstance();
if (api.hasKeybinds("mymod")) {
    api.openModKeybinds("mymod", currentScreen);                 // one mod
    api.openModKeybinds(List.of("mymod", "myaddon"), currentScreen); // several at once
}
```

The menu opens over `currentScreen` with the search box already filled in, so the player can see the filter and clear it.

### Register key bindings at runtime

For a key binding that belongs to something the player makes while playing, like a sandbox or a lock group, register it with `bindings()` instead of creating a `KeyMapping`.

```java
RuntimeBindings bindings = KeybinderyAPI.getInstance().bindings();
Identifier id = Identifier.fromNamespaceAndPath("mymod", "sandbox/nether_test");

bindings.register(id, Component.literal("Sandbox: Nether test"), chord, () -> openSandbox());
List<BindingConflict> clashes = bindings.conflicts(chord, id); // everything else sharing a key
bindings.unregister(id);
```

Registering again with the same id replaces the binding. A binding fires on the same presses vanilla passes to key mappings, follows the player's chord mode (including Simultaneous Mode), and runs on the client thread. The label is what shows when the binding comes up as a conflict. `conflicts` checks the player's key mappings by the same rule as the Key Binds menu, plus every other runtime binding. Runtime bindings aren't saved, so store the chord with `Chord.serialize()` and register it again at startup.

### A chord button for your own screen

`captureWidget` returns a vanilla button that captures a chord.

```java
AbstractWidget button = KeybinderyAPI.getInstance().captureWidget(x, y, 150, 20, chord, newChord -> {
    saveChord(newChord.serialize());
    bindings.register(id, label, newChord, onPress);
});
if (button != null) addRenderableWidget(button);
```

The player clicks it, presses a chord, and lets go, and `onChange` gets the chord. Escape or Delete while capturing clears it, and so does a right-click. The button keeps showing the latest chord, and the mod stores it.

### Without Keybindery installed

`keybindery-api` has no runtime dependency on Keybindery. If a player doesn't have Keybindery, every call goes to a stub. Chords read as `UNBOUND` and writes do nothing. `createYACLChordOption` returns one disabled label that reads "\<label\>: (Keybindery not installed)". `hasKeybinds` and `isHeld` return false, and `openModKeybinds` does nothing. Runtime bindings register nothing, `conflicts` returns an empty list, and `captureWidget` returns `null`, so leave the button out when it does. The mod's own screens still render.

## License

MIT. See [LICENSE](https://github.com/trevorschoeny/keybindery/blob/main/LICENSE).

## Credits

Inspired by [Amecs](https://modrinth.com/mod/amecs) for multi-key bindings and [Controlling](https://modrinth.com/mod/controlling) for controls-screen search.
