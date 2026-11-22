package org.mozilla.javascript.lens.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import org.mozilla.javascript.lens.ChangeEvent;
import org.mozilla.javascript.lens.ChangeListener;
import org.mozilla.javascript.lens.InMemoryJournal;
import org.mozilla.javascript.lens.JulChangeLogger;
import org.mozilla.javascript.lens.Lens;
import org.mozilla.javascript.lens.LensConfig;
import org.mozilla.javascript.lens.LensExecution;
import org.mozilla.javascript.lens.LensReport;
import org.mozilla.javascript.lens.LensResult;
import org.mozilla.javascript.lens.NetChange;
import org.mozilla.javascript.lens.OldValues;
import org.mozilla.javascript.lens.PropertyChange;
import org.mozilla.javascript.lens.ServiceCall;
import org.mozilla.javascript.lens.ServicePolicy;
import example.app.AuditService;
import example.app.Order;
import example.app.OrderRepository;

/**
 * The code samples in README.md and docs/ are copied from the snippet blocks below
 * (DocSnippetsTest keeps them identical). Each one runs here, using only the public API.
 */
class ExamplesTest {

    private final List<ChangeListener> added = new ArrayList<>();

    @BeforeAll
    static void install() {
        // snippet: install
        Lens.install();
        // end-snippet
    }

    @AfterEach
    void cleanUp() {
        added.forEach(Lens::removeListener);
        Lens.install(LensConfig.defaults());
        Lens.setEnabled(true);
    }

    private void listen(ChangeListener l) {
        Lens.addListener(l);
        added.add(l);
    }

