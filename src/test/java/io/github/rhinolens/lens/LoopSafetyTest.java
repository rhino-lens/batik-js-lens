package io.github.rhinolens.lens;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.rhinolens.testapp.Order;
import io.github.rhinolens.testapp.RuleEngineHarness;

/** Reading observations and writing them back can never loop. */
class LoopSafetyTest extends LensTestBase {

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void writingTheReportIntoAnObservedObjectAddsExactlyOneEvent(int opt) {
        LensResult<Object> r = Lens.observe("audit", null, () -> engine(opt).run("audit.js",
                "order.setStatus('A');\norder.setAuditJson(lens.report().toJson());", scope()));

        assertThat(r.report().propertyChanges()).extracting(PropertyChange::property)
                .containsExactly("status", "auditJson");
        assertThat(order.getAuditJson()).contains("\"status\"");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void usingReportObjectsCreatesNoEvents(int opt) {
        run(opt, "read-report.js",
                "var r = lens.report(); r.toJson(); r.toText(); r.events(); r.netChanges(); r.stats().counts();");
        assertThat(journal.peek()).isEmpty();
    }

    @Test
    void aListenerThatRunsAnotherScriptDoesNotRecurse() {
        Order other = new Order(7);
        Lens.addListener(new ChangeListener() {
            @Override
            public void onEvent(ChangeEvent event) {
                // runs a script that changes an observed object, inside the dispatch of an event
                RuleEngineHarness nested = new RuleEngineHarness(9, false);
                try {
                    nested.run("nested.js", "other.setStatus('FROM-LISTENER');", Map.of("other", other));
                } finally {
                    nested.close();
                }
            }
        });

        LensResult<Object> r = Lens.observe("outer", null,
                () -> engine(9).run("outer.js", "order.setStatus('A');", scope()));

        assertThat(other.getStatus()).isEqualTo("FROM-LISTENER");
        assertThat(r.report().events()).hasSize(1);
        assertThat(r.report().stats().suppressed()).isPositive();
        assertThat(journal.peek()).hasSize(1);
        assertThat(Lens.diagnostics().total()).isZero();
    }

    @Test
    void aRunawayLoopIsCappedAndTheScriptCompletes() {
        Lens.install(LensConfig.defaults().maxEventsPerExecution(1_000));
        long start = System.nanoTime();
        LensResult<Object> r = Lens.observe("runaway", null, () -> engine(9).run("runaway.js",
                "for (var i = 0; i < 1000000; i++) { order.setFinalTotal(i); }\n'done';", scope()));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(String.valueOf(r.value())).isEqualTo("done");
        assertThat(order.getFinalTotal()).isEqualTo(999_999.0);
        assertThat(r.report().events()).hasSize(1_000);
        assertThat(r.report().isTruncated()).isTrue();
        assertThat(r.report().stats().dropped()).isEqualTo(999_000);
        System.out.println("1,000,000 setter calls with a 1,000-event cap: " + millis + " ms");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void quietlyAndUnobservedRecordNothing(int opt) {
        LensResult<Object> r = Lens.observe("quiet", null, () -> engine(opt).run("quiet.js",
                "lens.quietly(function () { order.setStatus('SILENT'); order.note = 'x'; });\norder.setFinalTotal(1);",
                scope()));
        assertThat(order.getStatus()).isEqualTo("SILENT");
        assertThat(r.report().propertyChanges()).extracting(PropertyChange::property).containsExactly("finalTotal");
        assertThat(r.report().stats().suppressed()).isPositive();

        journal.clear();
        Lens.unobserved(() -> engine(opt).run("unobserved.js", "order.setStatus('ALSO-SILENT');", scope()));
        assertThat(journal.peek()).isEmpty();
        assertThat(order.getStatus()).isEqualTo("ALSO-SILENT");
    }
}
