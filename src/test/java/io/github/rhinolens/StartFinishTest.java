package io.github.rhinolens;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mozilla.javascript.EcmaError;

/**
 * {@link Lens#start} / {@link Lens#finish}: marking a rule run in the application's own method
 * (start before {@code evaluateString}, finish in {@code finally}), including forgotten finishes.
 */
class StartFinishTest extends LensTestBase {

    /** Collects every finished report, as an application listener would. */
    private final List<LensReport> finished = new ArrayList<>();

    private void collectReports() {
        Lens.addListener(new ChangeListener() {
            @Override
            public void onEvent(ChangeEvent event) {
            }

            @Override
            public void onExecutionFinished(LensReport report) {
                finished.add(report);
            }
        });
    }

    /** The application's rule method, unchanged except for the two Lens lines. */
    private LensReport runRule(int opt, String ruleName, String script) {
        Lens.start(ruleName, Map.of("recordId", 42));
        LensReport report;
        try {
            engine(opt).run(ruleName, script, scope());
        } finally {
            report = Lens.finish();
        }
        return report;
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void startAndFinishAroundEvaluateString(int opt) {
        collectReports();
        LensReport report = runRule(opt, "status-rule.js", "order.status = 'PRICED';\norder.setFinalTotal(42);");

        assertThat(report.outcome()).isEqualTo(LensReport.Outcome.SUCCESS);
        assertThat(report.ruleName()).isEqualTo("status-rule.js");
        assertThat(report.attributes()).containsEntry("recordId", 42);
        assertThat(report.propertyChanges()).hasSize(2);
        assertThat(report.propertyChanges()).extracting(ChangeEvent::line).containsExactly(1, 2);
        assertThat(finished).containsExactly(report);
        assertThat(Lens.lastReport()).isSameAs(report);
        assertThat(Lens.currentExecution()).isNull();
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void finishWithErrorMarksTheRunFailed(int opt) {
        Lens.start("broken.js");
        LensReport report;
        try {
            engine(opt).run("broken.js", "order.setStatus('A');\nmissingFunction();", scope());
            report = Lens.finish();
        } catch (EcmaError e) {
            report = Lens.finish(e);
        }
        assertThat(report.outcome()).isEqualTo(LensReport.Outcome.FAILED);
        assertThat(report.error()).contains("missingFunction");
        assertThat(report.propertyChanges()).hasSize(1);
    }

    @Test
    void whenLensIsOffStartAndFinishDoNothing() {
        Lens.setEnabled(false);
        LensReport report = runRule(9, "off.js", "order.setStatus('A');");

        assertThat(report).isNull();
        assertThat(Lens.currentExecution()).isNull();
        assertThat(order.getStatus()).isEqualTo("A"); // the script ran normally
    }

    @Test
    void finishWithoutStartReturnsNullAndLeavesOtherExecutionsOpen() {
        LensExecution x = Lens.execution("explicit", null);
        assertThat(Lens.finish()).isNull();
        assertThat(Lens.currentExecution()).isSameAs(x);
        x.finish();
    }

    // ---- forgotten finish() ----------------------------------------------------------------

    /** A buggy caller: starts a run and returns without finishing it. */
    private void forgetToFinish(String ruleName) {
        Lens.start(ruleName);
        engine(9).run(ruleName, "order.setStatus('LEAKED');", scope());
    }

    @Test
    void aForgottenRunIsClosedByTheNextStart() {
        collectReports();
        forgetToFinish("leaky.js");
        assertThat(Lens.currentExecution()).isNotNull(); // still open after its method returned

        LensReport next = runRule(9, "next.js", "order.setStatus('NEXT');");

        assertThat(finished).hasSize(2);
        LensReport leaked = finished.get(0);
        assertThat(leaked.ruleName()).isEqualTo("leaky.js");
        assertThat(leaked.outcome()).isEqualTo(LensReport.Outcome.STOPPED);
        assertThat(leaked.propertyChanges()).hasSize(1);
        assertThat(next.outcome()).isEqualTo(LensReport.Outcome.SUCCESS);
        assertThat(next.propertyChanges()).hasSize(1); // its own change only
        assertThat(Lens.diagnostics().counts()).containsEntry("execution-not-finished", 1L);
        assertThat(Lens.currentExecution()).isNull();
    }

    @Test
    void startInALoopWithoutFinishKeepsOnlyOneRunOpen() {
        collectReports();
        for (int i = 0; i < 3; i++) {
            Lens.start("loop-" + i);
            engine(9).run("loop.js", "order.setStatus('S" + i + "');", scope());
        }
        assertThat(finished).extracting(LensReport::outcome)
                .containsExactly(LensReport.Outcome.STOPPED, LensReport.Outcome.STOPPED);
        assertThat(Lens.finish().ruleName()).isEqualTo("loop-2");
        assertThat(Lens.currentExecution()).isNull();
    }

    // ---- legitimate nesting stays open ------------------------------------------------------

    /** A rule whose work runs another rule (deeper in the stack), each with start/finish. */
    private LensReport outerRule() {
        Lens.start("outer.js");
        LensReport report;
        try {
            engine(9).run("outer.js", "order.setStatus('OUTER');", scope());
            LensReport inner = runRule(9, "inner.js", "order.setFinalTotal(7);");
            assertThat(inner.outcome()).isEqualTo(LensReport.Outcome.SUCCESS);
            assertThat(inner.propertyChanges()).extracting(PropertyChange::property).containsExactly("finalTotal");
            engine(9).run("outer.js", "order.note = 'after inner';", scope());
        } finally {
            report = Lens.finish();
        }
        return report;
    }

    @Test
    void aRunStartedDeeperInsideAnotherIsNested() {
        LensReport outer = outerRule();

        assertThat(outer.outcome()).isEqualTo(LensReport.Outcome.SUCCESS);
        assertThat(outer.propertyChanges()).extracting(PropertyChange::property).containsExactly("status", "note");
        assertThat(Lens.diagnostics().counts()).doesNotContainKey("execution-not-finished");
    }

    @Test
    void twoRunsNestedInOneMethodBothStayOpen() {
        Lens.start("batch");
        Lens.start("rule-in-batch");                      // same method, next line: nested, not forgotten
        engine(9).run("rule-in-batch", "order.setStatus('B');", scope());
        LensReport inner = Lens.finish();
        LensReport outer = Lens.finish();

        assertThat(inner.ruleName()).isEqualTo("rule-in-batch");
        assertThat(inner.outcome()).isEqualTo(LensReport.Outcome.SUCCESS);
        assertThat(outer.ruleName()).isEqualTo("batch");
        assertThat(outer.outcome()).isEqualTo(LensReport.Outcome.SUCCESS);
        assertThat(Lens.diagnostics().counts()).doesNotContainKey("execution-not-finished");
    }

    // ---- failure safety ---------------------------------------------------------------------

    @Test
    void executionNeverThrowsEvenIfItsAttributesDo() {
        Map<String, Object> hostile = new AbstractMap<String, Object>() {
            @Override
            public Set<Entry<String, Object>> entrySet() {
                throw new IllegalStateException("boom");
            }
        };

        LensExecution x = Lens.execution("hostile", hostile);
        Lens.start("hostile-start", hostile);

        assertThat(x).isNotNull();
        assertThat(Lens.currentExecution()).isNull(); // neither was tracked
        assertThat(x.finish().ruleName()).isEqualTo("hostile");
        assertThat(Lens.finish()).isNull();
        assertThat(Lens.diagnostics().counts()).containsEntry("execution", 1L).containsEntry("start", 1L);
        assertThat(engine(9).runToString("after.js", "order.setStatus('OK'); order.getStatus()", scope()))
                .isEqualTo("OK");
    }
}
