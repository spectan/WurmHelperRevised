# UI Scale for Wurm Unlimited

A client mod for [Ago's client mod loader](https://github.com/ago1024/WurmClientModLauncher) that makes the whole Wurm Unlimited interface larger - windows, icons, buttons, menus and text - while the world keeps rendering at your full resolution. Text stays sharp at any size. It can also fade the chat and event windows when nothing is happening in them, the target window when nothing is targeted, and the fight window when you are not fighting.

Wurm Unlimited is not DPI-aware, so on high-resolution screens the interface is tiny, and the font size setting only enlarges text. This scales everything.

Client-side only: servers never see it, and it works on any server.

## Install

1. Install [Ago's client mod loader](https://github.com/ago1024/WurmClientModLauncher/releases) if you have not already (unzip into `WurmLauncher`, run `patcher.bat`).
2. Download `uiscale-VERSION.zip` from [Releases](https://github.com/liv-on/WurmUIScale/releases) and unzip it into the game's `WurmLauncher` folder. You should have `mods\uiscale.properties` and `mods\uiscale\uiscale.jar`.
3. Set the scale in `mods\uiscale.properties`, then start the game from Steam.

Afterwards, set the game back to your native resolution and bring the font sizes down: they are enlarged along with everything else.

## Settings (`mods\uiscale.properties`)

| Setting | Default | |
| --- | --- | --- |
| `scale` | 1.5 | Interface size, 1.0 to 4.0. 1.3 lays 1920x1080 out like 1477x831; 2 lays 4K out like 1080p. |
| `fade` | 0.3 | How far idle chat and event windows fade, 0.05 to 1 (1 = never). |
| `fadeAfter` | 15 | Seconds without activity before they fade. |
| `targetFade` | 0 | How far the target window fades while nothing is targeted, 0 to 1 (0 = hidden, and clicks pass through it; 1 = never). |
| `combatFade` | 0.3 | How far the fight window (stances, attack directions) fades while you are not fighting, 0 to 1 (1 = never). |

In the game, the console (F1) command `uifade` toggles all fading; `uifade on`, `uifade off` and `uifade status` also work. The choice is remembered.

## How it works

The interface is told the window is (real size / scale) and lays itself out in that smaller space; window size and mouse position going into it are converted from real to scaled, and viewport and clip rectangles going out to OpenGL from scaled to real. The 3D world keeps real coordinates. Glyph atlases are rasterised at the real pixel size and drawn through a pixel-snapped 1/scale transform, so text stays sharp.

It hooks in during the mod loader's init phase: each of the 15 targeted game classes is taken from the loader's javassist class pool (with whatever other mods changed so far), 38 call sites are rewritten with the ASM copy inside the game's Java 8 runtime, and the class goes back into the pool. The call sites are listed in [`src/uiscale/Patcher.java`](src/uiscale/Patcher.java). Nothing on disk is modified.

## Building

`build.cmd` needs a JDK 9 or newer on PATH and this folder next to the game's `WurmLauncher` and `runtime` folders, with the mod loader installed. It builds `uiscale.jar` and runs two tests:

- `SelfTest` - every rule fires on the real game classes, and every patched class passes ASM's bytecode verifier;
- `LoaderTest` - runs Ago's real mod loader over a staged `mods` folder and checks that every rule fires there, that other mods' changes to the same classes survive, and that every class as the loader defines it passes the verifier.

`deploy.cmd` copies the build into the game; `package.ps1` makes the release zips.

## License

MIT - see [LICENSE.txt](LICENSE.txt).
