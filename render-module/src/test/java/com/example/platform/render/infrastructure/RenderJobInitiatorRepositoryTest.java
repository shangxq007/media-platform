package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.events.RenderInitiator;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.OffsetDateTime;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderJobInitiatorRepositoryTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private RenderJobRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        RenderTestSchemaFixture.truncate(dsl);
        repository = new RenderJobRepository(dsl);
    }

    @Test
    void acceptedAndRejectedJobsRoundTripExactPrincipal() {
        RenderInitiator p1 = RenderInitiator.from(
                CanonicalActor.user("principal-p1", "tenant-1", Set.of("ADMIN"), "jwt"));

        repository.create("rj-accepted", "project-1", "tenant-1", "snapshot-1",
                "default", "QUEUED", p1, OffsetDateTime.now());
        repository.createRejected("rj-rejected", "project-1", "tenant-1", "snapshot-2",
                "default", "quota", p1, OffsetDateTime.now());

        assertEquals(p1, repository.requireInitiator("rj-accepted"));
        assertEquals(p1, repository.requireInitiator("rj-rejected"));
    }

    @Test
    void retryCopiesOriginalInitiatorExactly() {
        RenderInitiator original = RenderInitiator.from(
                CanonicalActor.apiKey("api-key-1", "tenant-1", Set.of(), "api-key"));
        repository.create("rj-original", "project-1", "tenant-1", "snapshot-1",
                "default", "FAILED", original, OffsetDateTime.now());

        repository.createRetryJob("rj-retry", "rj-original");

        assertEquals(original, repository.requireInitiator("rj-retry"));
    }

    @Test
    void explicitSystemIsPersistedWithoutFabricatingHumanPrincipal() {
        RenderInitiator system = RenderInitiator.from(
                CanonicalActor.system("workflow-render-service", "tenant-1"));

        repository.create("rj-system", "project-1", "tenant-1", "snapshot-1",
                "default", "QUEUED", system, OffsetDateTime.now());

        assertEquals(system, repository.requireInitiator("rj-system"));
    }
}
