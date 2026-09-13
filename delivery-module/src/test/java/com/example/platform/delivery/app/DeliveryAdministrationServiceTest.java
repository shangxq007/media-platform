package com.example.platform.delivery.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.delivery.api.dto.*;
import com.example.platform.secrets.api.port.*;
import com.example.platform.shared.authorization.*;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.zaxxer.hikari.HikariDataSource;
import java.util.*;
import org.jooq.*;
import org.jooq.conf.*;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.server.ResponseStatusException;

class DeliveryAdministrationServiceTest extends PostgresTestContainerSupport {
    static final String SCHEMA = isolatedSchemaName();
    static javax.sql.DataSource dataSource;
    static DSLContext dsl;
    DeliveryAdministrationService service;
    SecretResolver secrets;
    SecretRefRegistryPort registry;
    DeliveryJobService jobs;
    DeliveryAccess access;
    DeliveryDestinationCredentialService credentials;
    CanonicalActor actor;
    Set<String> deniedProjects;
    Map<String, Map<String, String>> vault;

    @BeforeAll static void database() {
        dataSource = createDataSource();
        dsl = DSL.using(new TransactionAwareDataSourceProxy(dataSource), SQLDialect.POSTGRES,
                new Settings().withRenderMapping(new RenderMapping().withSchemata(
                        new MappedSchema().withInput("public").withOutput(SCHEMA))));
        DeliveryTestSchema.migrate(jdbcUrl(),username(),password(),SCHEMA);

    }

    @AfterAll static void close() { dsl.execute("drop schema " + SCHEMA + " cascade"); closeDataSource(dataSource); }

    @BeforeEach void setup() {
        dsl.execute("truncate " + SCHEMA + ".delivery_job, " + SCHEMA + ".delivery_policy, " + SCHEMA + ".delivery_destination");
        actor = CanonicalActor.user("user-a", "tenant-a", Set.of("EDITOR"), "test");
        deniedProjects = new HashSet<>();
        access = new DeliveryAccess(() -> Optional.ofNullable(actor), request ->
                deniedProjects.contains(request.resource().projectId())
                        ? AuthorizationDecision.deny("RBAC_DENY", "RBAC", "Denied") : AuthorizationDecision.allow("RBAC"),
                (tenant, project) -> tenant.equals("tenant-a") && project.startsWith("project-a"));
        vault = new HashMap<>();
        secrets = mock(SecretResolver.class);
        registry = mock(SecretRefRegistryPort.class);
        SecretsConfigPort config = mock(SecretsConfigPort.class);
        when(config.vaultEnabled()).thenReturn(true);
        when(secrets.storeCredentialMap(anyString(), anyString(), anyMap())).thenAnswer(call -> {
            String ref = "vault:platform/" + call.getArgument(0) + "/" + call.getArgument(1);
            vault.put(ref, Map.copyOf(call.getArgument(2)));
            return ref;
        });
        doAnswer(call -> { vault.remove(call.getArgument(0)); return null; }).when(secrets).deleteByRef(anyString());
        jobs = mock(DeliveryJobService.class);
        credentials = new DeliveryDestinationCredentialService(secrets, registry, config);
        var target = new DeliveryAdministrationService(dsl, jobs,
                credentials, mock(CredentialBundlePort.class), access);
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        service = (DeliveryAdministrationService) factory.getProxy();
    }

    CreateDeliveryDestinationRequest destination(String name) {
        return new CreateDeliveryDestinationRequest(name, "SFTP", Map.of(), null, Map.of("password", "test-only"), true);
    }
    CreateDeliveryPolicyRequest policy(String id) { return new CreateDeliveryPolicyRequest(id, null, null, null); }
    int destinations() { return dsl.fetchCount(DSL.table(SCHEMA + ".delivery_destination")); }

