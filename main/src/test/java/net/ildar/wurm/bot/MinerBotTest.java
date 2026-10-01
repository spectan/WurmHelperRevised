package net.ildar.wurm.bot;

import org.junit.Test;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MinerBotTest {
    @Test
    public void snapshotEntriesRetriesConcurrentModification() {
        FlakyEntryMap<Integer, String> map = new FlakyEntryMap<>(1,
                new AbstractMap.SimpleEntry<>(1, "one"),
                new AbstractMap.SimpleEntry<>(2, "two"));

        List<Map.Entry<Integer, String>> entries = MinerBot.snapshotEntries(map, 2);

        assertEquals(2, entries.size());
    }

    @Test
    public void snapshotEntriesReturnsEmptyAfterRepeatedConcurrentModification() {
        FlakyEntryMap<Integer, String> map = new FlakyEntryMap<>(3,
                new AbstractMap.SimpleEntry<>(1, "one"));

        List<Map.Entry<Integer, String>> entries = MinerBot.snapshotEntries(map, 2);

        assertTrue(entries.isEmpty());
    }

    @Test
    public void shouldTakeShardWhenItFitsInFreeSpace() {
        assertTrue(MinerBot.shouldTakeShard(25f, 20f));
    }

    @Test
    public void shouldNotTakeShardWhenItDoesNotFit() {
        assertFalse(MinerBot.shouldTakeShard(20f, 20f));
        assertFalse(MinerBot.shouldTakeShard(15f, 20f));
    }

    private static class FlakyEntryMap<K, V> extends AbstractMap<K, V> {
        private int failuresRemaining;
        private final Set<Map.Entry<K, V>> entries;

        @SafeVarargs
        private FlakyEntryMap(int failuresRemaining, Map.Entry<K, V>... entries) {
            this.failuresRemaining = failuresRemaining;
            this.entries = new HashSet<>(Arrays.asList(entries));
        }

        @Override
        public Set<Map.Entry<K, V>> entrySet() {
            return new AbstractSetWithFlakyIterator();
        }

        private class AbstractSetWithFlakyIterator extends AbstractSet<Map.Entry<K, V>> {
            @Override
            public int size() {
                return entries.size();
            }

            @Override
            public Iterator<Map.Entry<K, V>> iterator() {
                if (failuresRemaining-- > 0)
                    throw new ConcurrentModificationException();
                return entries.iterator();
            }
        }
    }
}
