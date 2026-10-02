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

## Licenses

- **MIT** (`uiscale`, `esp`): see the `LICENSE` file in the module folder.
- **LGPL-3.0** (`archeogroup`, `compass`, `fishbuddy`, `skilltrack`, `timelock`, `toolbelt`, `tooltips`): the module source stays under the
  GNU Lesser General Public License v3; see `LICENSE-LGPL-3.0.txt` in each folder. The source is available in this repository.
- **Used with permission** (`ezbulk`, `freecam`, `improvedimprove`, `livemap`): these repositories have no license. They are included with
  the permission of their authors; all rights remain with them (see `PERMISSION.txt`).

The release zip carries all of these files under `WurmHelper/licenses/`.

## Changes made when merging

- `esp`: the `esp reload` command reads `mods/WurmHelper/wurmesp.properties` instead of `mods/wurmesp.properties`.
- `ezbulk`: reads `mods/WurmHelper/ezbulk.properties` instead of `mods/ezbulk.properties`.
- `uiscale`: its log and remembered `uifade` setting go to `mods/WurmHelper/` (the folder of the jar it is in) instead of `mods/uiscale/`.
- Test code (`uiscaletest`, LiveHudMap's unit tests) and build files are not included.

## Updating a module

Replace the module's `java/` (and `resources/`) folder with the new upstream source, re-apply the changes listed above,
and update the commit in `SOURCE.txt`. `default-config/` holds the settings file shipped for each module that has one.
