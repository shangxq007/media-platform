package com.example.platform.shared.test;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Immutable entry: no class name, executable, selector or descriptor path from caller input. */
public final class ControlledJUnitMain {
    private static java.util.List<String> postgresTargets(boolean local) throws java.io.IOException {
        try (var in = ControlledJUnitMain.class.getResourceAsStream("postgres-targets.txt")) {
            if (in == null) throw new IllegalStateException("MISSING_POSTGRES_TARGETS");
            var lines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines();
            return lines.filter(line -> local || line.startsWith("core "))
                    .map(line -> line.substring(line.indexOf(' ') + 1)).toList();
        }
    }
    private static void ready(String[] args) {
        System.out.println("CONTROLLED_READY "+args[0]+" "+args[1]+" "+args[2]+" CLOSED_SPEC_SECRET");
    }
    public static void main(String[] args) {

        try {
            if (args.length!=7 || !java.util.Set.of("probe","provider-tests","junit","pg18-local").contains(args[4])) throw new IllegalArgumentException("JOB_ARGUMENTS");
            ControlledLaunch.initialize(args);
            System.err.println("CONTROLLED_STDERR_READY");
            TestDatabaseConnectionProvider p=TestDatabaseConnectionProvider.fromControlledLaunch();
            if ("probe".equals(args[4])) {
                ready(args);
                System.out.println("PROVIDER_CONSUMED "+p.jdbcUrl()+" role="+p.username()+" run="+p.runId());
                System.out.println("CHILD_ENV_KEYS "+String.join(",",new java.util.TreeSet<>(System.getenv().keySet())));
                return;
            }
            String selected = null;
            var request=LauncherDiscoveryRequestBuilder.request();
            if ("provider-tests".equals(args[4])) {
                request.selectors(DiscoverySelectors.selectClass("com.example.platform.shared.test.TestDatabaseConnectionProviderTest"));
            } else if ("junit".equals(args[4]) || "pg18-local".equals(args[4])) {
                var targets = postgresTargets("pg18-local".equals(args[4]));
                // A supervisor-owned PostgreSQL job gets one receipt-bound database and login.
                // All shards together execute the same immutable selection as the full suite.
                if (p.isIsolatedLocalResource() && p.runId().equals("pgsuite")) {
                    int slot = Integer.parseInt(p.attemptId().substring(1));
                    if (!p.attemptId().equals("s" + slot) || slot < 0 || slot >= targets.size())
                        throw new IllegalArgumentException("UNKNOWN_SUITE_SHARD");
                    selected = targets.get(slot);
                    targets = java.util.List.of(selected);
                }
                for (String target : targets) {
                    request.selectors(target.contains("#") ? DiscoverySelectors.selectMethod(target)
                            : DiscoverySelectors.selectClass(target));
                }
            } else throw new IllegalArgumentException("JOB_MODE");
            var listener=new SummaryGeneratingListener();
            var launcher=LauncherFactory.create();
            var built=request.build();
            if (!launcher.discover(built).containsTests()) throw new IllegalArgumentException("JOB_TARGETS_NOT_DISCOVERABLE");
            ready(args);
            if (selected != null) System.out.println("PG18_SUITE_SHARD " + p.attemptId().substring(1) + " " + selected);
            if ("pg18-local".equals(args[4])) {
                var bootstrap = Class.forName("com.example.platform.testinfra.LocalPg18Bootstrap");
                if (selected == null || selected.endsWith(".FlywaySchemaIntegrationTest")) {
                    bootstrap.getMethod("prepare").invoke(null);
                } else if (!selected.endsWith(".SchemaEquivalenceVerificationTest") &&
                           !selected.endsWith(".UserWorkflowDefinitionV1SchemaMigrationTest") &&
                           !selected.endsWith(".SourceVisualDescriptionSnapshotIT")) {
                    bootstrap.getMethod("migrate").invoke(null);
                }
            }
            launcher.registerTestExecutionListeners(listener, new org.junit.platform.launcher.TestExecutionListener() {
                @Override public void executionFinished(org.junit.platform.launcher.TestIdentifier id,
                        org.junit.platform.engine.TestExecutionResult result) {
                    if (id.isTest()) System.out.println("CONTROLLED_TEST " + id.getUniqueId()
                            + " " + result.getStatus());
                }
            });launcher.execute(built);
            listener.getSummary().printTo(new java.io.PrintWriter(System.out,true));
            listener.getSummary().printFailuresTo(new java.io.PrintWriter(System.out,true));
            var summary = listener.getSummary();
            boolean failed = summary.getTestsFoundCount() == 0 || summary.getTestsFailedCount() > 0
                    || summary.getContainersFailedCount() > 0;
            // Spring-managed schedulers can outlive the last test. Run the JVM shutdown hooks
            // after recording the complete JUnit outcome, then let the supervisor reap the job.
            System.exit(failed ? 1 : 0);
        } catch (Throwable e) {
            String reason=e instanceof IllegalArgumentException ? e.getMessage() : e.getClass().getSimpleName();
            System.out.println("CONTROLLED_REJECT "+reason);System.exit(2);
        }
    }
}
