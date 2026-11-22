package io.github.rhinolens.lens;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;

/**
 * Entry point of batik-js-lens: opt-in observability for what Rhino scripts do to Java objects.
 *
 * <p>Call {@link #install()} once at startup. From then on every Context Rhino creates (including
 * through plain {@code Context.enter()}) records, for each script operation on a Java object:
 * which script and line, which object, which property, old and new value; changes to Java
 * collections and arrays; and calls to services. Nothing else in the calling code has to change.
 * Optionally, name each evaluation with {@link #execution} or {@link #observe} to get a
 * {@link LensReport} with timing and every event.
 *
 * <p>Lens never breaks a script: all observation code is failure-safe, and failures are flagged in
 * {@link #diagnostics()}. No Rhino classes are changed; Lens extends Rhino's own wrappers.
 */
public final class Lens {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile boolean enabled = true;
    private static volatile LensConfig config = LensConfig.defaults();
    private static volatile boolean globalFactory;

    private static final WeakIdentityMap<String> SERVICES = new WeakIdentityMap<>();
    private static final WeakIdentityMap<String> LABELS = new WeakIdentityMap<>();
    private static final LensScriptApi SCRIPT_API = new LensScriptApi();

    private Lens() {
    }

    // ------------------------------------------------------------------ installing

    /** Installs with {@link LensConfig#defaults()}. Same as {@code install(LensConfig.defaults())}. */
    public static void install() {
        install(LensConfig.defaults());
    }

    /**
     * Installs Lens (once per JVM; later calls only replace the configuration): adds a listener to
     * Rhino's global {@link ContextFactory} that instruments each new Context. Never throws.
     * Contexts that already exist are not affected; see {@link #instrument(Context)}.
     */
    public static void install(LensConfig newConfig) {
        LensSafe.run("install", () -> {
            if (newConfig != null) {
                config = newConfig;
            }
            if (INSTALLED.compareAndSet(false, true)) {
                ContextFactory.getGlobal().addListener(new LensContextListener());
            }
        });
    }

    /**
     * Like {@link #install(LensConfig)}, but also makes Lens Rhino's global ContextFactory
     * ({@link ContextFactory#initGlobal}), so each top-level evaluation becomes an automatic
     * execution and {@link #lastReport()} works with no change to calling code. Rhino allows one
     * global factory per JVM; if another is already set, this falls back to {@link #install} (and
     * flags it). Call it before any script runs.
     */
    public static void installAsGlobalFactory(LensConfig newConfig) {
        LensSafe.run("install-global-factory", () -> {
            if (newConfig != null) {
                config = newConfig;
            }
            if (INSTALLED.compareAndSet(false, true)) {
                try {
                    ContextFactory.initGlobal(new LensContextFactory());
                    globalFactory = true;
                } catch (IllegalStateException alreadySet) {
                    ContextFactory.getGlobal().addListener(new LensContextListener());
                    LensDiagnostics.flag("install-global-factory",
                            new IllegalStateException("a global ContextFactory was already set; using a listener", alreadySet));
                }
            }
        });
    }

    /**
     * Instruments an existing Context (e.g. one created before {@link #install()} and reused from
     * a thread-local). Keeps its optimization level. Never throws.
     */
    public static void instrument(Context cx) {
        LensSafe.run("context-setup", () -> {
            if (!(cx.getWrapFactory() instanceof LensWrapFactory)) {
                cx.setWrapFactory(new LensWrapFactory());
            }
            // Puts the script name into compiled classes so events can name the rule.
            int level = cx.getOptimizationLevel();
            cx.setGeneratingDebug(true);
            cx.setOptimizationLevel(level);
        });
    }

    public static boolean isInstalled() {
        return INSTALLED.get();
    }

    /** True if {@link #installAsGlobalFactory} made Lens Rhino's global factory. */
    public static boolean isGlobalFactory() {
        return globalFactory;
    }

    /** Turns observation on or off at runtime (on by default). Off means plain Rhino behaviour. */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static LensConfig config() {
        return config;
    }

    // ------------------------------------------------------------------ listeners

