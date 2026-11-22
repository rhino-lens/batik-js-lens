package org.mozilla.javascript.lens;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Scriptable;

import example.app.Order;

/**
 * The global-factory mode: each top-level evaluation becomes an execution automatically. Runs the
 * factory directly (Rhino allows only one global factory per JVM, and tests share one JVM).
 */
class AutoExecutionTest extends LensTestBase {

    @Test
    void eachEvaluationBecomesAnExecution() throws Exception {
        AtomicReference<LensReport> first = new AtomicReference<>();
        AtomicReference<LensReport> second = new AtomicReference<>();
        Order o = new Order(5);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            // As Rhino's global factory (installAsGlobalFactory) Lens's factory is used for every
            // Context; here it is attached the way Rhino 1.6R5 does it outside initGlobal: call().
            LensContextFactory factory = new LensContextFactory();
            factory.call(cx -> {
                Scriptable scope = cx.initStandardObjects();
                scope.put("order", scope, o);
                cx.setOptimizationLevel(9);
                cx.evaluateString(scope, "order.setStatus('A');", "auto-1.js", 1, null);
                first.set(Lens.lastReport());
                cx.evaluateString(scope, "order.setStatus('B');\norder.note = 'n';", "auto-2.js", 1, null);
                second.set(Lens.lastReport());
                return null;
            });
        });
        t.setUncaughtExceptionHandler((th, e) -> failure.set(e));
        t.start();
        t.join();
        if (failure.get() != null) {
            throw new AssertionError("script thread failed", failure.get());
        }

        assertThat(first.get().ruleName()).isEqualTo("auto-1.js");
        assertThat(first.get().events()).hasSize(1);
        assertThat(second.get().ruleName()).isEqualTo("auto-2.js");
        assertThat(second.get().events()).hasSize(2);
        assertThat(second.get().executionId()).isNotEqualTo(first.get().executionId());
    }
}