    /** A minimal rule runner, the usual Rhino way. */
    private static Object evaluate(String ruleName, String script, Map<String, Object> objects) {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            objects.forEach((k, v) -> scope.put(k, scope, v));
            cx.setOptimizationLevel(9);
            return cx.evaluateString(scope, script, ruleName, 1, null);
        } finally {
            Context.exit();
        }
    }

    @Test
    void quickStart() {
        Order order = new Order(42);
        OrderRepository orderRepository = new OrderRepository();
        String script = "order.setStatus('PRICED');\norder.setFinalTotal(35.09);\norderRepository.save(order);";

        // snippet: quick-start
        LensResult<Object> result = Lens.observe("pricing-rule", Map.of("recordId", 42),
                () -> evaluate("pricing-rule.js", script, Map.of("order", order, "orderRepository", orderRepository)));

        LensReport report = result.report();
        for (NetChange change : report.netChanges()) {
            System.out.println(change);                 // Order.status "NEW" -> "PRICED"
        }
        System.out.println(report.durationMillis() + " ms, " + report.serviceCalls().size() + " service call(s)");
        String json = report.toJson();                  // store it anywhere
        // end-snippet

        assertThat(report.netChanges()).hasSize(2);
        assertThat(report.serviceCalls()).hasSize(1);
        assertThat(json).contains("\"ruleName\": \"pricing-rule\"");
    }

    @Test
    void sessionMethodIntegration() {
        List<LensReport> reports = new ArrayList<>();
        Order order = new Order(42);
        String ruleName = "discount-rule.js";
        String script = "order.setStatus('DISCOUNTED');";
        Map<String, Object> attributes = Map.of("recordId", order.getId());
        Object result;
        Context ctx = Context.enter();
        try {
            Scriptable scope = ctx.initStandardObjects();
            scope.put("order", scope, order);
            ctx.setOptimizationLevel(9);

            // snippet: session-integration
            LensExecution execution = Lens.execution(ruleName, attributes);
            try {
                result = ctx.evaluateString(scope, script, ruleName, 1, null);
                execution.succeeded();
            } catch (RuntimeException e) {
                execution.failed(e);
                throw e;
            } finally {
                LensReport report = execution.finish();   // never throws
                reports.add(report);                      // or save report.toJson()
            }
            // end-snippet
        } finally {
            Context.exit();
        }
        assertThat(result).isNotNull();
        assertThat(reports.get(0).propertyChanges()).hasSize(1);
        assertThat(reports.get(0).ruleName()).isEqualTo("discount-rule.js");
    }

    @Test
    void scriptApi() {
        Order order = new Order(42);
        AuditService auditService = new AuditService();
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            scope.put("order", scope, order);
            scope.put("auditService", scope, auditService);
            // snippet: script-api-scope
            scope.put("lens", scope, Lens.scriptApi());
            // end-snippet
            String script = String.join("\n",
                    // snippet: script-api-js
                    "order.setStatus('PRICED');",
                    "var r = lens.report();                        // this execution so far",
                    "var summary = r.changeCount() + ' change(s) in ' + r.durationMillis() + ' ms';",
                    "auditService.record(r.toJson());             // one ServiceCall event, no loop",
                    "lens.mark('pricing done');                   // a checkpoint event",
                    "lens.quietly(function () {                   // not observed",
                    "    order.setAuditJson(r.toJson());",
                    "});"
                    // end-snippet
            );
            LensResult<Object> r = Lens.observe("audit-rule", null,
                    () -> cx.evaluateString(scope, script, "audit-rule.js", 1, null));
            assertThat(r.report().serviceCalls()).hasSize(1);
            assertThat(r.report().propertyChanges()).hasSize(1);
            assertThat(order.getAuditJson()).isNotNull();
        } finally {
            Context.exit();
        }
    }

    @Test
    void listener() {
        // snippet: listener
        List<String> lines = new ArrayList<>();
        ChangeListener printer = new ChangeListener() {
            @Override
            public void onEvent(ChangeEvent event) {
                if (event instanceof PropertyChange) {
                    PropertyChange c = (PropertyChange) event;
                    lines.add(c.ruleName() + ":" + c.line() + " " + c.target().label() + "." + c.property()
                            + " " + c.oldValue().text() + " -> " + c.newValue().text());
                }
            }

            @Override
            public void onExecutionFinished(LensReport report) {
                lines.add(report.ruleName() + " took " + report.durationMillis() + " ms");
            }
        };
        Lens.addListener(printer);
        // end-snippet
        added.add(printer);
        Lens.observe("listener-rule", null,
                () -> evaluate("listener-rule.js", "order.setStatus('A');", Map.of("order", new Order(7))));
        assertThat(lines.get(0)).isEqualTo("listener-rule.js:1 Order(id=7).status \"NEW\" -> \"A\"");
        assertThat(lines.get(1)).startsWith("listener-rule took ");
    }

    @Test
    void logging() {
        // snippet: logging
        ChangeListener logger = new JulChangeLogger(Level.INFO);   // logger org.mozilla.javascript.lens.changes
        Lens.addListener(logger);
        // end-snippet
        added.add(logger);
    }

    @Test
    void journalAtCommit() {
        // snippet: journal
        InMemoryJournal journal = new InMemoryJournal();
        Lens.addListener(journal);

        // ... in a transaction, rules run on this thread ...
        evaluate("status-rule.js", "order.setStatus('SHIPPED');", Map.of("order", new Order(42)));

        // at commit (same thread): what did the rules change?
        for (ChangeEvent event : journal.drain()) {
            System.out.println(event.toText());   // [status-rule.js:1] SETTER Order.status "NEW" -> "SHIPPED"
        }
        // end-snippet
        added.add(journal);
        assertThat(journal.drain()).isEmpty();
    }

    @Test
    void configuration() {
        // snippet: configuration
        Lens.install(LensConfig.defaults()
                .oldValues(OldValues.SIMPLE_TYPES)          // don't call getters of entity/collection properties
                .services(ServicePolicy.defaults()
                        .or(ServicePolicy.classNameSuffixes("Gateway")))
                .exclude("com.example.internal.")           // never observe these classes
                .maxEventsPerExecution(5_000)
                .lineNumbersInCompiledMode(true));
        // end-snippet
        assertThat(Lens.config().oldValues()).isEqualTo(OldValues.SIMPLE_TYPES);
    }

    @Test
    void services() {
        AuditService auditService = new AuditService();
        // snippet: register-service
        Lens.registerService(auditService, "auditService");   // always a service, whatever its class
        // end-snippet
        InMemoryJournal journal = new InMemoryJournal();
        listen(journal);
        evaluate("svc.js", "audit.record('x');", Map.of("audit", auditService));
        assertThat(journal.drain()).extracting(e -> ((ServiceCall) e).service()).containsExactly("auditService");
    }

    @Test
    void labels() {
        Order order = new Order(42);
        InMemoryJournal journal = new InMemoryJournal();
        listen(journal);
        // snippet: label
        Lens.label(order, "order");   // paths start at "order" instead of the class name
        // end-snippet
        evaluate("label.js", "order.getItems().get(0).setQuantity(5);", Map.of("order", order));
        assertThat(journal.drain().get(0).target().path()).isEqualTo("order.getItems().get(0)");
    }

    @Test
    void diagnostics() {
        // snippet: diagnostics
        Map<String, Long> failures = Lens.diagnostics().counts();   // step -> count; scripts were not affected
        Lens.diagnostics().recent().forEach(f -> System.out.println(f));
        // end-snippet
        assertThat(failures).isNotNull();
    }

    @Test
    void unobserved() {
        Order order = new Order(42);
        InMemoryJournal journal = new InMemoryJournal();
        listen(journal);
        // snippet: unobserved
        Lens.unobserved(() -> evaluate("maintenance.js", "order.setStatus('ARCHIVED');", Map.of("order", order)));
        // end-snippet
        assertThat(journal.drain()).isEmpty();
        assertThat(order.getStatus()).isEqualTo("ARCHIVED");
    }
}