    /** Adds a listener for every event and every finished execution. */
    public static void addListener(ChangeListener listener) {
        LensSafe.run("add-listener", () -> Dispatcher.add(listener));
    }

    public static void removeListener(ChangeListener listener) {
        LensSafe.run("remove-listener", () -> Dispatcher.remove(listener));
    }

    // ------------------------------------------------------------------ executions and reports

    /**
     * Opens an execution on this thread; everything observed until {@link LensExecution#finish()}
     * joins it. {@code ruleName} may be null (the script name from the first event is used).
     */
    public static LensExecution execution(String ruleName, Map<String, ?> attributes) {
        LensExecution x = new LensExecution(ruleName, attributes);
        ExecutionTracker.push(x);
        return x;
    }

    /**
     * Runs {@code work} (typically {@code () -> ctx.evaluateString(...)}) as one execution and
     * returns its value with the report. If {@code work} throws, the execution is marked failed,
     * its report becomes {@link #lastReport()}, and the exception is rethrown unchanged.
     */
    public static <T> LensResult<T> observe(String ruleName, Map<String, ?> attributes, Supplier<T> work) {
        LensExecution x = execution(ruleName, attributes);
        T value;
        try {
            value = work.get();
            x.succeeded();
        } catch (RuntimeException | Error e) {
            x.failed(e);
            x.finish();
            throw e;
        }
        return new LensResult<>(value, x.finish());
    }

    /** The open execution on this thread, or {@code null}. */
    public static LensExecution currentExecution() {
        return ExecutionTracker.current();
    }

    /** The report of the last execution finished on this thread, or {@code null}. */
    public static LensReport lastReport() {
        return ExecutionTracker.last();
    }

    // ------------------------------------------------------------------ quiet blocks

    /** Runs {@code work} without observing anything scripts do meanwhile (counted as suppressed). */
    public static void unobserved(Runnable work) {
        Guard.enter();
        try {
            work.run();
        } finally {
            Guard.exit();
        }
    }

    /** Same as {@link #unobserved(Runnable)}, returning {@code work}'s value. */
    public static <T> T unobserved(Supplier<T> work) {
        Guard.enter();
        try {
            return work.get();
        } finally {
            Guard.exit();
        }
    }

    // ------------------------------------------------------------------ services, labels, script API

    /** Treats {@code service} as a service named {@code name}, whatever the {@link ServicePolicy} says. */
    public static void registerService(Object service, String name) {
        LensSafe.run("register-service", () -> SERVICES.put(service, name));
    }

    /**
     * Names an object so event paths start there, e.g. {@code Lens.label(order, "order")} gives
     * {@code order.getItems().get(0)} instead of {@code Order.getItems().get(0)}.
     */
    public static void label(Object object, String name) {
        LensSafe.run("label", () -> LABELS.put(object, name));
    }

    /**
     * The object to put in a script's scope as {@code lens}:
     * {@code scope.put("lens", scope, Lens.scriptApi())}. See {@link LensScriptApi}.
     */
    public static LensScriptApi scriptApi() {
        return SCRIPT_API;
    }

    /** Failures inside Lens's observation code (scripts are never affected by them). */
    public static LensDiagnostics diagnostics() {
        return LensDiagnostics.instance();
    }

    // ------------------------------------------------------------------ internal

    /** Lens wrappers are being handed out (installed and enabled). */
    static boolean wrapping() {
        return INSTALLED.get() && enabled;
    }

    /** Operations are being observed right now on this thread (wrapping and not inside the guard). */
    static boolean observing() {
        return INSTALLED.get() && enabled && !Guard.active();
    }

    static String registeredServiceName(Object o) {
        return LensSafe.call("registered-service", () -> SERVICES.get(o), null);
    }

    static String labelOf(Object o) {
        return LensSafe.call("label-lookup", () -> LABELS.get(o), null);
    }

    /** Tests only: back to defaults (the Rhino listener stays installed). */
    static void resetForTests() {
        config = LensConfig.defaults();
        enabled = true;
        Dispatcher.clear();
        SERVICES.clear();
        LABELS.clear();
        LensDiagnostics.instance().reset();
        TestHooks.disarmAll();
    }
}
