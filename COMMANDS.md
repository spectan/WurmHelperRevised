# Commands

Generated from the sources by `tools/gen_commands.py` - don't edit by hand.

Use `bot <bot> <key> [arguments]`. A bot is named by its abbreviation or full name, and a key by its
short name or full name (`bot ch s 0.9` = `bot chopper stamina 0.9`). Keys can be set before the bot
is turned on. See the README for the general usage.

## Keys every bot has

| Key | Name | Description |
|---|---|---|
| `info <key>` | Info | Get information about configuration key |
| `off` | Off | Deactivate the bot |
| `pause` | Pause | Pause/resume the bot |
| `status` | Status | Show the bot's current settings |
| `t <milliseconds>` | Timeout | Set the timeout for bot. The bot will wait for specified time(in milliseconds) after each iteration/update |
| `on` | On | Start the bot (`on <key> [arguments]` sets a key first) |

## Bots

| Bot | Abbreviation | Description |
|---|---|---|
| [Archeo](#archeo) | `ac` | Investigates tiles and identifies fragments |
| [Archer](#archer) | `ar` | Automatically shoots at selected target with currently equipped bow. When the string breaks tries to place a new one. Deactivates on target death. |
| [Assistant](#assistant) | `a` | Assists player in various ways |
| [Bulk Item Getter](#bulk-item-getter) | `big` | Automatically transfers items to player's inventory from configured bulk storages. The n-th  source item will be transferred to the n-th target item |
| [Chopper](#chopper) | `ch` | Automatically chops felled trees near player |
| [Container Item Getter](#container-item-getter) | `cig` | Retrieves items from containers |
| [Crafter](#crafter) | `c` | Automatically does crafting operations using items from crafting window. New crafting operations are not starting until an action queue becomes empty. This behaviour can be disabled.  |
| [Digger](#digger) | `d` | Does the dirty job |
| [Farmer](#farmer) | `f` | Tends the fields, plants the seeds, cultivates the ground, collects harvests |
| [Flower Planter](#flower-planter) | `fp` | Skills up player's gardening skill by planting and picking flowers in surrounding area |
| [Forage Stuff Mover](#forage-stuff-mover) | `fsm` | Moves foragable and botanizable items from your inventory to the target inventories. Optionally you can toggle the moving of rocks or rare items on and off. |
| [Forager](#forager) | `fg` | Can forage, botanize, collect grass and flowers in an area surrounding player. Bot can be configured to process rectangular area of any size. Picked items, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. Containers only in root directory of player's inventory will be taken into account. Bot can be configured to drop picked items on the floor.  |
| [Forester](#forester) | `fr` | A forester bot. Can pick and plant sprouts, cut trees/bushes and gather the harvest in 3x3 area around player. Bot can be configured to process rectangular area of any size. Sprouts, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. Containers only in root directory of player's inventory will be taken into account. New item names can be added(harvested fruits for example) to be moved to containers too. Steppe and moss tiles will be cultivated if planting is enabled and player have shovel in his inventory.  |
| [Ground Item Getter](#ground-item-getter) | `gig` | Collects items from the ground around player. |
| [Healing](#healing) | `h` | Heals the player's wounds with cotton found in inventory |
| [Improver](#improver) | `i` | Improves selected items in provided inventories. Tools searched from player's inventory. Items like water or stone searched before each improve, actual instruments searched one time before improve of the first item that must be improved with this tool. Tool for improving is determined by improve icon that you see on the right side of item row in inventory. For example improve icons for stone chisel and carving knife are equal, and sometimes bot can choose wrong tool. Use "ci" key to change the chosen instrument. |
| [Item Mover](#item-mover) | `im` | Moves items from your inventory to the target destination. |
| [Meditation](#meditation) | `md` | Meditates on the carpet. Assumes that there are no restrictions on meditation skill. |
| [Miner](#miner) | `m` | Mines rocks and smelts ores. |
| [Multi Item Mover](#multi-item-mover) | `mim` | Moves many sets of items to their own containers |
| [Pathing](#pathing) | `pt` | Bot that can perform pathfinding to accomplish its various tasks |
| [Pile Collector](#pile-collector) | `pc` | Collects piles of items to bulk containers. Default name for target items is "dirt" |
| [Prospector](#prospector) | `pr` | Prospects selected tile |
| [RMI](#rmi) | `rmi` | Remotely control other clients |
| [Seller](#seller) | `s` | Sells items to tokens |
| [Tree Cutter](#tree-cutter) | `tc` | Cuts trees |

## Archeo

`bot ac` - Investigates tiles and identifies fragments

| Key | Name | Description |
|---|---|---|
| `area [<tiles ahead> <tiles to the right>]` | Area Mode | Start the area processing mode for an area of the given size, starting from the bottom left corner where the player stands and facing forward. While it is running, the same size (or no arguments) stops it and a different size resizes it |
| `area_speed <tiles per second>` | Area Speed | Set the moving speed for area mode in tiles per second (0.01 to 100). Default value is 1 tile per second. |
| `at` | Add Target | Add the inventory under the mouse cursor to identify fragments in |
| `co` | Fragment Combining | Toggle fragment combining |
| `ct` | Clear Targets | Clear inventories to identify fragments in |
| `id` | Identifying | Toggle identifying |
| `iv` | Investigating | Toggle investigating |
| `sh` | Shovel | Toggle investigating with shovel instead of trowel |

## Archer

`bot ar` - Automatically shoots at selected target with currently equipped bow. When the string breaks tries to place a new one. Deactivates on target death.

| Key | Name | Description |
|---|---|---|
| `s <threshold>` | Stamina | Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold |
| `string` | String | String the equipped bow with a bow string from the inventory |

## Assistant

`bot a` - Assists player in various ways

| Key | Name | Description |
|---|---|---|
| `b` | Butchering | Toggle butchering of corpses on the ground |
| `bu` | Burying | Toggle burying of corpses on the ground |
| `bua` | Bury All | Toggle burying corpses with the "Bury all" action (default) vs the normal bury action |
| `bub <keyword>` | Add Corpse Blacklist | Add keywords (comma separated) to the corpse blacklist. Corpses with names containing them are not buried. "rift" is in the blacklist by default |
| `bubc` | Clear Corpse Blacklist | Clear the corpse blacklist |
| `bud <milliseconds>` | Bury Delay | Set the delay before burying corpses (to allow other bots time to move items). Default is 2500 |
| `c [<spell>]` | Casting | Toggle automatic casts of spells (if the player has enough favor). Needs a statuette in the inventory. With a spell abbreviation the spell is chosen and casting is turned on (or kept on). The default spell is Dispel. You can see the list of available spells with the "ls" key |
| `cleanup` | Trash Cleaning | Toggle automatic cleaning of the selected trash heap. The timeout between cleanings can be configured separately |
| `cleanupid <id>` | Trash By ID | Turn on automatic cleaning of the trash bin with the provided id (changes the trash bin if cleaning is already on) |
| `cleanupt <milliseconds>` | Trash Timeout | Change the timeout between trash cleanings. Default is 5000 |
| `cwov` | Wisdom of Vynora | Toggle automatic casts of Wisdom of Vynora spell. Turns other spellcasts off |
| `eat` | Eating | Toggle automatic eating of the food the user is pointing at |
| `groom` | Grooming | Toggle grooming of creatures |
| `kb` | Kindling Burning | Toggle automatic burning of kindlings in player's inventory. AssistantBot.class.getSimpleName() will combine the kindlings and burn them using the selected forge. The timeout of burns can be configured separately |
| `kbid <id>` | Kindling By Forge ID | Turn on automatic kindling burns at the forge with the provided id (changes the forge if burning is already on) |
| `kbt <milliseconds>` | Kindling Timeout | Change the timeout between kindling burns. Default is 10000 |
| `l` | Lockpicking | Toggle automatic lockpicking. The target chest should be beneath the user's mouse |
| `lid <id>` | Lockpick By ID | Turn on automatic lockpicking of the target chest with the provided id (changes the chest if lockpicking is already on) |
| `ls` | List Spells | Show the list of available spells for autocasting |
| `lt <milliseconds>` | Lockpick Timeout | Change the timeout between lockpickings. Default is 610000 |
| `lumpcombine` | Lump Combining | Toggle automatic combining of (hot) lumps |
| `lumpheating` | Lump Heating | Toggle automatic lump heating by swapping lumps into/out of the hovered container |
| `notarget` | No Target | Toggle automatic clearing of the targeted creature if it is too far away |
| `p` | Praying | Toggle automatic praying at the selected altar. The timeout between prayers can be configured separately. |
| `pave <item name>` | Pave | Pave the hovered tile with an unused item of the given name from the inventory |
| `pavec <item name>` | Pave Corner | Pave the hovered tile corner with an unused item of the given name from the inventory |
| `paveclear` | Clear Paving | Forget which items have already been used for paving |
| `pc <count>` | Prayer Count | Set the number of prayers to be queued. If 0 (default) then as many as the action queue allows |
| `pid <id>` | Pray By Altar ID | Turn on automatic praying at the altar with the provided id (changes the altar if praying is already on) |
| `pis` | Pray On Selected Item | Turn on automatic praying on the selected inventory item (e.g. prayer beads). Select the item in your inventory first. |
| `ps <stamina>` | Prayer Stamina | Set the minimal stamina (0 to 1) needed for praying. Default is 0.5 |
| `pt <milliseconds>` | Prayer Timeout | Change the timeout between prayers. Default is 1500000 (25 minutes) |
| `s` | Sacrificing | Toggle automatic sacrificing at the selected altar. The timeout between sacrifices can be configured separately. |
| `sid <id>` | Sacrifice By ID | Turn on automatic sacrifices at the altar with the provided id (changes the altar if sacrificing is already on) |
| `st <milliseconds>` | Sacrifice Timeout | Change the timeout between sacrifices. Default is 1230000 (20.5 minutes) |
| `v` | Verbose | Toggle verbose mode. In verbose mode the AssistantBot.class.getSimpleName() will output additional info to the console |
| `w` | Drinking | Toggle automatic drinking of the liquid the user is pointing at |
| `wid <id>` | Drink By ID | Turn on automatic drinking of the liquid with the provided id (changes the target if drinking is already on) |

## Bulk Item Getter

`bot big` - Automatically transfers items to player's inventory from configured bulk storages. The n-th  source item will be transferred to the n-th target item

| Key | Name | Description |
|---|---|---|
| `c <quantity>` | Stock Quantity | Set quantity of source items to keep stocked in target, 0 to move as many as possible |
| `isc <index>` | Select Set | Choose an item spec to operate on |
| `isd` | Delete Set | Delete currently chosen item spec |
| `isl` | List Sets | List item specs |
| `isn` | New Set | Create a new item spec |
| `ss` | Set Source | Set the source item for chosen spec (in bulk storage) to what the user is currently pointing to |
| `ssxy` | Source XY | Find source item(s) for chosen spec from a fixed point at current cursor position |
| `st` | Set Target | Set the target item for chosen spec to what the user is currently pointing to |

## Chopper

`bot ch` - Automatically chops felled trees near player

| Key | Name | Description |
|---|---|---|
| `area [<tiles ahead> <tiles to the right>]` | Area Mode | Start the area processing mode for an area of the given size, starting from the bottom left corner where the player stands and facing forward. While it is running, the same size (or no arguments) stops it and a different size resizes it |
| `area_speed <tiles per second>` | Area Speed | Set the moving speed for area mode in tiles per second (0.01 to 100). Default value is 1 tile per second. |
| `c <clicks>` | Clicks | Set the amount of chops the bot will do each time |
| `d <distance>` | Distance | Set the distance (in meters) the bot should look around player in search for a felled tree |
| `s <threshold>` | Stamina | Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold |
| `tool` | Tool | Set the chopping tool from the selected inventory item |

## Container Item Getter

`bot cig` - Retrieves items from containers

| Key | Name | Description |
|---|---|---|
| `a <item name>` | Add Item | Add item names to be pulled. Separate several names with commas |
| `c` | Clear Items | Clear list of items to pull |
| `cs` | Clear Sources | Clear list of source containers |
| `ss` | Add Source | Add source container to pull from |
| `v` | Verbose | Toggle verbose messages |

## Crafter

`bot c` - Automatically does crafting operations using items from crafting window. New crafting operations are not starting until an action queue becomes empty. This behaviour can be disabled. 

| Key | Name | Description |
|---|---|---|
| `an <clicks>` | Clicks | Set an action number. The number of crafting operations the player will do on each click on continue/create button |
| `cs` | Combine Sources | Combine source items(on the left side of crafting window) |
| `ct` | Combine Targets | Combine target items(on the right side of crafting window) |
| `ctimeout <milliseconds>` | Combine Timeout | Set the timeout for item combining in milliseconds |
| `noan` | Toggle Action Check | Toggles the check for action queue state before the start of each crafting operation. By default CrafterBot.class.getSimpleName() will check action queue and start crafting operations only when it is empty |
| `nosort` | Toggle Sorting | Sorting of source and target items by weight is disabled by default. This key toggles sorting on and off |
| `r` | Repair | Toggle the source item repairing (on the left side of crafting window). Usually it is an instrument. When the source item gets 10% damage player will repair it automatically |
| `s <threshold>` | Stamina | Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold |
| `s1s` | Single Source | Toggles the setting of single item to source slot of crafting window |
| `ss <item name>` | Set Source | Set the source item name. CrafterBot.class.getSimpleName() will place item with provided name from your inventory to the source slot(on the left side of crafting window). The name may contain spaces |
| `ssid <item id>` | Source By ID | Set an item with provided id to the source slot(on the left side of crafting window) |
| `ssxy` | Source XY | Set the source item fixed point. CrafterBot.class.getSimpleName() will place item from that fixed point of screen to the source item slot(on the left side of crafting window) |
| `st <item name>` | Set Target | Set the target item name. CrafterBot.class.getSimpleName() will place item with provided name from your inventory to the target slot(on the right side of crafting window). The name may contain spaces |
| `stxy` | Target XY | Set the target item fixed point. CrafterBot.class.getSimpleName() will place item from that fixed point of screen to the target item slot(on the right side of crafting window) |
| `u` | Unfinished Mode | Toggle the special mode in which CrafterBot.class.getSimpleName() will place an item to the target item slot which is at the top of "Needed items" list |

## Digger

`bot d` - Does the dirty job

| Key | Name | Description |
|---|---|---|
| `area [<tiles ahead> <tiles to the right>]` | Area Mode | Start the area processing mode for an area of the given size, starting from the bottom left corner where the player stands and facing forward. While it is running, the same size (or no arguments) stops it and a different size resizes it |
| `area_speed <tiles per second>` | Area Speed | Set the moving speed for area mode in tiles per second (0.01 to 100). Default value is 1 tile per second. |
| `c <clicks>` | Clicks | Set the amount of actions the bot will do each time |
| `d [<height>]` | Dig | Dig until the specified height (in slopes) is reached. With a height it sets the height and turns digging on; without one it turns digging off |
| `dtile [<height>]` | Tile Digging | Dig all 4 corners of the current tile until the specified height (in slopes) is reached. With a height it turns tile digging on; without one it turns it off |
| `dtp` | Dig To Pile | Toggle the use of "Dig to pile" action instead of "Dig" |
| `l` | Levelling | Toggle the levelling of selected tile |
| `la [<height>]` | Area Levelling | Level the area around player to the specified height (in slopes). With a height it turns area levelling on; without one it turns it off |
| `s <threshold>` | Stamina | Set the stamina threshold (0 to 1). Player will not do any actions if his stamina is lower than specified threshold |
| `sm` | Surface Mining | Toggle the surface mining. The bot will do the same but with the pickaxe on the rock |
| `tool` | Tool | Set the digging tool from selected inventory item |
| `tr` | Repair | Toggle the repairing of the tool |

## Farmer

`bot f` - Tends the fields, plants the seeds, cultivates the ground, collects harvests

| Key | Name | Description |
|---|---|---|
| `and <item name>` | Add Drop Item | Add item names to drop on the ground. Separate several names with commas |
| `area [<tiles ahead> <tiles to the right>]` | Area Mode | Start the area processing mode for an area of the given size, starting from the bottom left corner where the player stands and facing forward. While it is running, the same size (or no arguments) stops it and a different size resizes it |
| `area_speed <tiles per second>` | Area Speed | Set the moving speed for area mode in tiles per second (0.01 to 100). Default value is 1 tile per second. |
| `c` | Cultivation | Toggle the dirt cultivation |
| `d` | Dropping | Toggle the dropping of harvested items. Add item names to drop by "and" key |
| `dl <count>` | Drop Limit | Set the drop limit, configured number of harvests won't be dropped |
| `ft` | Farm Tending | Toggle the farm tending |
| `h` | Harvest Mode | Toggle the harvesting |
| `p [seeds name]` | Planting | Turn the planting on with the name of the seeds to plant (or change the seeds), or turn it off without a name |
| `r` | Repair | Toggle the tool repairing |
| `s <threshold>` | Stamina | Set the stamina threshold (a value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold |

## Flower Planter

`bot fp` - Skills up player's gardening skill by planting and picking flowers in surrounding area

| Key | Name | Description |
|---|---|---|
| `s <threshold>` | Stamina | Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold |

## Forage Stuff Mover

`bot fsm` - Moves foragable and botanizable items from your inventory to the target inventories. Optionally you can toggle the moving of rocks or rare items on and off.

| Key | Name | Description |
|---|---|---|
| `at` | Set Target | Set the container under the mouse as the target. Foragable and botanizable items will be moved to it. Using it again switches to the new container |
| `mr` | Toggle Rocks | Toggle moving of rocks |
| `r` | Toggle Rares | Toggle moving of rare items |

## Forager

`bot fg` - Can forage, botanize, collect grass and flowers in an area surrounding player. Bot can be configured to process rectangular area of any size. Picked items, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. Containers only in root directory of player's inventory will be taken into account. Bot can be configured to drop picked items on the floor. 

| Key | Name | Description |
|---|---|---|
| `area [<tiles ahead> <tiles to the right>]` | Area Mode | Start the area processing mode for an area of the given size, starting from the bottom left corner where the player stands and facing forward. While it is running, the same size (or no arguments) stops it and a different size resizes it |
| `area_speed <tiles per second>` | Area Speed | Set the moving speed for area mode in tiles per second (0.01 to 100). Default value is 1 tile per second. |
| `b` | Botanizing | Toggle the botanizing |
| `bt <type>` | Botanize Type | Set the botanizing type. Use the "btl" key to see the available types |
| `btl` | Botanize Types | Show the list of botanizing types |
| `d` | Drop | Toggle the dropping of collected items to the ground |
| `dfa <item name>[, <item name>...]` | Add Item | Add item name(s) to the drop filter, separated by commas. Items whose name contains a filter name won't be dropped |
| `dfc` | Clear Items | Clear the drop filter |
| `dwf` | Drop When Full | Change drop mode between drop when full inventory or drop after every action |
| `f` | Forage | Toggle the foraging |
| `ft <type>` | Forage Type | Set the foraging type. Use the "ftl" key to see the available types |
| `ftl` | Forage Types | Show the list of foraging types |
| `g` | Grass | Toggle the grass gathering |
| `na <clicks>` | Clicks | Set the number of actions bot will do each time |
| `s <threshold>` | Stamina | Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold |
| `scn <container name>` | Container Name | Set the name of the containers to put sprouts/harvest in (may contain spaces) |
| `v` | Verbose | Toggle the verbose mode. Additional information will be shown in console during the work of the bot in verbose mode |

## Forester

`bot fr` - A forester bot. Can pick and plant sprouts, cut trees/bushes and gather the harvest in 3x3 area around player. Bot can be configured to process rectangular area of any size. Sprouts, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. Containers only in root directory of player's inventory will be taken into account. New item names can be added(harvested fruits for example) to be moved to containers too. Steppe and moss tiles will be cultivated if planting is enabled and player have shovel in his inventory. 

| Key | Name | Description |
|---|---|---|
| `aim <item name>[, <item name>...]` | Add Item | Add item name(s), separated by commas, to move into the containers along with sprouts |
| `area [<tiles ahead> <tiles to the right>]` | Area Mode | Start the area processing mode for an area of the given size, starting from the bottom left corner where the player stands and facing forward. While it is running, the same size (or no arguments) stops it and a different size resizes it |
| `area_speed <tiles per second>` | Area Speed | Set the moving speed for area mode in tiles per second (0.01 to 100). Default value is 1 tile per second. |
| `asb <tree name>[, <tree name>...]` | Add Sprout Blacklist | Add tree type(s), separated by commas, to not pick sprouts from |
| `atb <tree name>[, <tree name>...]` | Add Tree Blacklist | Add blacklisted tree type(s), separated by commas. Those trees are skipped |
| `atw <tree name>[, <tree name>...]` | Add Tree Whitelist | Add whitelisted tree type(s), separated by commas. When the whitelist is not empty only those trees are processed |
| `ca` | Cut All Sprouts | Toggle the cutting of sprouts from all trees |
| `cs` | Cut Shriveled | Toggle the cutting of shriveled trees |
| `csb` | Clear Sprout Blacklist | Clear blacklisted tree types for sprout picking |
| `ctb` | Clear Tree Blacklist | Clear blacklisted tree types |
| `ctw` | Clear Tree Whitelist | Clear whitelisted tree types |
| `df` | Deforestation | Toggle the cutting of all trees (deforestation) |
| `h` | Harvest Mode | Toggle the harvesting |
| `na <clicks>` | Clicks | Set the number of actions bot will do each time |
| `p` | Planting | Toggle the planting |
| `s <threshold>` | Stamina | Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold |
| `scn <container name>` | Container Name | Set the name of the containers to put sprouts/harvest in (may contain spaces) |

## Ground Item Getter

`bot gig` - Collects items from the ground around player.

| Key | Name | Description |
|---|---|---|
| `a <item name>` | Add Item | Add item names to the search list. Separate several names with commas, e.g. "log, small rock" |
| `d <distance>` | Distance | Set the distance (in meters, 1 tile is 4 meters) the bot should look around player in search for items |

## Healing

`bot h` - Heals the player's wounds with cotton found in inventory

| Key | Name | Description |
|---|---|---|
| `md <damage>` | Min Damage | Only treat wounds with damage greater than this value (0 to 100) |

## Improver

`bot i` - Improves selected items in provided inventories. Tools searched from player's inventory. Items like water or stone searched before each improve, actual instruments searched one time before improve of the first item that must be improved with this tool. Tool for improving is determined by improve icon that you see on the right side of item row in inventory. For example improve icons for stone chisel and carving knife are equal, and sometimes bot can choose wrong tool. Use "ci" key to change the chosen instrument.

| Key | Name | Description |
|---|---|---|
| `at` | Add Target | Add the inventory under the mouse cursor. Selected items in this inventory will be improved. |
| `ci` | Change Instrument | Change previously chosen instrument by tool selected in player's inventory |
| `ct` | Clear Targets | Remove all target inventories |
| `g` | Ground | Toggle the ground mode. Set the skill first by "ss" key |
| `ls` | List Skills | List available improving skills |
| `s <threshold>` | Stamina | Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold |
| `ss <skill abbreviation>` | Set Skill | Set the skill. Only tools from that skill will be used. You can list available skills using "ls" key. Use "ToolSkill.UNKNOWN.abbreviation" to determine the skill by the material of improved item (default) |

## Item Mover

`bot im` - Moves items from your inventory to the target destination.

| Key | Name | Description |
|---|---|---|
| `a <item name>` | Add Item | Add item names to move to the targets. Separate several names with commas. The maximum weight of moved item can be configured with "sw" key |
| `clear` | Clear Items | Clear the item name list |
| `fl` | First Level Only | Toggle the moving of only first level items of your inventory. Items that match added keywords but lying inside a group or a container will not be touched. Enabled by default |
| `r` | Toggle Rares | Toggle the moving of rare items. Disabled by default. |
| `st` | Set Target | Set the target item(under mouse pointer). Items from your inventory will be moved inside this item if it is a container or next to it otherwise. |
| `stc <container name>` | Target Container | Set the target container(under mouse pointer) with another containers inside. Items from your inventory will be moved to containers with provided name. Bot will try to put 100 items inside each container. But you can change this value using "stcn" key. |
| `stcn <count>` | Container Volume | Set the number of items to put inside each container. Use with "stc" key |
| `stid <id>` | Target By ID | Set the id of target item. Items from your inventory will be moved inside this item if it is a container or next to it otherwise. |
| `str` | Target Container Root | Set the target container(under mouse pointer). Items from your inventory will be moved to the root directory of that container. |
| `sw <weight>` | Max Weight | Set the maximum weight for item to be moved, 0 for any weight. Affects the last added item name. |

## Meditation

`bot md` - Meditates on the carpet. Assumes that there are no restrictions on meditation skill.

| Key | Name | Description |
|---|---|---|
| `c <clicks>` | Clicks | Set the amount of actions the bot will do each time |
| `rt <milliseconds>` | Repair Timeout | Set how often (in milliseconds) the meditation rug is repaired |
| `s <threshold>` | Stamina | Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold |

## Miner

`bot m` - Mines rocks and smelts ores.

| Key | Name | Description |
|---|---|---|
| `area` | Area Mining | Set the 3x3 area mining mode in which bot will mine the 3x3 area around player (takes no arguments) |
| `at <min quality>` | Add Target | Add the target (under the mouse cursor) for lumps with provided minimum quality (0-100) |
| `ati <min quality>` | Add Target Inventory | Add the target inventory (under the mouse cursor) for lumps with provided minimum quality (0-100) |
| `atid <id> <min quality>` | Add Target By ID | Add the target with provided id for lumps with provided minimum quality (0-100) |
| `c <clicks>` | Clicks | Change the amount of clicks bot will do each time (1 to 10) |
| `dir <f\|u\|d>` | Direction | Set mining direction. Possible directions are: f - forward, u - upward, d - downward. Forward is default direction |
| `fixed` | Fixed Tile | Set the fixed tile mining mode. Bot will remember selected tile and mine it |
| `ft` | Front Tile | Set the mining mode in which bot will mine a tile in front of a player |
| `m` | Moving | Toggle the automatic moving forward when bot have no work |
| `o` | Ore Mining | Toggle the mining of ore tiles. Enabled by default |
| `s <threshold>` | Stamina | Set the stamina threshold (0 to 1). Player will not do any actions if his stamina is lower than specified threshold |
| `sc` | Shard Combining | Toggle the combining of shards lying around the player in piles |
| `scn <shards name>` | Shards Name | Change the name of shards to combine. See "sc" key |
| `sfn <fuel name>` | Fuel Name | Set a name for the fuel for smelting ores |
| `sft <milliseconds>` | Fuel Timeout | Set a smelter fuelling timeout (in milliseconds) for smelting ores |
| `sm` | Smelting | Toggle the smelting of ores in selected pile. Set the smelter, pile and lump targets first |
| `sp` | Set Pile | Set a pile (under the mouse cursor) for smelting ores |
| `ssm` | Set Smelter | Set a smelter (under the mouse cursor) for smelting ores |
| `st` | Selected Tile | Set the mining mode in which bot will mine currently selected tile |
| `tool` | Tool | Set the mining tool from selected inventory item |
| `v` | Verbose | Toggle the verbose mode. While verbose bot will show additional info in console |

## Multi Item Mover

`bot mim` - Moves many sets of items to their own containers

| Key | Name | Description |
|---|---|---|
| `a <item name>` | Add Item | Add item names to chosen set. Separate several names with commas |
| `clear` | Clear Items | Clear list of items in chosen set |
| `fl` | First Level Only | Toggle moving of only top-level items |
| `isc <index>` | Select Set | Choose item set to operate on |
| `isd` | Delete Set | Delete current item set |
| `isl` | List Sets | Show item sets |
| `isn` | New Set | Create new item set |
| `st` | Set Target | Set target item for chosen set |
| `str` | Target Container Root | Set target container for chosen set |

## Pathing

`bot pt` - Bot that can perform pathfinding to accomplish its various tasks

| Key | Name | Description |
|---|---|---|
| `ap` | Avoid Passives | Toggle murdering only hostile creatures, leaving passive animals alone |
| `b` | Butchering | Toggle butchering of corpses on the ground while murdering |
| `bu` | Burying | Toggle burying of corpses on the ground while murdering |
| `bua` | Bury All | Toggle burying corpses with the "Bury all" action (default) vs the normal bury action |
| `bub <keyword>[, <keyword>...]` | Add Corpse Blacklist | Add keywords (comma separated) to the corpse blacklist. Corpses with names containing them are not buried. "rift" is in the blacklist by default |
| `bubc` | Clear Corpse Blacklist | Clear the corpse blacklist |
| `bud <milliseconds>` | Bury Delay | Set the delay before burying corpses (to allow other bots time to move items). Default is 2500 |
| `follow [<player name>]` | Follow | Follow the player whose name starts with the given text. Without a name it stops following; with a name while following it switches to that player. Needs the bot running |
| `groom` | Grooming | Toggle finding and grooming nearby creatures. Needs the bot running |
| `mb <keyword>[, <keyword>...]` | Add Murder Blacklist | Add keywords (comma separated) to the murder blacklist, matched against the creature's name and hover text. Creatures matching them are never attacked, so named animals (a bred horse's name) can be excluded |
| `mbc` | Clear Murder Blacklist | Clear the murder blacklist |
| `murder` | Murder | Toggle finding and murdering nearby creatures. Needs the bot running |
| `r` | Repair | Toggle automatic repairing of equipped items while murdering. When an equipped item gets 2 damage it is repaired between kills (unrepairable items like summer hats are skipped) |
| `shear` | Shear | Toggle finding and shearing nearby sheep. Needs the bot running |
| `speed <km/h>` | Speed | Set speed at which bot will move, in km/h |
| `walkto [<x> <y>]` | Walk To | Walk to given tile coordinates, or to the hovered tile when none are given. Needs the bot running |

## Pile Collector

`bot pc` - Collects piles of items to bulk containers. Default name for target items is "dirt"

| Key | Name | Description |
|---|---|---|
| `cc [container name]` | Custom Container | Set additional container name to search for target items, or clear it when called without a name |
| `mq <quality>` | Min Quality | Set minimum quality (0 to 100) of items to be collected |
| `st [container name]` | Set Target | Set the target bulk inventory(under mouse pointer) to put items to. Provide an optional name of containers inside inventory. Default is "large crate" |
| `stcc <capacity>` | Container Capacity | Set the capacity for target container. Default value is 300 |
| `stn <item name>` | Target Name | Set the name for target items. Default name is "dirt" |

## Prospector

`bot pr` - Prospects selected tile

| Key | Name | Description |
|---|---|---|
| `c <clicks>` | Clicks | Change the amount of clicks (1 to 10) the bot will do each time |
| `s <threshold>` | Stamina | Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold |
| `tool` | Tool | Set the prospecting tool from the selected inventory item |

## RMI

`bot rmi` - Remotely control other clients

| Key | Name | Description |
|---|---|---|
| `c` | Client | Toggle client mode, allowing remote control of this character. Needs the bot running |
| `regaddr <host>:<port>` | Registry Address | Set hostname and port of RMI registry. Change it before turning on server/client mode |
| `s` | Server | Toggle server mode, allowing sending commands to other characters. Needs the bot running |
| `sl` | Server List | Server mode: list known clients |
| `slr` | Server Refresh | Server mode: refresh list of clients |
| `sr <subcommand> [<args>]` | Server Dispatch | Server mode: dispatch a command to every character. Use `sr help` to list the subcommands |

## Seller

`bot s` - Sells items to tokens

| Key | Name | Description |
|---|---|---|
| `a <item name>` | Add Item | Add item names to be sold. Separate several names with commas |
| `b <item name>` | Add Blacklist | Add blacklisted item names. Separate several names with commas |
| `ca` | Clear Items | Clear list of items to sell |
| `cb` | Clear Blacklist | Clear blacklisted item names |
| `gems` | Gems | Set up bot to sell common (non-star) gems |
| `sc <count>` | Sell Count | Set max queued sell actions |
| `st` | Set Target | Set the selected settlement token to sell to |

## Tree Cutter

`bot tc` - Cuts trees

| Key | Name | Description |
|---|---|---|
| `a <age>` | Age Limit | Set minimal tree age for chopping by name or abbreviation, e.g. "oa" or "old" (see the "al" key). Chop all trees by default |
| `al` | Age List | Show the tree ages and their abbreviations |
| `area [<tiles ahead> <tiles to the right>]` | Area Mode | Start the area processing mode for an area of the given size, starting from the bottom left corner where the player stands and facing forward. While it is running, the same size (or no arguments) stops it and a different size resizes it |
| `area_speed <tiles per second>` | Area Speed | Set the moving speed for area mode in tiles per second (0.01 to 100). Default value is 1 tile per second. |
| `b` | Bush Cutting | Toggle bush cutting. Enabled by default |
| `c <clicks>` | Clicks | Set the number of chops queued each time |
| `s <threshold>` | Stamina | Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold |
| `sp` | Sprout Cutting | Toggle sprouting trees cutting. Enabled by default |
| `tool` | Tool | Use the item selected in your inventory as the cutting tool (a hatchet is looked up on start otherwise) |
| `tt <tree types>` | Tree Type | Set tree types for chopping, e.g. "birch oak" or "birch, oak". Use "all" to chop all trees again (the default) |
