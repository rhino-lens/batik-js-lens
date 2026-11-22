package io.github.rhinolens.lens;

/**
 * Per-thread "inside Lens" depth. While it is above zero nothing is observed: Lens's own capture
 * and dispatch code, listeners, {@link Lens#unobserved(Runnable)} and {@code lens.quietly(fn)} all
 * run inside it. This is what stops an event from causing another event.
 */
final class Guard {

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private Guard() {
    }

    static boolean active() {
        try {
            return DEPTH.get()[0] > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    static void enter() {
        try {
            DEPTH.get()[0]++;
        } catch (Throwable ignored) {
            // nothing sensible to do; observation simply stays on
        }
    }

    static void exit() {
        try {
            int[] depth = DEPTH.get();
            if (depth[0] > 0) {
                depth[0]--;
            }
        } catch (Throwable ignored) {
            // see enter()
        }
    }
}
