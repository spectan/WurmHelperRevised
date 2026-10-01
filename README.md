# WurmHelper

**Attention!!**

I personally used this mod only on servers where client modes were not prohibited. 
Obey the server rules and **_don't use this mod if autoclickers, macros or another automation tools were banned on the server_**

**Usage:**

Type `info` in the game console to see the list of available commands, and `info bots`
(or `bot list`) for the list of bots and which ones are running.

Bots are controlled with `bot <bot> <key> [arguments]`:

  * `<bot>` is the bot's abbreviation or its full name, e.g. `bot ch` or `bot chopper`, `bot tc` or `bot tree cutter`
  * `bot <bot>` shows what the bot does and lists all of its keys with their arguments
  * `bot <bot> on` starts the bot, `bot <bot> off` stops it, `bot <bot> pause` pauses/resumes it
  * `bot <bot> status` shows the bot's current settings
  * `bot <bot> info <key>` describes a key
  * keys can be typed by their short name or full name: `bot ch s 0.9` is the same as `bot ch stamina 0.9`
  * bots can be configured before they are started: `bot m c 3` on a stopped miner remembers the setting
    for when you run `bot m on`. You can also configure and start in one go: `bot m on c 3`
  * stamina thresholds are compared with stamina + damage, so they take a value between 0 and 2,
    or a percentage: `bot ch s 90` is the same as `bot ch s 0.9`
  * names can be several words, and lists are comma separated: `bot cig a small barrel, large crate`
  * `bot list` shows all bots and their state, `bot pause` pauses/resumes all running bots, `bot off` stops them

See [COMMANDS.md](COMMANDS.md) for every bot and key.

**Keybinds:**

If you bind console commands to keys, these work well:

  * `bot pause` and `bot off` to pause or stop everything quickly
  * keys that pick the container or item under the mouse (for example the item mover's target key) -
    hover the container and press the key
  * toggles like `bot a w` (assistant drinking)

With `OnscreenFeedback` on (the default), state changes show on screen too, so you can use keybinds with
the console closed. Set `PrefillConsoleInput=false` if you don't want commands to rewrite the console input line.

**Options:**

Set these in `mods/WurmHelper.properties`:

| Option | Default | Effect |
|---|---|---|
| `NoBlessings` | `false` | Hide the periodic "Ildar blesses you!" message |
| `ConsoleMsgColor` | `0.5,1.0,1.0` | Colour (r,g,b from 0 to 1) of the mod's console messages |
| `OnscreenFeedback` | `true` | Also show bot state changes on screen |
| `AlarmOnStop` | `false` | Play a sound when a bot stops by itself (error, missing tool...) |
| `PrefillConsoleInput` | `true` | Put the last bot command back into the console input line |

A bot that stops by itself always shows an on-screen message.

**Build:**

This is a Wurm Unlimited client mod for Ago's mod loader. The project is a
Maven multi-module build and is compiled for Java 8.

Requirements:

  1) JDK 8
  2) Maven
  3) Wurm Unlimited client/mod loader jars available in one directory

By default Maven looks for the Wurm jars in:

`C:\Program Files (x86)\Steam\steamapps\common\Wurm Unlimited\WurmLauncher`

If your Wurm Unlimited install is somewhere else, pass the location with
`-Dclient.location`.

Build the release zip with:

`mvn install`

Or, with a custom Wurm jar directory:

`mvn install -Dclient.location="C:\path\to\WurmLauncher"`

The build output is `WurmHelper.zip` in the repository root.

**What mod can do:**

  1) Automatic crafting
  2) Automatic mining and smelting
  3) Automatic forestry
  4) Automatic foraging and botanizing
  5) Some tools for automatic inventory items management
  6) Automatic item improving
  7) Another various things, making the gameplay easier
  
  You can join the discord channel with this link - https://discord.gg/GpqWrtF
  There you can send us your suggestions, found bugs and errors. Or just express your support with a couple of warm words
