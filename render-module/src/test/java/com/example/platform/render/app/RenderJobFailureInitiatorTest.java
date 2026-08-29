package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.testsupport.RenderInitiatorFixtures;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RenderJobFailureInitiatorTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private RenderJobRepository repository;
    private ApplicationEventPublisher eventPublisher;

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
        eventPublisher = mock(ApplicationEventPublisher.class);
    }

    @Test
    void normalFailureEventPreservesStoredPrincipal() {
        var p1 = RenderInitiatorFixtures.user("principal-p1", "tenant-1");
        repository.create("rj-1", "project-1", "tenant-1", "snapshot-1",
                "default", "EXECUTING", p1, OffsetDateTime.now());

        new RenderJobFailureService(repository, eventPublisher)
                .recordDurableFailure("rj-1", "provider failed");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        RenderJobFailedEvent failed = (RenderJobFailedEvent) event.getValue();
        assertEquals(p1, failed.initiator());
    }
}
