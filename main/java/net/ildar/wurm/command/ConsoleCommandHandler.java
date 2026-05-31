package net.ildar.wurm.command;

public interface ConsoleCommandHandler {
    void handle(String[] args);
    String getUsage();
    String getDescription();
}
