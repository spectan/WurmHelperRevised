package com.wurmonline.client.renderer.gui;

import net.encode.wurmesp.WurmEspMod;
import net.encode.wurmesp.util.ConfigUtils;

public class WurmEspWindow
extends WWindow {
    private WurmBorderPanel mainPanel;

    public WurmEspWindow() {
        super("Esp", false);   // closed at every start; the menu opens it
        this.setTitle("Esp");
        this.resizable = true;
        this.closeable = true;
        this.setInitialSize(100, 300, true);
        this.mainPanel = new WurmBorderPanel("Esp");
        WurmArrayPanel<FlexComponent> checkboxes = new WurmArrayPanel<FlexComponent>("Esp CheckBoxes", 0);
        EspWCheckBox playersCheckBox = new EspWCheckBox("Players", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.players = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        playersCheckBox.checked = WurmEspMod.players;
        EspWCheckBox mobsCheckBox = new EspWCheckBox("Aggro Mobs", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.mobs = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        mobsCheckBox.checked = WurmEspMod.mobs;
        EspWCheckBox animalsCheckBox = new EspWCheckBox("Animals", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.animals = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        animalsCheckBox.checked = WurmEspMod.animals;
        EspWCheckBox specialsCheckBox = new EspWCheckBox("Specials", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.specials = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        specialsCheckBox.checked = WurmEspMod.specials;
        EspWCheckBox itemsCheckBox = new EspWCheckBox("Items", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.items = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        itemsCheckBox.checked = WurmEspMod.items;
        EspWCheckBox tilesSearchCheckBox = new EspWCheckBox("Flower Tiles", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.tilesFlower = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        tilesSearchCheckBox.checked = WurmEspMod.tilesFlower;
        EspWCheckBox uniquesCheckBox = new EspWCheckBox("Uniques", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.uniques = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        uniquesCheckBox.checked = WurmEspMod.uniques;
        EspWCheckBox championsCheckBox = new EspWCheckBox("Conditioned", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.conditioned = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        championsCheckBox.checked = WurmEspMod.conditioned;
        EspWCheckBox xrayCheckBox = new EspWCheckBox("Xray", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.xray = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        xrayCheckBox.checked = WurmEspMod.xray;
        EspWCheckBox tilesCheckBox = new EspWCheckBox("Tiles", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.tilescloseby = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        tilesCheckBox.checked = WurmEspMod.tilescloseby;
        EspWCheckBox tilesWalkableCheckBox = new EspWCheckBox("Rideable Tiles", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.tilesclosebynotrideable = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        tilesWalkableCheckBox.checked = WurmEspMod.tilesclosebynotrideable;
        EspWCheckBox deedCheckBox = new EspWCheckBox("Deed", new CheckBoxListener(){

            @Override
            public void checkboxClicked(EspWCheckBox checkbox) {
                WurmEspMod.deedsize = checkbox.checked;
                ConfigUtils.saveProperties("esp");
            }
        });
        deedCheckBox.checked = WurmEspMod.deedsize;
        checkboxes.addComponent((FlexComponent)playersCheckBox);
        checkboxes.addComponent((FlexComponent)mobsCheckBox);
        checkboxes.addComponent((FlexComponent)animalsCheckBox);
        checkboxes.addComponent((FlexComponent)specialsCheckBox);
        checkboxes.addComponent((FlexComponent)itemsCheckBox);
        checkboxes.addComponent((FlexComponent)tilesSearchCheckBox);
        checkboxes.addComponent((FlexComponent)uniquesCheckBox);
        checkboxes.addComponent((FlexComponent)championsCheckBox);
        checkboxes.addComponent((FlexComponent)xrayCheckBox);
        checkboxes.addComponent((FlexComponent)tilesCheckBox);
        checkboxes.addComponent((FlexComponent)tilesWalkableCheckBox);
        checkboxes.addComponent((FlexComponent)deedCheckBox);
        this.mainPanel.setComponent((FlexComponent)checkboxes, 0);
        this.setComponent((FlexComponent)this.mainPanel);
    }

    public void closePressed() {
        hud.toggleComponent((WurmComponent)this);
    }

    /** Closed until the menu opens it (the mod adds it to the HUD to set it up). */
    public void hideOnStart(HeadsUpDisplay headsUp) {
        headsUp.hideComponent((WurmComponent)this);
    }

    public void toggle() {
        hud.toggleComponent((WurmComponent)this);
    }
}

