package com.rique.ruinedcollections.util;

import java.util.Locale;

public final class Longs {
    private Longs() {
    }

    public static long addClamped(long current, long delta) {
        if (delta <= 0) {
            return current;
        }
        long next = current + delta;
        if (next < 0 || next < current) {
            return Long.MAX_VALUE;
        }
        return next;
    }

    public static long multiplyClamped(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    public static Long parsePositive(String input) {
        try {
            long value = Long.parseLong(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String format(long value) {
        return String.format(Locale.US, "%,d", value);
    }
}
