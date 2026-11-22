package org.mozilla.javascript.lens;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import example.app.Fragile;

/**
 * Observation failures never break a script: each test runs the same script with Lens off and on,
 * and checks the script result is identical, while the failure is flagged.
 */
class FailureIsolationTest extends LensTestBase {

    /** Runs the script with Lens disabled, then enabled, and returns both results. */
    private String[] offAndOn(int opt, String rule, String script, Map<String, Object> extra) {
        Map<String, Object> s = scope();
        s.putAll(extra);
        Lens.setEnabled(false);
        String off = engine(opt).runToString(rule, script, s);
        Lens.setEnabled(true);
        journal.clear();
        Lens.diagnostics().reset();
        Map<String, Object> s2 = scope();
        s2.putAll(extra);
        String on = engine(opt).runToString(rule, script, s2);
        return new String[] { off, on };
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void throwingGetterDuringValueCapture(int opt) {
        String[] r = offAndOn(opt, "fragile.js", "fragile.setValue(5);\n'done:' + fragile.peek();",
                Map.of("fragile", new Fragile()));

        assertThat(r[1]).isEqualTo(r[0]).isEqualTo("done:5");
        assertThat(Lens.diagnostics().counts()).containsKey("read-value");
        PropertyChange c = events(PropertyChange.class).get(0);
        assertThat(c.isIncomplete()).isTrue();
        assertThat(c.oldValue().text()).startsWith("<unavailable");
        assertThat(c.ruleName()).isEqualTo("fragile.js");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void throwingToStringWhileRenderingAValue(int opt) {
        Fragile f = new Fragile();
        String[] r = offAndOn(opt, "render.js", "audit.record(fragile);\n'ok';", Map.of("fragile", f));

        assertThat(r[1]).isEqualTo(r[0]).isEqualTo("ok");
        assertThat(Lens.diagnostics().counts()).containsKey("render-toString");
        ServiceCall call = events(ServiceCall.class).get(0);
        assertThat(call.args().get(0).text()).startsWith("Fragile@");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void aThrowingListenerDoesNotStopOtherListenersOrTheScript(int opt) {
        List<ChangeEvent> seen = new ArrayList<>();
        List<ObservationFailure> failures = new ArrayList<>();
        Lens.addListener(event -> {
            throw new IllegalStateException("listener exploded");
        });
        Lens.addListener(event -> {
            if (event instanceof ObservationFailure) {
                failures.add((ObservationFailure) event);
            } else {
                seen.add(event);
            }
        });

        String[] r = offAndOn(opt, "listeners.js", "order.setStatus('A');\n'ok';", Map.of());

        assertThat(r[1]).isEqualTo(r[0]).isEqualTo("ok");
        assertThat(seen).hasSize(1);
        assertThat(failures).isNotEmpty();
        assertThat(failures.get(0).error()).contains("listener exploded");
        assertThat(Lens.diagnostics().counts().keySet()).anyMatch(step -> step.startsWith("listener "));
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void aFailingIdResolver(int opt) {
        Lens.install(LensConfig.defaults().idResolver(o -> {
            throw new IllegalStateException("no ids today");
        }));
        String[] r = offAndOn(opt, "ids.js", "order.setStatus('A');\n'ok';", Map.of());

        assertThat(r[1]).isEqualTo(r[0]);
        assertThat(Lens.diagnostics().counts()).containsKey("id-resolver");
        assertThat(events(PropertyChange.class).get(0).target().entityId()).isNull();
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void aFailingPositionLookup(int opt) {
        TestHooks.arm(TestHooks.Point.POSITION);
        String[] r = offAndOn(opt, "position.js", "order.setStatus('A');\n'ok';", Map.of());

        assertThat(r[1]).isEqualTo(r[0]);
        assertThat(Lens.diagnostics().counts()).containsKey("position");
        PropertyChange c = events(PropertyChange.class).get(0);
        assertThat(c.ruleName()).isNull();
        assertThat(c.line()).isEqualTo(-1);
        assertThat(c.newValue().text()).isEqualTo("\"A\"");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void aFailingWrapperFallsBackToPlainRhino(int opt) {
        TestHooks.arm(TestHooks.Point.WRAP_OBJECT);
        TestHooks.arm(TestHooks.Point.WRAP_ARRAY);
        String[] r = offAndOn(opt, "wrap.js",
                "order.setStatus('A');\norder.getTags()[0] = 'x';\norder.status + order.getTags()[0];", Map.of());

        assertThat(r[1]).isEqualTo(r[0]).isEqualTo("Ax");
        assertThat(journal.peek()).isEmpty();
        assertThat(Lens.diagnostics().counts()).containsKeys("wrap-object", "wrap-array");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void aSettersOwnExceptionReachesTheScriptUnchanged(int opt) {
        String script = "var msg;\n"
                + "try { order.getItems().get(0).setQuantity(-1); msg = 'no error'; }\n"
                + "catch (e) { msg = e.javaException ? e.javaException.getMessage() : String(e); }\n"
                + "msg;";
        String[] r = offAndOn(opt, "setter-error.js", script, Map.of());

        assertThat(r[1]).isEqualTo(r[0]).isEqualTo("quantity must be >= 0 but was -1");
        assertThat(events(PropertyChange.class)).isEmpty(); // nothing changed
        assertThat(Lens.diagnostics().total()).isZero();
    }
}
