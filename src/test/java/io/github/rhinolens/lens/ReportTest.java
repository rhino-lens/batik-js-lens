package io.github.rhinolens.lens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mozilla.javascript.EcmaError;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** LensReport: timing, outcomes, net changes, exports, and reading it from a script. */
class ReportTest extends LensTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void successReportHasTimingEventsAndStats(int opt) {
        LensResult<Object> r = Lens.observe("pricing", Map.of("recordId", 42), () -> engine(opt).run("pricing.js",
                "order.setStatus('PRICED');\nrepo.save(order);\norder.getItems().add(order.getItems().get(0));", scope()));

        LensReport report = r.report();
        assertThat(report.outcome()).isEqualTo(LensReport.Outcome.SUCCESS);
        assertThat(report.durationNanos()).isPositive();
        assertThat(report.endedAt()).isAfterOrEqualTo(report.startedAt());
        assertThat(report.events()).hasSize(3);
        assertThat(report.changeCount()).isEqualTo(2);
        assertThat(report.stats().count(ChangeEvent.Kind.SERVICE_CALL)).isEqualTo(1);
        assertThat(report.stats().serviceCallNanos()).isPositive();
        assertThat(report.stats().overheadNanos()).isPositive();
        assertThat(report.serviceCalls()).hasSize(1);
        assertThat(report.changesTo(order)).hasSize(1);
        assertThat(Lens.lastReport()).isSameAs(report);
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void failedScriptStillHasAReport(int opt) {
        assertThatThrownBy(() -> Lens.observe("broken", null,
                () -> engine(opt).run("broken.js", "order.setStatus('A');\nmissingFunction();", scope())))
                .isInstanceOf(EcmaError.class);

        LensReport report = Lens.lastReport();
        assertThat(report.outcome()).isEqualTo(LensReport.Outcome.FAILED);
        assertThat(report.error()).contains("missingFunction");
        assertThat(report.propertyChanges()).hasSize(1);
    }

    @Test
    void stoppedOutcome() {
        LensExecution x = Lens.execution("stopped-rule", null);
        engine(9).run("stop.js", "order.setStatus('A');", scope());
        x.stopped();
        LensReport report = x.finish();
        assertThat(report.outcome()).isEqualTo(LensReport.Outcome.STOPPED);
        assertThat(x.finish()).isSameAs(report); // finish is idempotent
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void netChangesCollapseRepeatedChanges(int opt) {
        LensResult<Object> r = Lens.observe("net", null, () -> engine(opt).run("net.js",
                "order.setStatus('A');\norder.status = 'B';\norder.setStatus('C');\norder.setFinalTotal(5);", scope()));

        List<NetChange> net = r.report().netChanges();
        assertThat(net).hasSize(2);
        NetChange status = net.get(0);
        assertThat(status.property()).isEqualTo("status");
        assertThat(status.oldValue().text()).isEqualTo("\"NEW\"");
        assertThat(status.newValue().text()).isEqualTo("\"C\"");
        assertThat(status.changes()).isEqualTo(3);
        assertThat(status.lines()).containsExactly(1, 2, 3);
        assertThat(status.isEffective()).isTrue();
    }

    @Test
    void jsonRoundTripsAndMatchesTheSchema() throws Exception {
        LensResult<Object> r = Lens.observe("json", Map.of("recordId", 42, "user", "ann"), () -> engine(9).run(
                "json.js", "order.setStatus('A \"quoted\"\\n');\nrepo.save(order);", scope()));

        JsonNode root = JSON.readTree(r.report().toJson());
        assertThat(root.get("ruleName").asText()).isEqualTo("json");
        assertThat(root.get("attributes").get("recordId").asInt()).isEqualTo(42);
        assertThat(root.get("events")).hasSize(2);
        assertThat(root.get("events").get(0).get("newValue").get("text").asText()).isEqualTo("\"A \\\"quoted\\\"\\n\"");

        Path schemaPath = Paths.get("docs", "report.schema.json");
        JsonNode schema = JSON.readTree(Files.readString(schemaPath));
        for (JsonNode required : schema.get("required")) {
            assertThat(root.has(required.asText())).as("report has %s", required.asText()).isTrue();
        }
        JsonNode eventSchema = schema.get("$defs").get("event");
        for (JsonNode event : root.get("events")) {
            for (JsonNode required : eventSchema.get("required")) {
                assertThat(event.has(required.asText())).as("event has %s", required.asText()).isTrue();
            }
        }
        Iterator<String> fields = root.fieldNames();
        while (fields.hasNext()) {
            String f = fields.next();
            assertThat(schema.get("properties").has(f)).as("schema documents %s", f).isTrue();
        }
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void scriptCanReadTheReportSoFar(int opt) {
        LensResult<Object> r = Lens.observe("self", null, () -> engine(opt).run("self.js",
                "order.setStatus('A');\nvar rep = lens.report();\nlens.mark('checked');\n"
                        + "rep.changeCount() + ':' + rep.outcome() + ':' + (lens.elapsedMillis() >= 0);", scope()));

        assertThat(String.valueOf(r.value())).isEqualTo("1:RUNNING:true");
        assertThat(r.report().eventsOf(Mark.class)).extracting(Mark::label, Mark::line)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("checked", 3));
    }

    @Test
    void textReportForLogs() {
        LensResult<Object> r = Lens.observe("text", null,
                () -> engine(9).run("text.js", "order.setStatus('A');\norder.setStatus('B');", scope()));
        String text = r.report().toText();
        assertThat(text).contains("Lens report: text SUCCESS")
                .contains("[text.js:1] SETTER Order.status \"NEW\" -> \"A\"")
                .contains("net changes:")
                .contains("Order.status \"NEW\" -> \"B\" (2 changes)");
    }
}
