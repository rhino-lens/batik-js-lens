package io.github.rhinolens.lens;

import java.util.function.Supplier;

/**
 * The only way Lens hooks run observation code.
 *
 * <p>Every step is wrapped in try/catch. A failure is flagged through {@link LensDiagnostics}
 * and a safe fallback is returned, so observation can never break the script that is running.
 * The only exception that passes through is {@link OutOfMemoryError}, which no code can recover
 * from by carrying on.
 */
final class LensSafe {

    private LensSafe() {
    }

    /** Runs {@code code}; on failure flags it under {@code step} and carries on. */
    static void run(String step, Runnable code) {
        try {
            code.run();
        } catch (Throwable t) {
            handle(step, t);
        }
    }

    /** Runs {@code code}; on failure flags it under {@code step} and returns {@code fallback}. */
    static <T> T call(String step, Supplier<T> code, T fallback) {
        try {
            return code.get();
        } catch (Throwable t) {
            handle(step, t);
            return fallback;
        }
    }

    private static void handle(String step, Throwable t) {
        if (t instanceof OutOfMemoryError) {
            throw (OutOfMemoryError) t;
        }
        LensDiagnostics.flag(step, t);
    }
}
