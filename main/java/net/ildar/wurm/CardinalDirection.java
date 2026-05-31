package net.ildar.wurm;

public enum CardinalDirection {
    unknown(0),
    north(0),
    n(0),
    northeast(45),
    ne(45),
    east(90),
    e(90),
    southeast(135),
    se(135),
    south(180),
    s(180),
    southwest(225),
    sw(225),
    west(270),
    w(270),
    northwest(315),
    nw(315);

    public final int angle;

    CardinalDirection(int angle) {
        this.angle = angle;
    }

    public static CardinalDirection getByName(String name) {
        try {
            return Enum.valueOf(CardinalDirection.class, name);
        } catch (Exception e) {
            return CardinalDirection.unknown;
        }
    }

    public static String getNamesList() {
        StringBuilder directions = new StringBuilder();
        for (CardinalDirection direction : CardinalDirection.values())
            directions.append(direction.name()).append("|");
        directions.deleteCharAt(directions.length() - 1);
        return directions.toString();
    }
}
