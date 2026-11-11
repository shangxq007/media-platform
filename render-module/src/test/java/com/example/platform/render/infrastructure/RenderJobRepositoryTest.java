package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.render.testsupport.RenderInitiatorFixtures;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderJobRepositoryTest extends PostgresTestContainerSupport {

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
    void createAndFindById() {
        repository.create("rj-1", "proj-1", "tenant-1", "snap-1", "social_1080p", "QUEUED", RenderInitiatorFixtures.user("tenant-1"), OffsetDateTime.now());

        Optional<RenderJobResponse> found = repository.findById("rj-1");
        assertTrue(found.isPresent());
        assertEquals("rj-1", found.get().id());
        assertEquals("proj-1", found.get().projectId());
        assertEquals("snap-1", found.get().timelineSnapshotId());
        assertEquals("social_1080p", found.get().profile());
        assertEquals("QUEUED", found.get().status());
    }

    @Test
    void findByIdReturnsEmptyForMissing() {
        Optional<RenderJobResponse> found = repository.findById("nonexistent");
        assertFalse(found.isPresent());
    }

    @Test
    void findByIdAndProjectAndTenant() {
        repository.create("rj-2", "proj-2", "tenant-2", "snap-2", "standard", "QUEUED", RenderInitiatorFixtures.user("tenant-2"), OffsetDateTime.now());

        Optional<RenderJobResponse> found = repository.findByIdAndProjectAndTenant("rj-2", "proj-2", "tenant-2");
        assertTrue(found.isPresent());
        assertEquals("rj-2", found.get().id());
    }

    @Test
    void findByIdAndProjectAndTenantRejectsWrongTenant() {
        repository.create("rj-3", "proj-3", "tenant-3", "snap-3", "standard", "QUEUED", RenderInitiatorFixtures.user("tenant-3"), OffsetDateTime.now());

        Optional<RenderJobResponse> found = repository.findByIdAndProjectAndTenant("rj-3", "proj-3", "wrong-tenant");
        assertFalse(found.isPresent());
    }

    @Test
    void findByIdAndProjectAndTenantRejectsWrongProject() {
        repository.create("rj-4", "proj-4", "tenant-4", "snap-4", "standard", "QUEUED", RenderInitiatorFixtures.user("tenant-4"), OffsetDateTime.now());

        Optional<RenderJobResponse> found = repository.findByIdAndProjectAndTenant("rj-4", "wrong-proj", "tenant-4");
        assertFalse(found.isPresent());
    }



    @Test
    void listByProjectAndTenant() {
        repository.create("rj-7a", "proj-7", "tenant-7", "snap-7a", "standard", "QUEUED", RenderInitiatorFixtures.user("tenant-7"), OffsetDateTime.now());
        repository.create("rj-7b", "proj-7", "tenant-7", "snap-7b", "social_1080p", "RUNNING", RenderInitiatorFixtures.user("tenant-7"), OffsetDateTime.now());
        repository.create("rj-8", "proj-8", "tenant-7", "snap-8", "standard", "QUEUED", RenderInitiatorFixtures.user("tenant-7"), OffsetDateTime.now());

        List<RenderJobResponse> proj7Jobs = repository.listByProjectAndTenant("proj-7", "tenant-7");
        assertEquals(2, proj7Jobs.size());
        assertTrue(proj7Jobs.stream().allMatch(j -> j.projectId().equals("proj-7")));
    }







    @Test
    void findTenantIdById() {
        repository.create("rj-12", "proj-12", "tenant-12", "snap-12", "standard", "QUEUED", RenderInitiatorFixtures.user("tenant-12"), OffsetDateTime.now());

        Optional<String> tenantId = repository.findTenantIdById("rj-12");
        assertTrue(tenantId.isPresent());
        assertEquals("tenant-12", tenantId.get());
    }

    @Test
    void findTenantIdByIdReturnsEmptyForMissing() {
        Optional<String> tenantId = repository.findTenantIdById("nonexistent");
        assertFalse(tenantId.isPresent());
    }






    @Test void compareAndSetRequiresExactOwnerAndExpectedStatus() {
        repository.create("cas-job","cas-project","cas-tenant","snapshot","default","QUEUED",RenderInitiatorFixtures.user("cas-tenant"),OffsetDateTime.now());
        assertEquals(0,repository.compareAndSetStatus("cas-job","cas-project","foreign","QUEUED","CANCELLED"));
        assertEquals(0,repository.compareAndSetStatus("cas-job","foreign","cas-tenant","QUEUED","CANCELLED"));
        assertEquals(0,repository.compareAndSetStatus("cas-job","cas-project","cas-tenant","EXECUTING","CANCELLED"));
        assertEquals(1,repository.compareAndSetStatus("cas-job","cas-project","cas-tenant","QUEUED","CANCELLED"));
        assertEquals(0,repository.compareAndSetStatus("cas-job","cas-project","cas-tenant","QUEUED","EXECUTING"));
        assertEquals("CANCELLED",repository.findById("cas-job").orElseThrow().status());
    }
}
