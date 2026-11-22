package org.mozilla.javascript.lens;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import example.app.RuleEngineHarness;

/**
 * Informational: per-operation cost of Lens, compiled and interpreted. Prints a table and writes
 * it to target/lens-performance.md (source of the numbers in docs/performance.md). No assertions.
 */
class PerformanceTest extends LensTestBase {

    private static final int N = 20_000;
    private static final String SETTERS = "for (var i = 0; i < " + N + "; i++) { order.setFinalTotal(i); }";
    private static final String READS = "var s = 0; for (var i = 0; i < " + N + "; i++) { s += order.getFinalTotal(); } s;";

    @Test
    void measure() throws IOException {
        Dispatcher.clear(); // no listeners: measure Lens itself
        StringBuilder table = new StringBuilder();
        table.append("| Mode | Operation | Plain Rhino | Lens on | Lens on, inside an execution | Lens on, compiled-mode line numbers off |\n");
        table.append("|---|---|---|---|---|---|\n");
        for (int opt : new int[] { 9, -1 }) {
            for (String[] op : new String[][] { { "setter call", SETTERS }, { "getter call (no event)", READS } }) {
                Lens.install(LensConfig.defaults().maxEventsPerExecution(Integer.MAX_VALUE));
                double plain = perOp(opt, op[1], false, false);
                double on = perOp(opt, op[1], true, false);
                double inExecution = perOp(opt, op[1], true, true);
                Lens.install(LensConfig.defaults().maxEventsPerExecution(Integer.MAX_VALUE).lineNumbersInCompiledMode(false));
                double noLines = perOp(opt, op[1], true, false);
                table.append(String.format(Locale.ROOT, "| %s | %s | %.2f µs | %.2f µs | %.2f µs | %.2f µs |%n",
                        opt == -1 ? "interpreted (-1)" : "compiled (" + opt + ")", op[0], plain, on, inExecution, noLines));
            }
        }
        String out = "Per operation, " + N + " operations per run, best of 5 runs after warm-up, "
                + System.getProperty("java.vm.name") + " " + System.getProperty("java.version") + ", "
                + System.getProperty("os.name") + " " + System.getProperty("os.arch") + ".\n\n" + table;
        System.out.println(out);
        Path target = Paths.get("target", "lens-performance.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, out);
    }

    private double perOp(int opt, String script, boolean lensOn, boolean inExecution) {
        Lens.setEnabled(lensOn);
        try (RuleEngineHarness e = new RuleEngineHarness(opt, false)) {
            for (int i = 0; i < 3; i++) {
                timed(e, script, inExecution); // warm-up
            }
            long best = Long.MAX_VALUE;
            for (int i = 0; i < 5; i++) {
                best = Math.min(best, timed(e, script, inExecution));
            }
            return best / 1000.0 / N;
        } finally {
            Lens.setEnabled(true);
        }
    }

    private long timed(RuleEngineHarness e, String script, boolean inExecution) {
        long start = System.nanoTime();
        if (inExecution) {
            Lens.observe("perf", null, () -> e.run("perf.js", script, scope()));
        } else {
            e.run("perf.js", script, scope());
        }
        return System.nanoTime() - start;
    }
}
