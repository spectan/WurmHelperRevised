package org.tmarchuk.wurmunlimited.client.rendertweaks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class ClientRenderTweaksModTest {
    @Test
    public void commandIsTheFirstWordInLowerCase() {
        assertEquals("weather", ClientRenderTweaksMod.commandOf("weather"));
        assertEquals("togglegrass", ClientRenderTweaksMod.commandOf("  ToggleGrass now "));
        assertEquals("toggletrees", ClientRenderTweaksMod.commandOf("toggleTrees"));
    }

    @Test
    public void otherCommandsThatMerelyStartWithTheSameLettersAreNotTheCommand() {
        assertNotEquals("weather", ClientRenderTweaksMod.commandOf("weatherman"));
        assertNotEquals("togglegrass", ClientRenderTweaksMod.commandOf("toggleGrassland"));
    }

    @Test
    public void emptyInputIsNoCommand() {
        assertEquals("", ClientRenderTweaksMod.commandOf(null));
        assertEquals("", ClientRenderTweaksMod.commandOf("   "));
    }
}
