# WurmHelper

**Attention!!**

I personally used this mod only on servers where client modes were not prohibited. 
Obey the server rules and **_don't use this mod if autoclickers, macros or another automation tools were banned on the server_**

**Usage:**

type "info" in game console to see the list of available commands

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
