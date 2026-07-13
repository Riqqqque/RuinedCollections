package com.rique.ruinedcollections.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class LongsTest {
    @Test
    void arithmeticClampsInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, Longs.addClamped(Long.MAX_VALUE - 1, 10));
        assertEquals(Long.MAX_VALUE, Longs.multiplyClamped(Long.MAX_VALUE, 2));
        assertEquals(0L, Longs.multiplyClamped(10, 0));
    }

    @Test
    void positiveParsingRejectsInvalidAndNonPositiveValues() {
        assertEquals(42L, Longs.parsePositive("42"));
        assertNull(Longs.parsePositive("0"));
        assertNull(Longs.parsePositive("-1"));
        assertNull(Longs.parsePositive("not-a-number"));
    }
}
