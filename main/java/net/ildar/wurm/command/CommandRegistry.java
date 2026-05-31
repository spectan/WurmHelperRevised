package net.ildar.wurm.command;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CommandRegistry {
    private final Map<String, ConsoleCommandHandler> handlers = new HashMap<>();
    private final Set<String> commandNames = new LinkedHashSet<>();

    public void register(String name, ConsoleCommandHandler handler) {
        String key = name.toLowerCase();
        if (handlers.containsKey(key)) {
            System.err.println("CommandRegistry warning: overwriting handler for command '" + key + "'");
        }
        handlers.put(key, handler);
        commandNames.add(key);
    }

    public ConsoleCommandHandler get(String name) {
        return handlers.get(name.toLowerCase());
    }

    public boolean has(String name) {
        return handlers.containsKey(name.toLowerCase());
    }

    public Set<String> getCommandNames() {
        return new LinkedHashSet<>(commandNames);
    }
}
