package io.github.rhinolens.lens;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.rhinolens.testapp.Customer;
import io.github.rhinolens.testapp.Order;
import io.github.rhinolens.testapp.RuleEngineHarness;

/** What Lens records, in compiled (9) and interpreted (-1) mode, with scripts run like a rule engine. */
class ObservationTest extends LensTestBase {

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void setterCallRecordsOldAndNewValueRuleAndLine(int opt) {
        run(opt, "pricing-rule.js", "var a = 1;\norder.setStatus('PRICED');\n");

        List<PropertyChange> changes = events(PropertyChange.class);
        assertThat(changes).hasSize(1);
        PropertyChange c = changes.get(0);
        assertThat(c.via()).isEqualTo(PropertyChange.Via.SETTER);
        assertThat(c.property()).isEqualTo("status");
        assertThat(c.oldValue().text()).isEqualTo("\"NEW\"");
        assertThat(c.newValue().text()).isEqualTo("\"PRICED\"");
        assertThat(c.ruleName()).isEqualTo("pricing-rule.js");
        assertThat(c.line()).isEqualTo(2);
        assertThat(c.target().entityId()).isEqualTo("42");
        assertThat(c.target().className()).isEqualTo(Order.class.getName());
        assertThat(order.getStatus()).isEqualTo("PRICED");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void assignmentToBeanPropertyIsRecorded(int opt) {
        run(opt, "assign.js", "order.status = 'HOLD';");

        PropertyChange c = events(PropertyChange.class).get(0);
        assertThat(c.via()).isEqualTo(PropertyChange.Via.ASSIGNMENT);
        assertThat(c.property()).isEqualTo("status");
        assertThat(c.oldValue().text()).isEqualTo("\"NEW\"");
        assertThat(c.newValue().text()).isEqualTo("\"HOLD\"");
        assertThat(order.getStatus()).isEqualTo("HOLD");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void assignmentToPublicFieldIsRecorded(int opt) {
        run(opt, "field.js", "order.note = 'rush';");

        PropertyChange c = events(PropertyChange.class).get(0);
        assertThat(c.via()).isEqualTo(PropertyChange.Via.FIELD);
        assertThat(c.property()).isEqualTo("note");
        assertThat(c.oldValue().text()).isEqualTo("null");
        assertThat(c.newValue().text()).isEqualTo("\"rush\"");
        assertThat(order.note).isEqualTo("rush");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void numericSetterAndExactLines(int opt) {
        run(opt, "lines.js", "var x = 10;\n\norder.setFinalTotal(x * 2.5);\n// comment\norder.finalTotal = 30;\n");

        List<PropertyChange> changes = events(PropertyChange.class);
        assertThat(changes).extracting(ChangeEvent::line).containsExactly(3, 5);
        assertThat(changes.get(0).oldValue().text()).isEqualTo("0.0");
        assertThat(changes.get(0).newValue().text()).isEqualTo("25.0");
        assertThat(changes.get(1).newValue().text()).isEqualTo("30.0");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void javaArrayElementIsRecorded(int opt) {
        Lens.label(order, "order");
        run(opt, "array.js", "var tags = order.getTags();\ntags[1] = 'vip';");

        ArrayElementChange c = events(ArrayElementChange.class).get(0);
        assertThat(c.index()).isEqualTo(1);
        assertThat(c.oldValue().text()).isEqualTo("\"web\"");
        assertThat(c.newValue().text()).isEqualTo("\"vip\"");
        assertThat(c.target().path()).isEqualTo("order.getTags()");
        assertThat(c.line()).isEqualTo(2);
        assertThat(order.getTags()[1]).isEqualTo("vip");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void listAddIsRecordedWithSizes(int opt) {
        run(opt, "list.js", "order.getItems().add(order.getItems().get(0));");

        CollectionChange c = events(CollectionChange.class).get(0);
        assertThat(c.method()).isEqualTo("add");
        assertThat(c.sizeBefore()).isEqualTo(2);
        assertThat(c.sizeAfter()).isEqualTo(3);
        assertThat(c.returned().text()).isEqualTo("true");
        assertThat(c.target().path()).isEqualTo("Order.getItems()");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void mapPutReturnsPreviousValue(int opt) {
        run(opt, "map.js", "order.getAttributes().put('channel', 'web');");

        CollectionChange c = events(CollectionChange.class).get(0);
        assertThat(c.method()).isEqualTo("put");
        assertThat(c.args()).extracting(ValueSnapshot::text).containsExactly("\"channel\"", "\"web\"");
        assertThat(c.returned().text()).isEqualTo("\"store\"");
        assertThat(c.sizeBefore()).isEqualTo(1);
        assertThat(c.sizeAfter()).isEqualTo(1);
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void nestedObjectPathIsRecorded(int opt) {
        Lens.label(order, "order");
        run(opt, "nested.js", "order.getItems().get(0).setQuantity(3);");

        PropertyChange c = events(PropertyChange.class).get(0);
        assertThat(c.target().path()).isEqualTo("order.getItems().get(0)");
        assertThat(c.property()).isEqualTo("quantity");
        assertThat(c.oldValue().text()).isEqualTo("2");
        assertThat(c.newValue().text()).isEqualTo("3");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void serviceCallsRecordArgsResultErrorAndDuration(int opt) {
        String result = runToString(opt, "crud.js",
                "repo.save(order);\n"
                        + "var caught = 'none';\n"
                        + "try { repo.findById(-1); } catch (e) { caught = 'caught'; }\n"
                        + "caught;");

        assertThat(result).isEqualTo("caught");
        List<ServiceCall> calls = events(ServiceCall.class);
        assertThat(calls).hasSize(2);
        ServiceCall save = calls.get(0);
        assertThat(save.service()).isEqualTo("orderRepository");
        assertThat(save.method()).isEqualTo("save");
        assertThat(save.args()).extracting(ValueSnapshot::text).containsExactly("Order(id=42)");
        assertThat(save.result().text()).isEqualTo("Order(id=42)");
        assertThat(save.error()).isNull();
        assertThat(save.durationNanos()).isPositive();
        assertThat(save.line()).isEqualTo(1);
        ServiceCall find = calls.get(1);
        assertThat(find.error()).contains("no order -1");
        assertThat(find.result()).isNull();
        assertThat(find.line()).isEqualTo(3);
        assertThat(repo.saved).containsExactly(order);
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void registeredServicesAreRecorded(int opt) {
        Lens.registerService(audit, "auditTrail");
        run(opt, "audit.js", "audit.record('x');");
        assertThat(events(ServiceCall.class)).extracting(ServiceCall::service).containsExactly("auditTrail");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void readsAndPlainCallsCreateNoEvents(int opt) {
        run(opt, "reads.js", "var s = order.status + order.getFinalTotal() + order.getItems().size(); s;");
        assertThat(journal.peek()).isEmpty();
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void sharedLibraryScopeLikeRuleEnginesUse(int opt) {
        if (engine != null) {
            engine.close();
        }
        engine = new RuleEngineHarness(opt, true);
        engine.run("library-rule.js", "order.setFinalTotal(money(10.456));", scope());
        PropertyChange c = events(PropertyChange.class).get(0);
        assertThat(c.newValue().text()).isEqualTo("10.46");
        assertThat(c.ruleName()).isEqualTo("library-rule.js");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void contextReusedAcrossRulesOnTheSameThread(int opt) {
        RuleEngineHarness e = engine(opt);
        e.run("rule-1.js", "order.setStatus('A');", scope());
        e.run("rule-2.js", "\norder.setStatus('B');", scope());
        assertThat(events(PropertyChange.class)).extracting(ChangeEvent::ruleName, ChangeEvent::line)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("rule-1.js", 1),
                        org.assertj.core.groups.Tuple.tuple("rule-2.js", 2));
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void executionIdAndAttributesOnEvents(int opt) {
        LensResult<Object> r = Lens.observe("pricing", Map.of("recordId", 42),
                () -> engine(opt).run("pricing.js", "order.setStatus('X');", scope()));
        PropertyChange c = events(PropertyChange.class).get(0);
        assertThat(c.executionId()).isEqualTo(r.report().executionId());
        assertThat(c.attributes()).containsEntry("recordId", 42);
        assertThat(c.sequence()).isZero();
        assertThat(c.offsetNanos()).isPositive();
        assertThat(r.report().ruleName()).isEqualTo("pricing");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void disabledMeansNoEvents(int opt) {
        Lens.setEnabled(false);
        run(opt, "off.js", "order.setStatus('X'); order.note = 'n'; repo.save(order);");
        assertThat(journal.peek()).isEmpty();
        assertThat(order.getStatus()).isEqualTo("X");
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void simpleTypesModeDoesNotCallEntityGetters(int opt) {
        Lens.install(LensConfig.defaults().oldValues(OldValues.SIMPLE_TYPES));
        Customer bob = new Customer(7, "Bob");
        Map<String, Object> s = scope();
        s.put("bob", bob);
        engine(opt).run("customer.js", "order.setCustomer(bob);\norder.setStatus('Y');", s);

        List<PropertyChange> changes = events(PropertyChange.class);
        assertThat(Order.customerGetterCalls).isZero();
        assertThat(changes.get(0).property()).isEqualTo("customer");
        assertThat(changes.get(0).oldValue().isCaptured()).isFalse();
        assertThat(changes.get(0).newValue().text()).isEqualTo("Customer(id=7)");
        assertThat(changes.get(1).oldValue().text()).isEqualTo("\"NEW\""); // simple types still read
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void compiledModeLineNumbersCanBeTurnedOff(int opt) {
        Lens.install(LensConfig.defaults().lineNumbersInCompiledMode(false));
        LensResult<Object> r = Lens.observe("named-rule", null,
                () -> engine(opt).run("rule.js", "\norder.setStatus('A');", scope()));

        PropertyChange c = events(PropertyChange.class).get(0);
        assertThat(c.newValue().text()).isEqualTo("\"A\"");
        assertThat(r.report().ruleName()).isEqualTo("named-rule");
        if (opt == -1) { // interpreted mode always knows the position, cheaply
            assertThat(c.ruleName()).isEqualTo("rule.js");
            assertThat(c.line()).isEqualTo(2);
        } else {
            assertThat(c.ruleName()).isNull();
            assertThat(c.line()).isEqualTo(-1);
        }
    }

    @ParameterizedTest(name = "optimization {0}")
    @ValueSource(ints = { 9, -1 })
    void alwaysModeReadsEntityGetters(int opt) {
        Map<String, Object> s = scope();
        s.put("bob", new Customer(7, "Bob"));
        engine(opt).run("customer.js", "order.setCustomer(bob);", s);

        PropertyChange c = events(PropertyChange.class).get(0);
        assertThat(Order.customerGetterCalls).isEqualTo(2); // before and after
        assertThat(c.oldValue().text()).isEqualTo("Customer(id=1)");
        assertThat(c.newValue().text()).isEqualTo("Customer(id=7)");
    }
}
