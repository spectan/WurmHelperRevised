# Merged mods

These client mods are built into WurmHelper. Each folder holds one mod's original source, unchanged except where noted below,
together with its license (or permission note) and `SOURCE.txt`, which records the upstream repository and the exact commit included.

| Module (`id`) | Original mod | Author | License | Console commands | What it does |
|---|---|---|---|---|---|
| UI Scale (`uiscale`) | [WurmUIScale](https://github.com/liv-on/WurmUIScale) | liv-on | MIT | `uifade` | Scales the whole interface for high-resolution screens |
| Archeology Grouping (`archeogroup`) | [archeogroup](https://github.com/bdew-wurm/archeogroup) | bdew | LGPL-3.0 | - | Groups archaeology fragments in the inventory |
| Improved Compass (`compass`) | [compass](https://github.com/bdew-wurm/compass) | bdew | LGPL-3.0 | - | Improved compass on the HUD |
| WurmEspRevisited (`esp`) | [WurmEspRevisited](https://github.com/encode32/WurmEspRevisited) | encode32 | MIT | `esp` | Highlights players, creatures, items and tiles |
| EZBulk (`ezbulk`) | [wurm-EZBulk](https://github.com/RoselyndsShadow/wurm-EZBulk) | RoselyndsShadow | used with permission | `ezbulk`, `ezbulk_reload`, `ezbulk_debug`, `ezbulk_stop`, `ezbulk_cancel` | Fast bulk container transfers without the quantity dialog (CTRL/SHIFT while dragging) |
| Fish Buddy (`fishbuddy`) | [fishbuddy](https://github.com/bdew-wurm/fishbuddy) | bdew | LGPL-3.0 | - | Fishing helper window |
| FreecamMod (`freecam`) | [FreecamMod](https://github.com/Snidor/FreecamMod) | Snidor | used with permission | `togglefreecam` | Free camera |
| WUClientImprovedImprove (`improvedimprove`) | [WUClientImprovedImprove](https://github.com/munsta0/WUClientImprovedImprove) | munsta0 | used with permission | - | Improve actions use the right tool from the toolbelt |
| Live HUD Map (`livemap`) | [LiveHudMap](https://github.com/ago1024/LiveHudMap) | ago1024 | used with permission | `toggle livemap` | Live map window on the HUD |
| Skill Gain Tracker (`skilltrack`) | [skilltrack](https://github.com/bdew-wurm/skilltrack) | bdew | LGPL-3.0 | `toggle_gain_tracker` | Skill gain tracker window |
| Time Lock (`timelock`) | [timelock](https://github.com/bdew-wurm/timelock) | bdew | LGPL-3.0 | `timelock` | Locks the time of day shown by the client |
| Max Toolbelt (`toolbelt`) | [toolbelt](https://github.com/bdew-wurm/toolbelt) | bdew | LGPL-3.0 | - | Unlocks all toolbelt slots |
| Better Tooltips (`tooltips`) | [tooltips](https://github.com/bdew-wurm/tooltips) | bdew | LGPL-3.0 | - | Extra information in tooltips |
| Third Person (`thirdperson`) | [thources-third-person-mod](https://github.com/Thource/thources-third-person-mod) | Thource | MIT | `tp` | Third person camera with zoom and a side offset |
| Render Tweaks (`rendertweaks`) | [WurmRenderTweaks](https://github.com/TysonCodes/WurmRenderTweaks) | TysonCodes | MIT | `toggleGrass`, `toggleTrees`, `weather` | Hide grass and trees, open the weather controls |

## Licenses

- **MIT** (`uiscale`, `esp`, `thirdperson`, `rendertweaks`): see the `LICENSE` file in the module folder.
- **LGPL-3.0** (`archeogroup`, `compass`, `fishbuddy`, `skilltrack`, `timelock`, `toolbelt`, `tooltips`): the module source stays under the
  GNU Lesser General Public License v3; see `LICENSE-LGPL-3.0.txt` in each folder. The source is available in this repository.
- **Used with permission** (`ezbulk`, `freecam`, `improvedimprove`, `livemap`): these repositories have no license. They are included with
  the permission of their authors; all rights remain with them (see `PERMISSION.txt`).

The release zip carries all of these files under `WurmHelper/licenses/`.

## Changes made when merging

- `esp`: reads `mods/WurmHelper/esp.properties` instead of `mods/wurmesp.properties` (both at startup and on `esp reload`). Changing a setting with the `esp` console command saves the file, so settings persist across sessions.
- `esp` (update fixes): every creature and ground item is tracked (a concurrent map by id, no longer an unsynchronized list) and what is outlined is decided each frame, so `esp search`, the toggles and `esp reload` also reach things that were already there. A unit reads its hover name and colours as they are now (a creature that turns raging or champion changes colour; reloaded colours apply at once). `esp search h` no longer also matches model names (and `m` hover names). The hooks catch their own errors so a problem in ESP cannot break the client's own `initialize`, and only creatures (not boats and other mobile models) are tracked.
- `ezbulk`: reads `mods/WurmHelper/ezbulk.properties` instead of `mods/ezbulk.properties`.
- `uiscale`: its log and remembered `uifade` setting go to `mods/WurmHelper/` (the folder of the jar it is in) instead of `mods/uiscale/`.
- `thirdperson`: also says its messages in the in-game console (the original printed to the client log only). Commands: `tp toggle` (or `on` / `off`), `tp zoom-in`, `tp zoom-out`, `tp set-zoom <n>`, `tp get-zoom`, `tp set-xoffset <n>`, `tp get-xoffset`. Off until you turn it on; bind `tp toggle` to a key.
- `rendertweaks`: the render methods the 2016 original hooked (`DecorationRenderer.renderGrass`, `WorldRender.renderCells`) no longer exist in the client, so grass is hidden at `DecorationChunk.render` and trees at `SurfaceCell.renderTrees`. The commands match the first word exactly (the original swallowed anything that started with `weather`, `toggleGrass` or `toggleTrees`) and say what they did in the console. Grass and trees come back on when the client restarts.
- Test code (`uiscaletest`, LiveHudMap's unit tests) and build files are not included.

## Updating a module

Replace the module's `java/` (and `resources/`) folder with the new upstream source, re-apply the changes listed above,
and update the commit in `SOURCE.txt`. `default-config/` holds the settings file shipped for each module that has one.
