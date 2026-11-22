package example.app;

import java.util.HashMap;
import java.util.Map;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * Runs rule scripts the way a typical Rhino rule engine does, so Lens is tested exactly in that
 * setting (no Lens-specific calls in here):
 * <ul>
 * <li>{@code Context.enter()} directly (Rhino's global factory);</li>
 * <li>the Context and scope are kept in a thread-local and reused by later rules on the thread;</li>
 * <li>the scope is either a fresh {@code initStandardObjects()} or a child of a shared library
 *     scope (prototype = shared scope, no parent);</li>
 * <li>Java objects and services are put into the scope raw: {@code scope.put(key, scope, value)};</li>
 * <li>optimization level is set per run (9 = compiled, -1 = interpreted);</li>
 * <li>{@code evaluateString(scope, script, ruleName, 1, null)}.</li>
 * </ul>
 */
public final class RuleEngineHarness implements AutoCloseable {

    private static final ThreadLocal<Map<String, Object>> SESSION = new ThreadLocal<>();

    private final int optimizationLevel;
    private final boolean includeLibrary;
    private final Scriptable sharedScope;

    public RuleEngineHarness(int optimizationLevel, boolean includeLibrary) {
        this.optimizationLevel = optimizationLevel;
        this.includeLibrary = includeLibrary;
        this.sharedScope = includeLibrary ? buildSharedScope() : null;
    }

    /** Runs {@code script} named {@code ruleName} with {@code scopeObjects} in scope; returns its result. */
    public Object run(String ruleName, String script, Map<String, Object> scopeObjects) {
        Context ctx;
        Scriptable scope;
        Map<String, Object> session = SESSION.get();
        if (session != null && session.get("ctx") != null && session.get("scope") != null) {
            ctx = (Context) session.get("ctx");
            scope = (Scriptable) session.get("scope");
        } else {
            ctx = Context.enter();
            if (includeLibrary) {
                scope = ctx.newObject(sharedScope);
                scope.setPrototype(sharedScope);
                scope.setParentScope(null);
            } else {
                scope = ctx.initStandardObjects();
            }
        }
        for (Map.Entry<String, Object> e : scopeObjects.entrySet()) {
            scope.put(e.getKey(), scope, e.getValue());
        }
        scope.put("out", scope, System.out);
        ctx.setOptimizationLevel(optimizationLevel);
        Object result = ctx.evaluateString(scope, script, ruleName, 1, null);
        Map<String, Object> keep = new HashMap<>();
        keep.put("ctx", ctx);
        keep.put("scope", scope);
        SESSION.set(keep);
        return result instanceof org.mozilla.javascript.Wrapper
                ? ((org.mozilla.javascript.Wrapper) result).unwrap() : result;
    }

    /** Same as {@link #run} but returns the result as text (JS numbers 3.0 → "3"). */
    public String runToString(String ruleName, String script, Map<String, Object> scopeObjects) {
        Object r = run(ruleName, script, scopeObjects);
        return Context.toString(r);
    }

    /** Ends the thread's session (exits the Context), like a request finishing. */
    @Override
    public void close() {
        Map<String, Object> session = SESSION.get();
        if (session != null) {
            SESSION.remove();
            Context.exit();
        }
    }

    private static Scriptable buildSharedScope() {
        Context cx = Context.enter();
        try {
            Scriptable shared = cx.initStandardObjects();
            cx.evaluateString(shared, "function money(x) { return Math.round(x * 100) / 100; }", "library.js", 1, null);
            return shared;
        } finally {
            Context.exit();
        }
    }
}
