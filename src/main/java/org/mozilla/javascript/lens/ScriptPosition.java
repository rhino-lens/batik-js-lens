package org.mozilla.javascript.lens;

import java.util.Optional;

import org.mozilla.javascript.GeneratedClassLoader;
import org.mozilla.javascript.LensAccess;

/**
 * Which script (the name given to {@code evaluateString}) and which line is running.
 *
 * <ul>
 * <li>Interpreted mode (optimization level -1): read from Rhino's interpreter frame.</li>
 * <li>Compiled mode (0–9): the first stack frame of a class Rhino generated, found by its name
 * ({@code org.mozilla.javascript.gen.*}), or else by its {@link GeneratedClassLoader}. Its file
 * name is the script name, which Rhino writes into the class only when {@code generatingDebug}
 * is on; {@link Lens#instrument} turns it on.</li>
 * </ul>
 */
final class ScriptPosition {

    static final ScriptPosition UNKNOWN = new ScriptPosition(null, -1);

    /** Rhino 1.6R5 compiles each script into one class named {@code org.mozilla.javascript.gen.c<n>}. */
    private static final String GENERATED_PREFIX = "org.mozilla.javascript.gen.";

    /** Fast path: matches frames by class name, without fetching class objects; the script frame is near the top. */
    private static final StackWalker BY_NAME = StackWalker.getInstance(java.util.Collections.emptySet(), 8);

    /** Fallback for scripts precompiled under other names: matches by class loader (slower). */
    private static final StackWalker BY_LOADER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private final String ruleName;
    private final int line;

    ScriptPosition(String ruleName, int line) {
        this.ruleName = ruleName;
        this.line = line;
    }

    String ruleName() {
        return ruleName;
    }

    int line() {
        return line;
    }

    /** Current position; failures are flagged and give {@link #UNKNOWN}. */
    static ScriptPosition current() {
        return LensSafe.call("position", ScriptPosition::lookup, UNKNOWN);
    }

    /** For {@link LensDiagnostics} itself: same lookup, but a failure is not flagged again. */
    static ScriptPosition currentOrUnknownNoFlag() {
        try {
            return lookup();
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    private static ScriptPosition lookup() {
        TestHooks.maybeFail(TestHooks.Point.POSITION);
        Object[] interpreted = LensAccess.interpretedPosition();
        if (interpreted != null) {
            return new ScriptPosition((String) interpreted[0], (Integer) interpreted[1]);
        }
        if (!Lens.config().lineNumbersInCompiledMode()) {
            return UNKNOWN;
        }
        Optional<ScriptPosition> compiled = BY_NAME.walk(frames -> frames
                .filter(f -> f.getClassName().startsWith(GENERATED_PREFIX))
                .findFirst()
                .map(f -> new ScriptPosition(f.getFileName(), f.getLineNumber())));
        if (compiled.isPresent()) {
            return compiled.get();
        }
        return BY_LOADER.walk(frames -> frames
                .filter(f -> f.getDeclaringClass().getClassLoader() instanceof GeneratedClassLoader)
                .findFirst()
                .map(f -> new ScriptPosition(f.getFileName(), f.getLineNumber())))
                .orElse(UNKNOWN);
    }
}
