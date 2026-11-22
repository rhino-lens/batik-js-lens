package io.github.rhinolens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import io.github.rhinolens.testapp.AuditService;
import io.github.rhinolens.testapp.Order;
import io.github.rhinolens.testapp.OrderRepository;
import io.github.rhinolens.testapp.RuleEngineHarness;

/** Shared setup: Lens installed once, a fresh journal and model per test, scripts run like a rule engine. */
abstract class LensTestBase {

    protected final InMemoryJournal journal = new InMemoryJournal();
    protected RuleEngineHarness engine;
    protected Order order;
    protected OrderRepository repo;
    protected AuditService audit;

    @BeforeAll
    static void installLens() {
        Lens.install();
    }

    @BeforeEach
    void setUpLens() {
        Lens.resetForTests();
        Lens.addListener(journal);
        journal.clear();
        order = new Order(42);
        repo = new OrderRepository();
        audit = new AuditService();
        Order.customerGetterCalls = 0;
    }

    @AfterEach
    void tearDownLens() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
        Lens.resetForTests();
        journal.clear();
    }

    protected RuleEngineHarness engine(int optimizationLevel) {
        if (engine != null) {
            engine.close();
        }
        engine = new RuleEngineHarness(optimizationLevel, false);
        return engine;
    }

    protected Map<String, Object> scope() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("order", order);
        s.put("repo", repo);
        s.put("audit", audit);
        s.put("lens", Lens.scriptApi());
        return s;
    }

    protected Object run(int optimizationLevel, String ruleName, String script) {
        return engine(optimizationLevel).run(ruleName, script, scope());
    }

    protected String runToString(int optimizationLevel, String ruleName, String script) {
        return engine(optimizationLevel).runToString(ruleName, script, scope());
    }

    protected <T extends ChangeEvent> List<T> events(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (ChangeEvent e : journal.peek()) {
            if (type.isInstance(e)) {
                out.add(type.cast(e));
            }
        }
        return out;
    }
}
