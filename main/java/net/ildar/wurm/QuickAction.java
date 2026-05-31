package net.ildar.wurm;

import com.wurmonline.shared.constants.PlayerAction;

public enum QuickAction {
    Butcher("bu", "butchering knife", PlayerAction.BUTCHER),
    Bury("br", "shovel", PlayerAction.BURY),
    BuryInsideMine("brm", "pickaxe", PlayerAction.BURY),
    CutTree("ct", "hatchet", PlayerAction.CUT_DOWN),
    ChopLog("cl", "hatchet", PlayerAction.CHOP_UP),
    Mine("m", "pickaxe", PlayerAction.MINE_FORWARD),
    TendField("ft", "rake", PlayerAction.FARM),
    Dig("d", "shovel", PlayerAction.DIG),
    DigToPile("dp", "shovel", PlayerAction.DIG_TO_PILE),
    Lockpick("l", "lock picks", new PlayerAction("", (short) 101, PlayerAction.ANYTHING)),
    LightFire("lf", "steel and flint", new PlayerAction("", (short) 12, PlayerAction.ANYTHING)),
    LeadAnimal("la", "rope", PlayerAction.LEAD),
    Sow("s", "seeds", PlayerAction.SOW);

    public final String abbreviation;
    public final String toolName;
    public final PlayerAction playerAction;

    QuickAction(String abbreviation, String toolName, PlayerAction playerAction) {
        this.abbreviation = abbreviation;
        this.toolName = toolName;
        this.playerAction = playerAction;
    }

    public static QuickAction getByAbbreviation(String abbreviation) {
        for (QuickAction action : values())
            if (action.abbreviation.equals(abbreviation))
                return action;
        return null;
    }
}
