package net.ildar.wurm.bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;

/**
 * The planning half of the Multi Item Mover's nested mode: which containers to fill (every container named
 * e.g. "frying pan", at any depth, inside every oven or forge) and which moves put N of each item into each one.
 * Pure: it works on {@link Node}s, so it is tested without the game.
 */
final class NestedFill {
    private NestedFill() {}

    /** What the planner needs from an item: the game's InventoryMetaItem is adapted to it. */
    interface Node {
        long id();

        String baseName();

        /** The name as the list shows it: a bulk entry carries its count, "meat (120x)". */
        String displayName();

        List<Node> children();

        /** A rare (or better) item: the movers leave those alone. */
        default boolean rare() {
            return false;
        }
    }

    /** One transfer: N of an item from the source into one container. */
    static final class Move {
        final long dest;
        final String itemName;
        /** A bulk entry (its count is in its name): moved with the quantity question. */
        final boolean bulk;
        final int quantity;
        /** The bulk entry (bulk moves only); for a collect move, the container taken from. */
        final long sourceId;
        /** The single items to move together (non-bulk moves only). */
        final List<Long> ids;

        private Move(long dest, String itemName, boolean bulk, int quantity, long sourceId, List<Long> ids) {
            this.dest = dest;
            this.itemName = itemName;
            this.bulk = bulk;
            this.quantity = quantity;
            this.sourceId = sourceId;
            this.ids = ids;
        }
    }

    /** The count in a bulk entry's name, "meat (120x)" -> 120; -1 when it has none (a single item). */
    static int bulkQuantity(Node n) {
        String shown = n.displayName();
        if (shown == null) {
            return -1;
        }
        Matcher m = BulkItemGetterBot.quantityRegex.matcher(shown);
        if (!m.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean nameHas(Node n, String lowerWord) {
        String base = n.baseName();
        return base != null && base.toLowerCase(Locale.ROOT).contains(lowerWord);
    }

    /** True when the container's name holds one of the host words ("oven", "forge"). */
    static boolean isHost(Node root, List<String> hosts) {
        for (String h : hosts) {
            if (nameHas(root, h.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every container named {@code inner} at any depth inside the hosts among {@code roots}, in tree order.
     * A match is not searched for further matches inside it.
     */
    static List<Node> findContainers(List<Node> roots, List<String> hosts, String inner) {
        List<Node> out = new ArrayList<>();
        String word = inner.toLowerCase(Locale.ROOT);
        for (Node root : roots) {
            if (isHost(root, hosts)) {
                collect(root.children(), word, out);
            }
        }
        return out;
    }

    private static void collect(List<Node> nodes, String word, List<Node> out) {
        if (nodes == null) {
            return;
        }
        for (Node n : nodes) {
            if (nameHas(n, word)) {
                out.add(n);
            } else {
                collect(n.children(), word, out);
            }
        }
    }

    /** How many items named {@code name} the container holds (a stacked entry counts its stack). */
    static int count(Node container, String name) {
        List<Node> kids = container.children();
        if (kids == null) {
            return 0;
        }
        String word = name.toLowerCase(Locale.ROOT);
        int n = 0;
        for (Node k : kids) {
            if (nameHas(k, word)) {
                int q = bulkQuantity(k);
                n += q > 0 ? q : 1;
            }
        }
        return n;
    }

    /** True when the container holds an item named like one of the names. */
    static boolean holdsAny(Node container, List<String> names) {
        List<Node> kids = container.children();
        if (kids == null) {
            return false;
        }
        for (String name : names) {
            String word = name.toLowerCase(Locale.ROOT);
            for (Node k : kids) {
                if (nameHas(k, word)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Of these ids, the ones still inside the container (what a planned collect can still take). */
    static List<Long> stillInside(Node container, List<Long> ids) {
        List<Long> out = new ArrayList<>();
        List<Node> kids = container.children();
        if (kids == null) {
            return out;
        }
        Set<Long> present = new HashSet<>();
        for (Node k : kids) {
            present.add(k.id());
        }
        for (Long id : ids) {
            if (present.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * The moves that bring every container up to {@code perContainer} of each named item, taking from
     * {@code sources} (item name -> the source entries named like it) until they run out. {@code skip} leaves out
     * a (container id, item name) that was just sent to and has not shown up yet.
     */
    static List<Move> plan(List<Node> containers, List<String> itemNames, int perContainer,
                           Map<String, List<Node>> sources, BiPredicate<Long, String> skip) {
        return plan(containers, itemNames, perContainer, sources, skip, c -> false);
    }

    /** As above, but a container that {@code blocked} accepts is not filled at all. */
    static List<Move> plan(List<Node> containers, List<String> itemNames, int perContainer,
                           Map<String, List<Node>> sources, BiPredicate<Long, String> skip,
                           Predicate<Node> blocked) {
        List<Move> moves = new ArrayList<>();
        Map<Long, Integer> bulkLeft = new HashMap<>();
        Set<Long> used = new HashSet<>();
        for (Node c : containers) {
            if (blocked.test(c)) {
                continue;
            }
            for (String name : itemNames) {
                if (skip.test(c.id(), name)) {
                    continue;
                }
                int need = perContainer - count(c, name);
                List<Node> pool = sources.get(name);
                if (need <= 0 || pool == null) {
                    continue;
                }
                List<Long> singles = new ArrayList<>();
                for (Node s : pool) {
                    if (need <= 0) {
                        break;
                    }
                    int q = bulkQuantity(s);
                    if (q > 0) {
                        int left = bulkLeft.containsKey(s.id()) ? bulkLeft.get(s.id()) : q;
                        if (left <= 0) {
                            continue;
                        }
                        int take = Math.min(need, left);
                        bulkLeft.put(s.id(), left - take);
                        moves.add(new Move(c.id(), name, true, take, s.id(), null));
                        need -= take;
                    } else if (used.add(s.id())) {
                        singles.add(s.id());
                        need--;
                    }
                }
                if (!singles.isEmpty()) {
                    moves.add(new Move(c.id(), name, false, singles.size(), -1, singles));
                }
            }
        }
        return moves;
    }

    /**
     * The moves that take the named items out of every container into {@code destId} (your inventory or a target
     * container): one move per container and item name, rare items left alone. {@code skip} leaves out a
     * (container id, item name) that was just collected from and has not updated yet.
     */
    static List<Move> planCollect(List<Node> containers, List<String> itemNames, long destId,
                                  BiPredicate<Long, String> skip) {
        List<Move> moves = new ArrayList<>();
        for (Node c : containers) {
            List<Node> kids = c.children();
            if (kids == null) {
                continue;
            }
            for (String name : itemNames) {
                if (skip.test(c.id(), name)) {
                    continue;
                }
                String word = name.toLowerCase(Locale.ROOT);
                List<Long> ids = new ArrayList<>();
                for (Node k : kids) {
                    if (nameHas(k, word) && !k.rare()) {
                        ids.add(k.id());
                    }
                }
                if (!ids.isEmpty()) {
                    moves.add(new Move(destId, name, false, ids.size(), c.id(), ids));
                }
            }
        }
        return moves;
    }
}
