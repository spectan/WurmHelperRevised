package net.ildar.wurm.command;

import net.ildar.wurm.Utils;

abstract class SimpleHandler implements ConsoleCommandHandler {
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
