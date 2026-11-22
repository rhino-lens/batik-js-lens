package io.github.rhinolens;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.rhinolens.testapp.Order;

/** Fixes from the pre-release review: journal capacity, identity, JSON, locale, listeners. */
class ReviewFixesTest extends LensTestBase {

    @Test
    void fullJournalDropsOldestInConstantTime() {
        InMemoryJournal small = new InMemoryJournal(1_000);
        long t0 = System.nanoTime();
        for (int i = 0; i < 200_000; i++) {
            small.onEvent(new Mark(ScriptPosition.UNKNOWN, "m" + i));
        }
        long millis = (System.nanoTime() - t0) / 1_000_000;

        List<ChangeEvent> kept = small.drain();
        assertThat(kept).hasSize(1_000);
        assertThat(((Mark) kept.get(0)).label()).isEqualTo("m199000");
        assertThat(((Mark) kept.get(999)).label()).isEqualTo("m199999");
        // An ArrayList shifting 1,000 entries per event took seconds here; a deque takes milliseconds.
        assertThat(millis).isLessThan(2_000);
    }

    @Test
    void targetsAreComparedByIdentityNotJustHash() {
        Order a = new Order(1);
        Order b = new Order(2);
        // Same class and same (forced) hash: only identity tells them apart.
        TargetRef refA = new TargetRef(Order.class.getName(), 7, null, null, a);
        TargetRef refB = new TargetRef(Order.class.getName(), 7, null, null, b);
        TargetRef refA2 = new TargetRef(Order.class.getName(), 7, null, null, a);

        assertThat(refA.sameObjectAs(refB)).isFalse();
        assertThat(refA.sameObjectAs(refA2)).isTrue();
        assertThat(refA.refersTo(a)).isTrue();
        assertThat(refA.refersTo(b)).isFalse();
    }

    @Test
    void netChangesKeepTwoObjectsOfTheSameClassApart() {
        Order other = new Order(43);
        Map<String, Object> s = scope();
        s.put("other", other);
        LensResult<Object> r = Lens.observe("two.js", null, () -> engine(9).run("two.js",
                "order.setStatus('A');\nother.setStatus('B');\norder.setStatus('C');", s));

        List<NetChange> net = r.report().netChanges();
        assertThat(net).hasSize(2);
        assertThat(net.get(0).newValue().text()).isEqualTo("\"C\"");
        assertThat(net.get(0).changes()).isEqualTo(2);
        assertThat(net.get(1).newValue().text()).isEqualTo("\"B\"");
        assertThat(r.report().changesTo(other)).hasSize(1);
    }

    @Test
    void jsonStaysValidWithNonFiniteNumbers() throws Exception {
        LensExecution x = Lens.execution("nan", Map.of("ratio", Double.NaN, "limit", Double.POSITIVE_INFINITY));
        String json = x.finish().toJson();

        JsonNode node = new ObjectMapper().readTree(json); // throws if the JSON is invalid
        assertThat(node.path("attributes").path("ratio").asText()).isEqualTo("NaN");
        assertThat(node.path("attributes").path("limit").asText()).isEqualTo("Infinity");
    }

    @Test
    void textReportIsTheSameInEveryLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY); // decimal comma
            LensResult<Object> r = Lens.observe("locale.js", null,
                    () -> engine(9).run("locale.js", "repo.save(order);", scope()));
            String text = r.report().toText();
            assertThat(text).containsPattern("in \\d+\\.\\d{3} ms");
            assertThat(text).doesNotContainPattern("\\d+,\\d{3} ms");
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void elapsedMillisFromAScript() {
        Lens.start("elapsed.js");
        String result = engine(9).runToString("elapsed.js", "lens.elapsedMillis() > 0", scope());
        Lens.finish();
        assertThat(result).isEqualTo("true");
    }

    @Test
    void aListenerAddedTwiceIsCalledOnceAndCanBeRemoved() {
        int[] calls = { 0 };
        ChangeListener counter = e -> calls[0]++;
        Lens.addListener(counter);
        Lens.addListener(counter);
        engine(9).run("listen.js", "order.setStatus('A');", scope());
        assertThat(calls[0]).isEqualTo(1);

        Lens.removeListener(counter);
        engine(9).run("listen.js", "order.setStatus('B');", scope());
        assertThat(calls[0]).isEqualTo(1);
    }
}
