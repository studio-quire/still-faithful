# Old Faithful's options mod (oldfaithful-options)

Claude Opus 5.5, 2026-11-02. Things the user asked for in Old Faithful that aren't fixes of beta's mistakes
(`docs/fixes.md` has those): options and comforts. Pure Faithful has none of them. Client only. Public id
`oldfaithful-options` (`oldfaithful_edition=fixed`, `docs/release.md` > Names); in Old Faithful's release only. Switches in `config/oldfaithful-options.properties`, one per package under `mixin/`, as
the fix mod has them.

| Switch | What |
|---|---|
| `options.fov` | A FOV slider, 30 to 110 degrees, 70 ("Normal", beta's) by default |
| `gui.normalScale` | A first start (no options.txt) has GUI Scale at Normal instead of Auto; an existing file keeps its choice |
| `options.screen` | Old Faithful... in Options: the settings screen (below) |
| `title.quitGame` | Quit Game on the title screen (user, 2026-11-02: Old Faithful only, not Pure Faithful) |
| (display mod) | The window opens maximised the first time, then where the player left it, fullscreen included |

## The settings screen

Claude Opus 5.5, 2026-11-02. So players needn't edit config files: **Options > Old Faithful...**, a 200-wide button
above Video Settings and Controls on their 24-pixel pitch, in the gap beta leaves under its options (left out if
another mod has put something there). Beta's own look throughout: its buttons, font, background and layout.

- **Layout**: the top screen has a button per group, two columns of 150-wide buttons from a sixth of the way down, Done
  at `height/6 + 168`, as beta's Video Settings. Each group is a page of the same shape, "Name: Value" buttons that step
  on a click (fixes read **Fixed / Beta**, the rest ON / OFF or their choices). Under the buttons, two lines at most:
  the hint for the button pointed at (what beta did, in a sentence), or the group's note. A page past five rows goes
  over pages with arrows beside Done (none needs it yet). Groups: Blocks and Items, Mobs and Vehicles, Redstone and
  Lighting, Looks and Sounds, Multiplayer, Window and Mouse.
- **What's there**: only what's installed. Each mod lists its switches through a small API in this mod
  (`settings/`: a `SettingsProvider` registered as the `oldfaithful-options:settings` entrypoint in its
  fabric.mod.json, so it's loaded only when this mod is present, and never on a server): `FixesSettings` (each fix's
  name, group, hint and side), `LightingSettings` (lava). This mod adds its own Quit Game, and Pure Faithful Display's
  settings when that mod is installed with our LWJGL component (`DisplaySettings`, here, so the display mod and Pure
  Faithful are unchanged). Not shown: the FOV switch (its slider is in Options) and GUI Normal on a first start (no
  first start is left once the screen can be seen); they stay in the file. A group with nothing in it isn't shown; a
  fix without a row in `FixesSettings` shows under "More" by its key, and `FixesSettingsTest` fails until it has one.
- **Saving**: a click writes the file at once, changing that one value in place and keeping every other byte (the
  explanations, other keys, order, line ends: `ConfigFile`), whole or not at all (a temporary file moved over it).
  Values are read as the owning mod reads them (`Boolean.parseBoolean`, "raw" or not), and the files are read again
  each time a screen opens, so hand edits show.
- **When it takes effect**: a fix is in the game only if its switch was on at start (its mixins are skipped
  otherwise), so every fix waits for a restart: its button gets an asterisk ("Repeaters: Beta*") while the file
  differs from what's running, with "* Takes effect after a restart" under the buttons (the top screen says "Some
  changes take effect after a restart"). A setting can be both (`applyNow` and `running`: Still Faithful's Dusk, live
  between Stepped and Smooth, after a restart to or from Beta): it's marked while what's running differs from the
  file. At once: VSync, Even Frames, Smooth Look, Mouse and Fullscreen (set where
  the display mod and our LWJGL read them; `Window.refreshSettings` for the mouse and fullscreen), and Lava Light
  (the lighting check reads it per chunk: it changes land as it loads, and light already in the world stays). After
  a restart: Remember Window, Start Maximized, Scale to Display, Quit Game Button.
