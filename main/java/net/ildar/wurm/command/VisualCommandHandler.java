package net.ildar.wurm.command;

import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;

public class VisualCommandHandler {
    public static void register(CommandRegistry registry) {
        registry.register("hidemount", new SimpleHandler("",
                "Toggle hiding of mounted creature/vehicle, for easier terraforming (especially underwater)") {
            @Override
            public void handle(String[] args) {
                WurmHelper.hideMount = !WurmHelper.hideMount;
                Utils.consolePrint("Mount is now %s", WurmHelper.hideMount ? "hidden" : "visible");
            }
        });

        registry.register("hidestructures", new SimpleHandler("",
                "Toggle hiding of structures such as walls, fences, and bridges") {
            @Override
            public void handle(String[] args) {
                WurmHelper.hideStructures = !WurmHelper.hideStructures;
                Utils.consolePrint("Structures are now %s", WurmHelper.hideStructures ? "hidden" : "visible");
            }
        });

        registry.register("showcoords", new SimpleHandler("",
                "Toggle display of coordinates in tile/border/corner tooltips") {
            @Override
            public void handle(String[] args) {
                WurmHelper.showTileCoords = !WurmHelper.showTileCoords;
                Utils.consolePrint("Tile coordinates are now %s", WurmHelper.showTileCoords ? "visible" : "hidden");
            }
        });
    }

    private static abstract class SimpleHandler implements ConsoleCommandHandler {
        private final String usage;
        private final String description;

        SimpleHandler(String usage, String description) {
            this.usage = usage;
            this.description = description;
        }

        @Override
        public String getUsage() {
            return usage;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }
}
