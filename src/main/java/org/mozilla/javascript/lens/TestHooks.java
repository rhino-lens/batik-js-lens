package org.mozilla.javascript.lens;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lets tests force failures at fixed points, to prove that a failing observation step is flagged
 * and the script still completes. Nothing is ever armed outside tests.
 */
final class TestHooks {

    enum Point { POSITION, WRAP_OBJECT, WRAP_ARRAY }

    private static volatile Set<Point> armed = EnumSet.noneOf(Point.class);

    private TestHooks() {
    }

    static void arm(Point point) {
        Set<Point> next = EnumSet.noneOf(Point.class);
        next.addAll(armed);
        next.add(point);
        armed = next;
    }

    static void disarmAll() {
        armed = EnumSet.noneOf(Point.class);
    }

    static void maybeFail(Point point) {
        if (!armed.isEmpty() && armed.contains(point)) {
            throw new IllegalStateException("forced failure at " + point + " (test)");
        }
    }
}
