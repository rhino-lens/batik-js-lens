package org.mozilla.javascript;

/**
 * Internal to batik-js-lens; not part of its API.
 *
 * <p>Lives in Rhino's package (the Rhino jar is not sealed) only to read where the interpreter is:
 * the same package-private method Rhino itself uses for error messages. It changes nothing.
 */
public final class LensAccess {

    private LensAccess() {
    }

    /**
     * Script name and line of the innermost interpreted frame on this thread, as
     * {@code {String name, Integer line}}, or {@code null} when no interpreted script is running
     * (no Context, or compiled mode).
     */
    public static Object[] interpretedPosition() {
        Context cx = Context.getCurrentContext();
        if (cx == null || cx.lastInterpreterFrame == null) {
            return null;
        }
        int[] line = { 0 };
        String name = Interpreter.getSourcePositionFromStack(cx, line);
        return name == null ? null : new Object[] { name, Integer.valueOf(line[0]) };
    }
}
