package net.ildar.wurm.bot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class NestedFillTest {

    private static final class N implements NestedFill.Node {
        final long id;
        final String name;
        final String display;
        final List<NestedFill.Node> kids = new ArrayList<>();
        boolean rare;

        N(long id, String name, String display) {
            this.id = id;
            this.name = name;
            this.display = display;
        }

        N add(N child) {
            kids.add(child);
            return this;
        }

        @Override public long id() { return id; }
        @Override public String baseName() { return name; }
        @Override public String displayName() { return display; }
        @Override public List<NestedFill.Node> children() { return kids; }
        @Override public boolean rare() { return rare; }
    }

    private static long next = 1000;

    private static N item(String name) {
        return new N(next++, name, name);
    }

    private static N bulk(String name, int qty) {
        return new N(next++, name, name + " (" + qty + "x)");
    }

    private static N pan(N... contents) {
        N p = new N(next++, "frying pan", "frying pan");
        for (N c : contents) p.add(c);
        return p;
    }

    private static N backpack(N... contents) {
        N b = new N(next++, "backpack", "backpack");
        for (N c : contents) b.add(c);
        return b;
    }

    private static N host(String name, N... contents) {
        N h = new N(next++, name, name);
        for (N c : contents) h.add(c);
        return h;
    }

    private static final List<String> HOSTS = Arrays.asList("oven", "forge");

    @Test
    public void findsEveryPanInEveryBackpackOfEveryOvenAndForge() {
        N p1 = pan(), p2 = pan(), p3 = pan(), p4 = pan();
        N oven = host("oven", backpack(p1, p2), backpack(p3));
        N forge = host("forge", backpack(p4));
        N chest = host("large chest", backpack(pan()));   // not an oven or forge

        List<NestedFill.Node> found = NestedFill.findContainers(Arrays.<NestedFill.Node>asList(oven, forge, chest), HOSTS, "frying pan");

        assertEquals(Arrays.<NestedFill.Node>asList(p1, p2, p3, p4), found);
    }

    @Test
    public void hostMatchIsByWordAnyCase() {
        N p = pan();
        N big = host("Large Oven", backpack(p));

        assertEquals(Collections.<NestedFill.Node>singletonList(p),
                NestedFill.findContainers(Collections.<NestedFill.Node>singletonList(big), HOSTS, "Frying Pan"));
    }

    @Test
    public void doesNotLookInsideAPanForAnotherPan() {
        N inner = pan();
        N outer = pan(inner);
        N oven = host("oven", backpack(outer));

        assertEquals(Collections.<NestedFill.Node>singletonList(outer),
                NestedFill.findContainers(Collections.<NestedFill.Node>singletonList(oven), HOSTS, "frying pan"));
    }

    @Test
    public void countsOnlyMatchingContents() {
        N p = pan(item("meat"), item("meat"), item("corn"));

        assertEquals(2, NestedFill.count(p, "meat"));
        assertEquals(1, NestedFill.count(p, "corn"));
        assertEquals(0, NestedFill.count(p, "fish"));
    }

    @Test
    public void bulkQuantityIsReadFromTheDisplayName() {
        assertEquals(120, NestedFill.bulkQuantity(bulk("meat", 120)));
        assertEquals(-1, NestedFill.bulkQuantity(item("meat")));
    }

    @Test
    public void putsOneOfEachIntoEveryPanWithOneBulkEntryPerItem() {
        N a = pan(), b = pan(), c = pan();
        N meat = bulk("meat", 100), corn = bulk("corn", 100);
        Map<String, List<NestedFill.Node>> sources = new HashMap<>();
        sources.put("meat", Collections.<NestedFill.Node>singletonList(meat));
        sources.put("corn", Collections.<NestedFill.Node>singletonList(corn));

        List<NestedFill.Move> moves = NestedFill.plan(Arrays.<NestedFill.Node>asList(a, b, c), Arrays.asList("meat", "corn"), 1, sources, (dest, name) -> false);

        assertEquals(6, moves.size());
        for (NestedFill.Move m : moves) {
            assertTrue(m.bulk);
            assertEquals(1, m.quantity);
        }
        assertEquals(a.id, moves.get(0).dest);
        assertEquals("meat", moves.get(0).itemName);
        assertEquals("corn", moves.get(1).itemName);
        assertEquals(c.id, moves.get(5).dest);
    }

    @Test
    public void skipsWhatThePanAlreadyHoldsAndTopsUpToN() {
        N full = pan(item("meat"), item("meat"));
        N half = pan(item("meat"));
        N empty = pan();
        N meat = bulk("meat", 100);
        Map<String, List<NestedFill.Node>> sources = Collections.singletonMap("meat", Collections.<NestedFill.Node>singletonList(meat));

        List<NestedFill.Move> moves = NestedFill.plan(Arrays.<NestedFill.Node>asList(full, half, empty), Collections.singletonList("meat"), 2, sources, (d, n) -> false);

        assertEquals(2, moves.size());
        assertEquals(half.id, moves.get(0).dest);
        assertEquals(1, moves.get(0).quantity);
        assertEquals(empty.id, moves.get(1).dest);
        assertEquals(2, moves.get(1).quantity);
    }

    @Test
    public void stopsWhenTheBinRunsOutAndGivesTheLastPanWhatIsLeft() {
        N a = pan(), b = pan(), c = pan();
        N meat = bulk("meat", 5);
        Map<String, List<NestedFill.Node>> sources = Collections.singletonMap("meat", Collections.<NestedFill.Node>singletonList(meat));

        List<NestedFill.Move> moves = NestedFill.plan(Arrays.<NestedFill.Node>asList(a, b, c), Collections.singletonList("meat"), 2, sources, (d, n) -> false);

        assertEquals(3, moves.size());
        assertEquals(2, moves.get(0).quantity);
        assertEquals(2, moves.get(1).quantity);
        assertEquals("the last pan gets the one that is left", 1, moves.get(2).quantity);
    }

    @Test
    public void singleItemsMoveTogetherAndAreNotUsedTwice() {
        N a = pan(), b = pan();
        N m1 = item("meat"), m2 = item("meat"), m3 = item("meat");
        Map<String, List<NestedFill.Node>> sources = Collections.singletonMap("meat", Arrays.<NestedFill.Node>asList(m1, m2, m3));

        List<NestedFill.Move> moves = NestedFill.plan(Arrays.<NestedFill.Node>asList(a, b), Collections.singletonList("meat"), 2, sources, (d, n) -> false);

        assertEquals(2, moves.size());
        assertEquals(Arrays.asList(m1.id, m2.id), moves.get(0).ids);
        assertEquals("only one is left for the second pan", Collections.singletonList(m3.id), moves.get(1).ids);
    }

    @Test
    public void leavesOutPansThatWereJustSentTo() {
        N a = pan(), b = pan();
        N meat = bulk("meat", 100);
        Map<String, List<NestedFill.Node>> sources = Collections.singletonMap("meat", Collections.<NestedFill.Node>singletonList(meat));

        List<NestedFill.Move> moves = NestedFill.plan(Arrays.<NestedFill.Node>asList(a, b), Collections.singletonList("meat"), 1, sources,
                (dest, name) -> dest == a.id);

        assertEquals(1, moves.size());
        assertEquals(b.id, moves.get(0).dest);
    }

    @Test
    public void noSourceItemsMeansNoMoves() {
        N a = pan();

        List<NestedFill.Move> moves = NestedFill.plan(Collections.<NestedFill.Node>singletonList(a), Collections.singletonList("meat"), 1,
                Collections.<String, List<NestedFill.Node>>emptyMap(), (d, n) -> false);

        assertTrue(moves.isEmpty());
    }

    // ---- collecting the end result back ----

    @Test
    public void collectsTheNamedItemsOutOfEveryContainerOneMoveEach() {
        N m1 = item("meal"), m2 = item("meal"), c1 = item("corn");
        N a = pan(m1, c1), b = pan(m2), empty = pan(item("corn"));

        List<NestedFill.Move> moves = NestedFill.planCollect(Arrays.<NestedFill.Node>asList(a, b, empty),
                Collections.singletonList("meal"), 77L, (id, name) -> false);

        assertEquals(2, moves.size());
        assertEquals(77L, moves.get(0).dest);
        assertEquals(Collections.singletonList(m1.id), moves.get(0).ids);
        assertEquals(Collections.singletonList(m2.id), moves.get(1).ids);
        assertEquals("the corn stays where it is", false, moves.get(0).ids.contains(c1.id));
    }

    @Test
    public void collectTakesSeveralOfAKindAndSeveralKinds() {
        N m1 = item("meal"), m2 = item("meal"), s1 = item("stew");
        N a = pan(m1, m2, s1);

        List<NestedFill.Move> moves = NestedFill.planCollect(Collections.<NestedFill.Node>singletonList(a),
                Arrays.asList("meal", "stew"), 5L, (id, name) -> false);

        assertEquals(2, moves.size());
        assertEquals(Arrays.asList(m1.id, m2.id), moves.get(0).ids);
        assertEquals(Collections.singletonList(s1.id), moves.get(1).ids);
    }

    @Test
    public void collectLeavesRareItemsAndRecentlyCollectedContainers() {
        N plain = item("meal"), rareMeal = item("meal");
        rareMeal.rare = true;
        N a = pan(plain, rareMeal);
        N b = pan(item("meal"));

        List<NestedFill.Move> moves = NestedFill.planCollect(Arrays.<NestedFill.Node>asList(a, b),
                Collections.singletonList("meal"), 5L, (id, name) -> id == b.id);

        assertEquals(1, moves.size());
        assertEquals(Collections.singletonList(plain.id), moves.get(0).ids);
    }

    @Test
    public void collectWithNothingToTakeIsNoMoves() {
        assertTrue(NestedFill.planCollect(Collections.<NestedFill.Node>singletonList(pan(item("corn"))),
                Collections.singletonList("meal"), 5L, (id, name) -> false).isEmpty());
    }
}
