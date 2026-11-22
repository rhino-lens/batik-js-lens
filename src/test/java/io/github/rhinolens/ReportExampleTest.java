package io.github.rhinolens;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Generates docs/examples/report-example.json from a real run (ids, times, hashes and durations
 * replaced by fixed values so the file is stable). Fails if the file is out of date; refresh with
 * {@code -Dlens.updateDocs=true}.
 */
@Order(1)
class ReportExampleTest extends LensTestBase {

    static final String RULE = String.join("\n",
            "order.setStatus('PRICED');",
            "order.finalTotal = 35.09;",
            "order.getItems().add(order.getItems().get(0));",
            "repo.save(order);",
            "lens.mark('pricing done');");

    @Test
    void exampleIsUpToDate() throws Exception {
        Lens.label(order, "order");
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("recordId", 42);
        attributes.put("user", "ann");
        LensResult<Object> r = Lens.observe("pricing-rule", attributes,
                () -> engine(9).run("pricing-rule.js", RULE, scope()));

        String json = Json.write(normalize(r.report().toMap(), new HashMap<>(), new int[1]));
        Path file = Paths.get("docs", "examples", "report-example.json");
        if (DocSnippetsTest.updating()) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json + "\n");
        }
        assertThat(Files.readString(file).stripTrailing()).isEqualTo(json);
    }

    private static final String[] FIXED_TIMES = { "2026-11-22T09:30:00.000100Z", "2026-11-22T09:30:00.000900Z",
            "2026-11-22T09:30:00.001400Z", "2026-11-22T09:30:00.002100Z", "2026-11-22T09:30:00.003600Z",
            "2026-11-22T09:30:00.004000Z", "2026-11-22T09:30:00.004300Z" };

    @SuppressWarnings("unchecked")
    private static Object normalize(Object value, Map<Object, String> hashes, int[] counter) {
        if (value instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                switch (k) {
                    case "executionId": v = "3f2b9c1e-7d4a-4e8b-9a6f-2c1d5e8f0a47"; break;
                    case "startedAt": v = "2026-11-22T09:30:00Z"; break;
                    case "endedAt": v = "2026-11-22T09:30:00.004800Z"; break;
                    case "time": v = FIXED_TIMES[Math.min(counter[0]++, FIXED_TIMES.length - 1)]; break;
                    case "thread": v = "main"; break;
                    case "durationNanos": v = v instanceof Long && out.containsKey("service") ? 1_200_000L : 4_800_000L; break;
                    case "offsetNanos": v = 100_000L + 700_000L * (Long) out.getOrDefault("sequence", 0L); break;
                    case "serviceCallNanos": v = 1_200_000L; break;
                    case "overheadNanos": v = 61_000L; break;
                    case "identityHash": v = hashes.computeIfAbsent(v, h -> List.of("1b6d3586", "4554617c", "74a14482")
                            .get(Math.min(hashes.size(), 2))); break;
                    case "label":
                        if (String.valueOf(v).contains("@") && out.get("identityHash") != null) {
                            String text = String.valueOf(v);
                            v = text.substring(0, text.indexOf('@') + 1) + out.get("identityHash");
                        }
                        break;
                    default: v = normalize(v, hashes, counter);
                }
                out.put(k, v);
            }
            if (out.containsKey("durationNanos") && out.containsKey("service")) {
                out.put("durationNanos", 1_200_000L);
            }
            return out;
        }
        if (value instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object o : (List<Object>) value) {
                out.add(normalize(o, hashes, counter));
            }
            return out;
        }
        return value;
    }
}