- **Multiplayer**: a switch decided where the world runs (`serverDecides`: drops, mobs, redstone, saved ticks, lava
  light...) reads "Name: Server" and can't be pressed while playing on a server, with the hint "The server you're
  playing on decides this. Yours is for single player." This game's own switches stay changeable there (what's
  drawn, heard and predicted, and the client-side multiplayer fixes); wooden slabs and tool speeds are this game's
  switch but apply on a server only where it has the fix too (`ServerMarker`), as their hints say. Fixes that run
  only on a server (#5, #16a, #16b, #48) aren't on the screen; the Multiplayer page's note points to the server's
  own file.

## FOV

- **Where**: the Options screen's empty place, right of Difficulty (beta lays its five options two to a row); left
  out if another mod has put something there. Drawn as beta's sliders are, on a widget of our own (`FovSlider`):
  beta's `SliderWidget` needs one of beta's `Option`s, an enum. It reads the mouse to the pixel like the fixed sliders
  (#40), so all 81 degrees are reachable at any GUI scale.
- **Stored** in options.txt as `fov:<degrees>` (later versions' key and unit). b1.7.3 ignores lines it doesn't know,
  so the file still loads without the mod, which drops the line at its next save.
- **Applied** in `GameRenderer.getFov`: beta's view (70; 60 under water; narrowing in death) times setting / 70, as
  later versions scale it. Frustum culling reads the projection back from GL, so it follows.
- **The hand and held item** stay at beta's view whatever the setting (as in later versions): beta draws them through
  the world's projection, so before they're drawn the projection is set again at beta's own value (with the anaglyph
  offset, as renderWorld does). Screen overlays (water, fire, the pumpkin) are drawn there too and stay as beta's.

## Quit Game

Beta has the button: beside a half-width Options, shown when the game runs on its own (`Minecraft.isApplet` false).
In a launcher it ran as an applet (Mojang's launcher, and Fabric's start, which sets `isApplet` in
`MinecraftApplet.init`), so players never saw it. The mod makes only the title screen's reading of the flag false:
beta's own layout, label ("Quit Game") and action (`scheduleStop`, what the window's close button does).

## The window

In the display mod (Pure's, `docs/lwjgl.md` > The display mod): `window.startMaximized` (off by default) opens the
window maximised when neither the launcher nor a remembered placement says otherwise. This mod's fabric.mod.json asks
the display mod for `window.remember=true` and `window.startMaximized=true` as defaults (`purefaithful-display:defaults`),
so standalone installs get Old Faithful's window as the pack does; a value already in the player's
`purefaithful-display.properties` wins. A launcher's own setting (Prism's window size or maximised, or `--width`) still
decides when it's given.

## Not added

- Shift (sneak) to leave a vehicle, a later version's addition: beta's right-click on the vehicle stays the only way
  (user, 2026-11-02).
- Antialiasing (multisampling the game's window, 2026-11-03): built and checked, then removed after the user's
  play-test showed light blue (the sky) through the seams between blocks at 4x/8x. Not important to the user, so
  dropped rather than chased (the commit and its revert are in git if it's ever wanted). Since measured
  (2026-11-03, `docs/bugs.md` #70): single-sample rendering shows the same sky through the seams between render
  chunks, only more rarely; multisampling tests several points per pixel, so it finds the cracks more often, and it
  takes a pixel's texture at the pixel's centre even when only an edge of the face covers it, so a face's edge can read
  the transparent texels beside its tile in the atlas and drop out (#72 is the single-sample form of that; reasoned,
  not measured with multisampling). #70's fix closes the first; the second reaches up to half a pixel outside the
  face, beyond what #72's fix would cover, so multisampling would need its texture sampled inside the covered part
  (centroid sampling, a shader) to come back.

## Tests

- `./gw :oldfaithful-options:test`: `Fov` (every degree round-trips the slider and is reachable at Small; labels,
  parsing, proportional scaling).
- `./gw :harness:clientStartupTestFixed` with this mod (`OptionsCheck`): every launch's title screen has Quit Game
  beside Options; in the world, FOV 100 draws the world at 100/70 of the hand's view and the hand at beta's (read
  back from GL's projection), `fov:100` saved and loaded back, the slider in its place in Options. Then a first start
  (`first-start/`: no options.txt, no config, no launcher size) must open maximised with the GUI at Normal, write
  both window defaults into the display config, keep a maximised placement, and stop through Quit Game cleanly.
- `./gw :oldfaithful-options:test` also: `ConfigFileTest` (a value changed in place with every other byte kept: line
  ends, separators, indents, continuations, escaped keys, comments that mention the key, duplicates, appending; the
  file written whole) and `SettingsRegistryTest` (groups and their order, values read as the mods read them, the
  restart mark, hand edits, settings applied at once).
- `clientStartupTestFixed` with this mod (`SettingsCheck`, after the FOV): Old Faithful... in place with beta's
  buttons where they were; the groups of the installed mods; every label fitting its button with any value and the
  restart mark (in the game's own font), every hint in two lines; Repeaters pressed (the file changed in that line
  only, "Beta*") and back (the file byte for byte as before); Lava Light, VSync (the swap interval), Smooth Look and
  Mouse (SDL's hint) taking effect at once; Remember Window marked for a restart; with the world marked remote, the
  world's switches reading "Server" and refusing a press while the game's own stay live. Then Options, the top
  screen and two pages at GUI scales Small, Normal and Large in 640x480, 854x480 and 1920x1080 windows (resized
  unseen), checked for buttons off the screen, over each other or overflowing, and saved as pictures in
  `local/run/client-startup-fixed/settings-shots/` (drawn with the mouse placed, so a hint shows). Not run on a real
  server: multiplayer there is the world's `isRemote` flag, set while the screen is built.
- The user's to look at: the slider's feel, the hand at wide settings, Quit Game in fullscreen; the settings screen
  (the wording, the groups, the asterisk).
