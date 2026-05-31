package net.ildar.wurm;

import net.ildar.wurm.bot.Bot;

public class BotRegistration {
    private Class botClass;
    private String description;
    private String abbreviation;

    public BotRegistration(Class botClass, String description, String abbreviation) {
        this.botClass = botClass;
        this.description = description;
        this.abbreviation = abbreviation;
    }

    public Class getBotClass() {
        return botClass;
    }

    public String getDescription() {
        return description;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public String getName() {
        return botClass.getSimpleName();
    }
}
