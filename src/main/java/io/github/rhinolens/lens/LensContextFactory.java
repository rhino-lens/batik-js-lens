package io.github.rhinolens.lens;

import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;

/**
 * Used only with {@link Lens#installAsGlobalFactory(LensConfig)}: as Rhino's global factory it
 * instruments every Context and turns each top-level script evaluation into an automatic
 * execution (unless the application already opened one), so {@link Lens#lastReport()} works
 * without any change to calling code.
 */
final class LensContextFactory extends ContextFactory {

    @Override
    protected void onContextCreated(Context cx) {
        super.onContextCreated(cx);
        Lens.instrument(cx);
    }

    @Override
    protected Object doTopCall(Callable callable, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        LensExecution auto = LensSafe.call("auto-execution",
                () -> Lens.wrapping() && ExecutionTracker.current() == null ? Lens.automaticExecution() : null, null);
        if (auto == null) {
            return super.doTopCall(callable, cx, scope, thisObj, args);
        }
        try {
            Object result = super.doTopCall(callable, cx, scope, thisObj, args);
            auto.succeeded();
            return result;
        } catch (RuntimeException | Error e) {
            auto.failed(e);
            throw e;
        } finally {
            auto.finish();
        }
    }
}
