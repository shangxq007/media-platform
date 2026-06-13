package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
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
        repository.create("rj-1", "proj-1", "tenant-1", "snap-1", "social_1080p", "QUEUED", OffsetDateTime.now());

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
        repository.create("rj-2", "proj-2", "tenant-2", "snap-2", "standard", "QUEUED", OffsetDateTime.now());

        Optional<RenderJobResponse> found = repository.findByIdAndProjectAndTenant("rj-2", "proj-2", "tenant-2");
        assertTrue(found.isPresent());
        assertEquals("rj-2", found.get().id());
    }

    @Test
    void findByIdAndProjectAndTenantRejectsWrongTenant() {
        repository.create("rj-3", "proj-3", "tenant-3", "snap-3", "standard", "QUEUED", OffsetDateTime.now());

        Optional<RenderJobResponse> found = repository.findByIdAndProjectAndTenant("rj-3", "proj-3", "wrong-tenant");
        assertFalse(found.isPresent());
    }

    @Test
    void findByIdAndProjectAndTenantRejectsWrongProject() {
        repository.create("rj-4", "proj-4", "tenant-4", "snap-4", "standard", "QUEUED", OffsetDateTime.now());

        Optional<RenderJobResponse> found = repository.findByIdAndProjectAndTenant("rj-4", "wrong-proj", "tenant-4");
        assertFalse(found.isPresent());
    }

    @Test
    void listByTenant() {
        repository.create("rj-5a", "proj-5", "tenant-5", "snap-5a", "standard", "QUEUED", OffsetDateTime.now());
        repository.create("rj-5b", "proj-5", "tenant-5", "snap-5b", "social_1080p", "RUNNING", OffsetDateTime.now());
        repository.create("rj-6", "proj-6", "tenant-6", "snap-6", "standard", "QUEUED", OffsetDateTime.now());

        List<RenderJobResponse> tenant5Jobs = repository.listByTenant("tenant-5");
        assertEquals(2, tenant5Jobs.size());
        assertTrue(tenant5Jobs.stream().allMatch(j -> j.id().startsWith("rj-5")));
    }

    @Test
    void listByProjectAndTenant() {
        repository.create("rj-7a", "proj-7", "tenant-7", "snap-7a", "standard", "QUEUED", OffsetDateTime.now());
        repository.create("rj-7b", "proj-7", "tenant-7", "snap-7b", "social_1080p", "RUNNING", OffsetDateTime.now());
        repository.create("rj-8", "proj-8", "tenant-7", "snap-8", "standard", "QUEUED", OffsetDateTime.now());

        List<RenderJobResponse> proj7Jobs = repository.listByProjectAndTenant("proj-7", "tenant-7");
        assertEquals(2, proj7Jobs.size());
        assertTrue(proj7Jobs.stream().allMatch(j -> j.projectId().equals("proj-7")));
    }

    @Test
    void updateStatus() {
        repository.create("rj-9", "proj-9", "tenant-9", "snap-9", "standard", "QUEUED", OffsetDateTime.now());
        repository.updateStatus("rj-9", "RUNNING");

        Optional<RenderJobResponse> found = repository.findById("rj-9");
        assertTrue(found.isPresent());
        assertEquals("RUNNING", found.get().status());
    }

    @Test
    void updateStatusAndClearError() {
        repository.create("rj-10", "proj-10", "tenant-10", "snap-10", "standard", "FAILED", OffsetDateTime.now());
        repository.updateStatusWithError("rj-10", "FAILED", "Something went wrong");
        repository.updateStatusAndClearError("rj-10", "QUEUED");

        Optional<RenderJobResponse> found = repository.findById("rj-10");
        assertTrue(found.isPresent());
        assertEquals("QUEUED", found.get().status());
    }

    @Test
    void existsByIdAndTenant() {
        repository.create("rj-11", "proj-11", "tenant-11", "snap-11", "standard", "QUEUED", OffsetDateTime.now());

        assertTrue(repository.existsByIdAndTenant("rj-11", "tenant-11"));
        assertFalse(repository.existsByIdAndTenant("rj-11", "wrong-tenant"));
        assertFalse(repository.existsByIdAndTenant("nonexistent", "tenant-11"));
    }

    @Test
    void findTenantIdById() {
        repository.create("rj-12", "proj-12", "tenant-12", "snap-12", "standard", "QUEUED", OffsetDateTime.now());

        Optional<String> tenantId = repository.findTenantIdById("rj-12");
        assertTrue(tenantId.isPresent());
        assertEquals("tenant-12", tenantId.get());
    }

    @Test
    void findTenantIdByIdReturnsEmptyForMissing() {
        Optional<String> tenantId = repository.findTenantIdById("nonexistent");
        assertFalse(tenantId.isPresent());
    }

    @Test
    void findProjectTenantId() {
        dsl.insertInto(DSL.table("project"))
                .columns(DSL.field("id"), DSL.field("tenant_id"), DSL.field("name"),
                        DSL.field("description"), DSL.field("status"), DSL.field("created_at"))
                .values("proj-13", "tenant-13", "Test", "", "ACTIVE", OffsetDateTime.now())
                .execute();

        Optional<String> tenantId = repository.findProjectTenantId("proj-13");
        assertTrue(tenantId.isPresent());
        assertEquals("tenant-13", tenantId.get());
    }

    @Test
    void findProjectTenantIdReturnsEmptyForMissing() {
        Optional<String> tenantId = repository.findProjectTenantId("nonexistent");
        assertFalse(tenantId.isPresent());
    }

    @Test
    void listAll() {
        repository.create("rj-14a", "proj-14", "tenant-14", "snap-14a", "standard", "QUEUED", OffsetDateTime.now());
        repository.create("rj-14b", "proj-15", "tenant-15", "snap-14b", "social_1080p", "RUNNING", OffsetDateTime.now());

        List<RenderJobResponse> all = repository.listAll();
        assertTrue(all.size() >= 2);
    }
}