    @Test void authorizedManagementAndPolicyLifecycle() {
        var dest = service.createDestination("tenant-a", destination("one"));
        assertEquals(1, destinations());
        assertEquals(1, service.listDestinations("tenant-a").size());
        String id = service.createPolicy("tenant-a", "project-a", policy(dest.id()));
        assertEquals(1, service.listPolicies("tenant-a", "project-a").size());
        service.updatePolicyEnabled("tenant-a", "project-a", id, false);
        assertThrows(IllegalArgumentException.class, () -> service.updatePolicyEnabled("tenant-a", "project-a2", id, true));
        assertThrows(IllegalStateException.class, () -> service.deleteDestination("tenant-a", dest.id()));
        service.deletePolicy("tenant-a", "project-a", id);
        service.deleteDestination("tenant-a", dest.id());
        assertEquals(0, destinations());
        assertTrue(vault.isEmpty());
    }

    @Test void missingActorCannotReadOrWrite() {
        actor = null;
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.listDestinations("tenant-a")).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.createDestination("tenant-a", destination("one")));
        verifyNoInteractions(secrets);
    }

    @Test void crossTenantAndProjectDeniedBeforePersistence() {
        assertThrows(ResponseStatusException.class, () -> service.createDestination("tenant-b", destination("one")));
        deniedProjects.add("project-a2");
        assertThrows(ResponseStatusException.class, () -> service.createPolicy("tenant-a", "project-a2", policy("d")));
        assertThrows(ResponseStatusException.class, () -> service.listPolicies("tenant-a", "foreign-project"));
        assertEquals(0, destinations());
        verifyNoInteractions(secrets);
    }

    @Test void destinationMustBelongToAuthorizedTenant() {
        dsl.execute("insert into " + SCHEMA + ".delivery_destination(id,tenant_id,name,protocol,created_at) values ('foreign','tenant-b','foreign','SFTP',current_timestamp)");
        assertThrows(IllegalArgumentException.class, () -> service.createPolicy("tenant-a", "project-a", policy("foreign")));
        assertThrows(IllegalArgumentException.class, () -> service.updateDestination("tenant-a", "foreign", new UpdateDeliveryDestinationRequest("new", null, null, null, null)));
        assertEquals(0, dsl.fetchCount(DSL.table(SCHEMA + ".delivery_policy")));
    }

    @Test void administratorOperationsUseAuthenticatedCanonicalRole() {
        assertThrows(ResponseStatusException.class, () -> service.listAdministrativeDestinations(null));
        actor = CanonicalActor.user("admin", "tenant-z", Set.of("ADMIN"), "jwt");
        assertTrue(service.listAdministrativeDestinations(null).isEmpty());
        actor = CanonicalActor.system("internal", "tenant-z");
        assertThrows(ResponseStatusException.class, () -> service.listAdministrativeJobs(null, null, 0, 10));
    }

    @Test void credentialStoreFailureNeverCreatesDestination() {
        when(secrets.storeCredentialMap(anyString(), anyString(), anyMap())).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> service.createDestination("tenant-a", destination("one")));
        assertEquals(0, destinations());
    }

    @Test void registryFailureCompensatesNewSecretAndRollsBackDatabase() {
        doThrow(new IllegalStateException("registry failed")).when(registry).register(anyString(), anyString(), anyString(), anyString());
        assertThrows(IllegalStateException.class, () -> service.createDestination("tenant-a", destination("one")));
        assertEquals(0, destinations());
        assertTrue(vault.isEmpty());
    }

    @Test void databaseFailureCompensatesOnlyNewSecret() {
        service.createDestination("tenant-a", destination("one"));
        var before = Map.copyOf(vault);
        assertThrows(RuntimeException.class, () -> service.createDestination("tenant-a", destination("x".repeat(300))));
        assertEquals(1, destinations());
        assertEquals(before, vault);
        verify(secrets, times(2)).storeCredentialMap(anyString(), anyString(), anyMap());
    }

    @Test void failedRotationKeepsOldCommittedCredentialsAndFields() {
        var dest = service.createDestination("tenant-a", destination("one"));
        var before = Map.copyOf(vault);
        doThrow(new IllegalStateException("registry failed")).when(registry).register(anyString(), anyString(), anyString(), anyString());
        assertThrows(IllegalStateException.class, () -> service.updateDestination("tenant-a", dest.id(),
                new UpdateDeliveryDestinationRequest("changed", null, null, null, Map.of("password", "new-test-only"))));
        assertEquals("one", service.listDestinations("tenant-a").getFirst().name());
        assertEquals(before, vault);
    }

    @Test void successfulRotationRetiresOldVersionAfterCommit() {
        var dest = service.createDestination("tenant-a", destination("one"));
        String old = vault.keySet().iterator().next();
        service.updateDestination("tenant-a", dest.id(), new UpdateDeliveryDestinationRequest(null, null, null, null, Map.of("password", "new-test-only")));
        assertFalse(vault.containsKey(old));
        assertEquals(1, vault.size());
    }

    @Test void cleanupFailureIsNotReportedAsSuccessfulDeletion() {
        var dest = service.createDestination("tenant-a", destination("one"));
        doThrow(new IllegalStateException("cleanup unresolved")).when(secrets).deleteByRef(anyString());
        assertThrows(IllegalStateException.class, () -> service.deleteDestination("tenant-a", dest.id()));
        assertEquals(0, destinations()); // database commit is explicit; external cleanup failed, not rolled back
        assertEquals(1, vault.size());
    }

    @Test void explicitCredentialCannotCrossTenantScope() {
        assertThrows(IllegalArgumentException.class, () -> service.createDestination("tenant-a",
                new CreateDeliveryDestinationRequest("one", "SFTP", Map.of(), "vault:platform/delivery/tenants/tenant-b/destinations/d", Map.of(), true)));
        assertEquals(0, destinations());
    }

    DeliveryCredentialMigrationService migration() {
        var factory = new ProxyFactory(new DeliveryCredentialMigrationService(dsl, secrets, credentials, access));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        return (DeliveryCredentialMigrationService) factory.getProxy();
    }

    @Test void migrationUsesAdministratorAndSameCredentialTransaction() {
        when(secrets.isVaultEnabled()).thenReturn(true);
        dsl.execute("insert into " + SCHEMA + ".delivery_destination(id,tenant_id,name,protocol,created_at,credential_json) values ('legacy','tenant-a','legacy','SFTP',current_timestamp,'{\"password\":\"test-only\"}')");
        assertThrows(ResponseStatusException.class, () -> migration().migrateTenant("tenant-a", false));
        actor = CanonicalActor.user("admin", "tenant-z", Set.of("ADMIN"), "jwt");
        assertEquals(1, migration().migrateTenant("tenant-a", false).migrated());
        assertNull(dsl.fetchValue("select credential_json from " + SCHEMA + ".delivery_destination where id='legacy'"));
        assertEquals(1, vault.size());
    }

    @Test void migrationFailureRetainsInlineCredentialsAndCompensatesVault() {
        when(secrets.isVaultEnabled()).thenReturn(true);
        actor = CanonicalActor.user("admin", "tenant-z", Set.of("ADMIN"), "jwt");
        dsl.execute("insert into " + SCHEMA + ".delivery_destination(id,tenant_id,name,protocol,created_at,credential_json) values ('legacy','tenant-a','legacy','SFTP',current_timestamp,'{\"password\":\"test-only\"}')");
        doThrow(new IllegalStateException("registry failure")).when(registry).register(anyString(), anyString(), anyString(), anyString());
        assertThrows(IllegalStateException.class, () -> migration().migrateTenant("tenant-a", false));
        assertNotNull(dsl.fetchValue("select credential_json from " + SCHEMA + ".delivery_destination where id='legacy'"));
        assertNull(dsl.fetchValue("select credential_ref from " + SCHEMA + ".delivery_destination where id='legacy'"));
        assertTrue(vault.isEmpty());
    }
}
