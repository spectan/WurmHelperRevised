package net.ildar.wurm.command;

import net.ildar.wurm.CardinalDirection;
import net.ildar.wurm.Utils;

public class MovementCommandHandler {

    public static void register(CommandRegistry registry) {
        registry.register("move", new SimpleHandler("(float)distance",
                "Moves your character in current direction for specified distance (in meters, 1 tile has 4 meters on each side).") {
            @Override
            public void handle(String[] args) {
                if (args != null && args.length == 1) {
                    try {
                        Utils.movePlayer(Float.parseFloat(args[0]));
                    } catch (NumberFormatException e) {
                        printUsage("move");
                    }
                } else {
                    printUsage("move");
                }
            }
        });

        registry.register("look", new SimpleHandler("{north|east|south|west}",
                "Precisely turns player in chosen direction.") {
            @Override
            public void handle(String[] args) {
                if (args != null && args.length == 1) {
                    CardinalDirection direction = CardinalDirection.getByName(args[0]);
                    if (direction == CardinalDirection.unknown) {
                        Utils.consolePrint("Unknown direction: " + args[0]);
                        return;
                    }
                    try {
                        Utils.turnPlayer(direction.angle, 0);
                    } catch (Exception e) {
                        Utils.consolePrint("Can't change looking direction");
                    }
                } else {
                    Utils.consolePrint("Usage: look {" + CardinalDirection.getNamesList() + "}");
                }
            }
        });

        registry.register("stabilize", new SimpleHandler("",
                "Moves your character to the very center of the tile + turns the sight towards nearest cardinal direction.") {
            @Override
            public void handle(String[] args) {
                Utils.stabilizePlayer();
            }
        });

        registry.register("mtcenter", new SimpleHandler("",
                "Moves your character to the center of the tile") {
            @Override
            public void handle(String[] args) {
                Utils.moveToCenter();
            }
        });

        registry.register("mtcorner", new SimpleHandler("",
                "Moves your character to the nearest tile corner") {
            @Override
            public void handle(String[] args) {
                Utils.moveToNearestCorner();
            }
        });

        registry.register("stabilizelook", new SimpleHandler("",
                "Turns the sight towards nearest cardinal direction") {
            @Override
            public void handle(String[] args) {
                Utils.stabilizeLook();
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

        protected void printUsage(String name) {
            Utils.consolePrint("Usage: " + name + " " + usage);
        }
    }
}
