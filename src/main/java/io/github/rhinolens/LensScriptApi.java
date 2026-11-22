package io.github.rhinolens;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * What scripts see as {@code lens}, once the application puts it in scope:
 * {@code scope.put("lens", scope, Lens.scriptApi())}.
 *
 * <pre>{@code
 * order.setStatus("PRICED");
 * var r = lens.report();                       // the execution so far
 * print(r.changeCount() + " changes in " + r.durationMillis() + " ms");
 * auditService.record(r.toJson());             // one ServiceCall event, no loop
 * lens.mark("pricing done");                   // a checkpoint event
 * lens.quietly(function () {                   // not observed
 *     order.setAuditJson(r.toJson());
 * });
 * }</pre>
 *
 * <p>Lens's own objects are never observed, so reading reports creates no events.
 */
public final class LensScriptApi {

    LensScriptApi() {
    }

    /** The open execution so far (outcome RUNNING), else the last report on this thread, else empty. */
    public LensReport report() {
        return LensSafe.call("script-report", () -> {
            LensExecution x = ExecutionTracker.current();
            if (x != null) {
                return x.report();
            }
            LensReport last = ExecutionTracker.last();
            return last != null ? last : LensReport.empty();
        }, LensReport.empty());
    }

    /** Milliseconds since the open execution started (0 if none). */
    public double elapsedMillis() {
        return LensSafe.call("script-elapsed", () -> {
            LensExecution x = ExecutionTracker.current();
            return x == null ? 0.0 : x.elapsedNanos() / 1e6;
        }, 0.0);
    }

    /** Records a checkpoint event with this label at the current line. */
    public void mark(String label) {
        if (!Lens.observing()) {
            return;
        }
        Guard.enter();
        try {
            LensSafe.run("script-mark", () -> Dispatcher.emit(new Mark(ScriptPosition.current(), label)));
        } finally {
            Guard.exit();
        }
    }

    /**
     * Runs {@code fn} with observation off (what it does is counted as suppressed) and returns its
     * result. Errors {@code fn} throws reach the script as usual.
     */
    public Object quietly(Function fn) {
        Context cx = Context.getCurrentContext();
        Scriptable scope = ScriptableObject.getTopLevelScope(fn);
        Guard.enter();
        try {
            return fn.call(cx, scope, scope, new Object[0]);
        } finally {
            Guard.exit();
        }
    }
}
