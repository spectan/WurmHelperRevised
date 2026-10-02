UI Scale for Wurm Unlimited 1.0.0
=================================

A client mod that makes the whole Wurm Unlimited interface larger - windows,
icons, buttons, menus and text - while the world keeps rendering at your
full resolution. Text stays sharp at any size. It can also fade the chat
and event windows when nothing is happening in them, the target window
when nothing is targeted, and the fight window when you are not fighting.

Client-side only: servers never see it, and it works on any server.

Updates, source and bug reports: https://github.com/liv-on/WurmUIScale


REQUIREMENTS
------------
* Wurm Unlimited from Steam, on Windows.
* Ago's client mod loader:
  https://github.com/ago1024/WurmClientModLauncher/releases
  (the same loader almost every Wurm Unlimited client mod uses).


INSTALLING
----------
1. Close Wurm Unlimited.
2. Open the game folder: in Steam, right-click Wurm Unlimited > Manage >
   Browse local files, then open the WurmLauncher folder.
3. If the mod loader is not installed yet: unzip it into WurmLauncher and
   run its patcher.bat once.
4. Unzip this mod into WurmLauncher too. You should now have
     WurmLauncher\mods\uiscale.properties
     WurmLauncher\mods\uiscale\uiscale.jar
5. Open mods\uiscale.properties in Notepad and set the scale (see below).
6. Start the game from Steam as usual.

Then, in the game:
* Set the resolution back to your screen's native resolution, if you had
  lowered it to make the interface readable.
* Set the font sizes back down (launcher settings - the gear on the server
  list - Text tab). Fonts are enlarged along with everything else now.


SETTINGS
--------
All in mods\uiscale.properties. Restart the game after changing them.

  scale      How much larger the interface is drawn, 1.0 to 4.0.
               1.3   1920x1080 lays out like 1477x831
               1.5   1920x1080 lays out like 1280x720
               2     3840x2160 lays out like 1920x1080   (4K)
  fade       How far idle chat and event windows fade, 0.05 to 1.
             0.3 is 30% opacity; 1 means never fade.
  fadeAfter  Seconds without activity before they fade.
  targetFade How far the target window fades while nothing is targeted,
             0 to 1. 0 (the default) hides it completely; 1 means never
             fade.
  combatFade How far the fight window fades while you are not fighting,
             0 to 1. 1 means never fade.


FADING
------
The chat window and the event window fade when idle: after fadeAfter
seconds without activity they ease down to the fade opacity, and come back
as soon as

  * a new line arrives in any of their tabs,
  * you type in the chat input, or
  * the mouse is over them.

The target window fades to the targetFade opacity whenever nothing is
targeted, and comes back as soon as you target something. At 0 it is gone
completely: clicks pass through where it was. Above 0 it also comes back
while the mouse is over it.

The fight window (stances and attack directions) fades to the combatFade
opacity whenever you are not fighting, and comes back as soon as a fight
starts or you move the mouse over it.

Every other window is left alone.

To switch fading on or off in the game, open the console (F1) and type:

  uifade            toggle
  uifade on         fade when idle
  uifade off        always fully visible
  uifade status     show the current setting

Your choice is remembered (in mods\uiscale\uiscale-state.properties). The
commands also work in PlayerFiles\configs\default\autorun.txt.


UNINSTALLING
------------
Close the game and delete mods\uiscale.properties and the mods\uiscale
folder. To switch it off without deleting it, set scale=1.0.


GOOD TO KNOW
------------
* Window positions start fresh. The game saves them per interface size
  (PlayerFiles\players\<name>\windows_WIDTHxHEIGHT.txt), and the interface
  size changes with the scale.
* Icons and other pictures are enlarged from their original resolution, so
  they are slightly soft. Text is redrawn at full resolution and stays sharp.
* The launcher window (server list, settings) is a separate program and is
  not scaled.
* If Steam verifies the game files, it puts the original client.jar back and
  the mod loader stops loading. Run the loader's patcher.bat again.


IF SOMETHING GOES WRONG
-----------------------
* The mod writes mods\uiscale\uiscale.log on every launch, listing every
  part of the game it adjusted. The loader's WurmLauncher\client.log gets
  one summary line: "uiscale: all 38 patches applied".
* A line with "NOT FOUND", "NOT PATCHED" or "FAILED" means that part was
  left unchanged - usually because another mod changes the same code in a
  way that clashes. Please include both logs when reporting a problem.


FOR MOD AUTHORS AND THE CURIOUS
-------------------------------
How it hooks in: the loader defines every game class from its javassist
class pool. In its init phase, before the game starts, the mod takes each of
the 15 classes it targets from the pool - including changes the loader and
other mods have made so far - rewrites a fixed set of call sites in those
bytes with the ASM copy inside the game's Java 8 runtime, and puts the class
back in the pool. Mods that change the class later build on the patched
version. Every rewrite replaces one call with a static call taking the same
values, or passes a value through a helper, so stack maps are unchanged.

What it changes: the interface is told the window is (real size / scale)
and lays itself out in that smaller space. Window size and mouse position
going into the interface are converted from real to scaled; viewport and
clip rectangles going out to OpenGL from scaled to real. The 3D world keeps
real coordinates, so clicking in the world is unaffected.

Sharp text: each font's glyph atlas is rasterised at the real pixel size,
strings are drawn through a 1/scale transform snapped to the pixel grid, and
text metrics are reported in interface units. The loading screen gets the
same scaled space.

Fading: the chat and event windows are recognised by title when they are
created, the target and fight windows by their class the first time they
draw; while one of them draws, a faded copy of each thing it queues is
drawn in place of the original, so nothing the game keeps is ever modified.
A hidden target window is also left out of the interface's check for what
is under the mouse, so clicks go through it.

The full list of call sites is in src\uiscale\Patcher.java.

Building: build.cmd needs a JDK 9 or newer on PATH and this source folder
next to the game's WurmLauncher and runtime folders, with the mod loader
installed. It builds uiscale.jar and runs two tests:
  SelfTest    every rule fires on the real game classes and every patched
              class passes ASM's bytecode verifier;
  LoaderTest  runs Ago's real mod loader over a staged mods folder and checks
              that every rule fires there, that other mods' changes to the
              same classes survive, and that every class as the loader
              defines it passes the verifier.
deploy.cmd copies the build into the game; package.ps1 makes the release zips.


LICENSE
-------
MIT - see LICENSE.txt.
