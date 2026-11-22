package io.github.rhinolens.lens;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Where an execution was opened: the application method (and line) that called
 * {@link Lens#start} or {@link Lens#execution}.
 *
 * <p>Used to notice executions that were never finished (for example, an exception path that
 * skipped {@code finish()}). When a new execution is opened while an older one is still open,
 * the older one is still running only if the method that opened it is still on the stack, below
 * the new caller. Otherwise that method has returned without finishing it. Two cases are decided
 * from the call sites alone:
 * <ul>
 * <li>the very same call site again (same method and line, e.g. a loop): the old one is over;</li>
 * <li>the same method at a different line: nested in one method, so the old one is still open.</li>
 * </ul>
 * Recording a call site walks only a few frames. The full stack is walked only when an execution
 * is opened while another is already open, which is rare.
 */
final class CallSite {

    /** Lens classes whose frames are skipped to find the application's frame. */
    private static final Set<String> INTERNAL = Set.of(
            Lens.class.getName(), LensSafe.class.getName(), ExecutionTracker.class.getName(),
            LensExecution.class.getName(), CallSite.class.getName());

    private static final StackWalker WALKER = StackWalker.getInstance();

    private final String className;
    private final String methodName;
    private final int line;

    CallSite(String className, String methodName, int line) {
        this.className = className;
        this.methodName = methodName;
        this.line = line;
    }

    /** The calling application frame, or {@code null} if it cannot be found (flagged). */
    static CallSite ofCaller() {
        return LensSafe.call("call-site", () -> WALKER.walk(frames -> frames
                .filter(f -> !isInternal(f.getClassName()))
                .findFirst()
                .map(f -> new CallSite(f.getClassName(), f.getMethodName(), f.getLineNumber()))
                .orElse(null)), null);
    }

    /**
     * The methods running below the current caller ({@code className#methodName}): the methods
     * that are still waiting for the current call to return.
     */
    static Set<String> callersOfCaller() {
        return WALKER.walk(frames -> {
            Set<String> out = new HashSet<>();
            boolean pastCaller = false;
            for (Iterator<StackWalker.StackFrame> it = frames.iterator(); it.hasNext(); ) {
                StackWalker.StackFrame f = it.next();
                if (!pastCaller) {
                    pastCaller = !isInternal(f.getClassName()); // the first application frame is the caller
                    continue;
                }
                out.add(f.getClassName() + "#" + f.getMethodName());
            }
            return out;
        });
    }

    private static boolean isInternal(String className) {
        if (INTERNAL.contains(className)) {
            return true;
        }
        int nested = className.indexOf('$');
        return nested > 0 && INTERNAL.contains(className.substring(0, nested)); // lambdas, inner classes
    }

    /**
     * True if an execution opened here is still running, given a new one is being opened at
     * {@code now} with {@code callers} below it.
     */
    boolean isStillRunning(CallSite now, Set<String> callers) {
        boolean sameMethod = className.equals(now.className) && methodName.equals(now.methodName);
        if (sameMethod) {
            return line != now.line; // same line again: a repeat, so the earlier one is over
        }
        return callers.contains(className + "#" + methodName);
    }

    @Override
    public String toString() {
        return className + "." + methodName + ":" + line;
    }
}
