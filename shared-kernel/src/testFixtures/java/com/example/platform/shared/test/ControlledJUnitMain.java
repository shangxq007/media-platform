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
            var request=LauncherDiscoveryRequestBuilder.request();
            if ("provider-tests".equals(args[4])) {
                request.selectors(DiscoverySelectors.selectClass("com.example.platform.shared.test.TestDatabaseConnectionProviderTest"));
            } else if ("junit".equals(args[4]) || "pg18-local".equals(args[4])) {
                request.selectors(
                    DiscoverySelectors.selectClass("com.example.platform.migration.UserWorkflowDefinitionV1SchemaMigrationTest"),
                    DiscoverySelectors.selectMethod("com.example.platform.H7V2CanonicalOwnershipInvariantTest#postgresqlServerMajorIs18AndOnlyExactClasspathFlywayV1IsApplied"),
                    DiscoverySelectors.selectClass("com.example.platform.SchemaEquivalenceVerificationTest"),
                    DiscoverySelectors.selectClass("com.example.platform.Gcr5Gcr6DatabaseStructuralIntegrityTest"),
                    DiscoverySelectors.selectClass("com.example.platform.FlywaySchemaIntegrationTest"),
                    DiscoverySelectors.selectClass("com.example.platform.testinfra.TestDatabaseIsolationGuardTest"));
                if ("pg18-local".equals(args[4])) request.selectors(
                    DiscoverySelectors.selectClass("com.example.platform.media.infrastructure.persistence.SourceVisualOwnershipIntegrityIT"),
                    DiscoverySelectors.selectClass("com.example.platform.media.infrastructure.persistence.SourceVisualDescriptionSnapshotIT"),
                    DiscoverySelectors.selectClass("com.example.platform.persistence.binding.JooqBoundGraphInputStoreIT"),
                    DiscoverySelectors.selectClass("com.example.platform.runtime.LocalHostRegistrationRoundTripIT"),
                    DiscoverySelectors.selectClass("com.example.platform.runtime.mediatask.MediaTaskActivityIntegrationTest"),
                    DiscoverySelectors.selectClass("com.example.platform.runtime.mediatask.MediaTaskExecuteTaskIntegrationTest"),
                    DiscoverySelectors.selectClass("com.example.platform.testinfra.TestDatabaseConnectionCapacityGuardTest"),
                    DiscoverySelectors.selectClass("com.example.platform.testinfra.ArtifactCommitRecoveryIT"),
                    DiscoverySelectors.selectClass("com.example.platform.testinfra.GraphRenderLifecycleIT"),
                    DiscoverySelectors.selectClass("com.example.platform.testinfra.FirstRenderPreparationPostgresTest"),
                    DiscoverySelectors.selectClass("com.example.platform.testinfra.PlatformCapabilityExecutionPostgresTest"),
                    DiscoverySelectors.selectClass("com.example.platform.testinfra.CompositionAdmissionCoexistencePostgresTest"),
                    DiscoverySelectors.selectClass("com.example.platform.workerfabric.domain.AtomicAssignmentGrantValidityPostgresTest"),
                    DiscoverySelectors.selectClass("com.example.platform.workerfabric.domain.ArtifactReuseIndexPostgresTest"),
                    DiscoverySelectors.selectClass("com.example.platform.workerfabric.domain.AtomicAssignmentGrantPostgresTest"),
                    DiscoverySelectors.selectClass("com.example.platform.workerfabric.domain.ExecutionBackendSelectionPostgresTest"),
                    DiscoverySelectors.selectClass("com.example.platform.workerfabric.domain.ExecutionLifecyclePostgresTest"),
                    DiscoverySelectors.selectClass("com.example.platform.workerfabric.domain.JooqReservationFeasibilityQueryPostgresTest"),
                    DiscoverySelectors.selectClass("com.example.platform.workerfabric.domain.WorkerFabricRegistrationPostgresTest"));
            } else throw new IllegalArgumentException("JOB_MODE");
            var listener=new SummaryGeneratingListener();
            var launcher=LauncherFactory.create();
            var built=request.build();
            if (!launcher.discover(built).containsTests()) throw new IllegalArgumentException("JOB_TARGETS_NOT_DISCOVERABLE");
            ready(args);
            if ("pg18-local".equals(args[4])) Class.forName("com.example.platform.testinfra.LocalPg18Bootstrap").getMethod("prepare").invoke(null);
            launcher.registerTestExecutionListeners(listener, new org.junit.platform.launcher.TestExecutionListener() {
                @Override public void executionFinished(org.junit.platform.launcher.TestIdentifier id,
                        org.junit.platform.engine.TestExecutionResult result) {
                    if (id.isTest()) System.out.println("CONTROLLED_TEST " + id.getUniqueId()
                            + " " + result.getStatus());
                }
            });launcher.execute(built);
            listener.getSummary().printTo(new java.io.PrintWriter(System.out,true));
            listener.getSummary().printFailuresTo(new java.io.PrintWriter(System.out,true));
            if (listener.getSummary().getTestsFoundCount()==0 || listener.getSummary().getTestsFailedCount()>0
                || listener.getSummary().getContainersFailedCount()>0) System.exit(1);
        } catch (Throwable e) {
            String reason=e instanceof IllegalArgumentException ? e.getMessage() : e.getClass().getSimpleName();
            System.out.println("CONTROLLED_REJECT "+reason);System.exit(2);
        }
    }
}
