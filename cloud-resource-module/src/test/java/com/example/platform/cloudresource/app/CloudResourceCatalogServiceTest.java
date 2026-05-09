package com.example.platform.cloudresource.app;

import com.example.platform.cloudresource.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CloudResourceCatalogServiceTest {

    private CloudResourceCatalogService service;

    @BeforeEach
    void setUp() {
        CloudResourceProvider stubProvider = new CloudResourceProvider() {
            @Override
            public String code() { return "test-provider"; }

            @Override
            public String ensureBucket(String logicalName) { return "test://bucket/" + logicalName; }
        };
        service = new CloudResourceCatalogService(List.of(stubProvider));
    }

    @Test
    void providerCodesReturnsRegisteredProviders() {
        List<String> codes = service.providerCodes();
        assertEquals(List.of("test-provider"), codes);
    }

    @Test
    void createBucketReturnsBucketWithGeneratedId() {
        CloudBucket bucket = service.createBucket("my-bucket", "us-east-1", "test-provider");
        assertNotNull(bucket.id());
        assertTrue(bucket.id().startsWith("cb-"));
        assertEquals("my-bucket", bucket.name());
        assertEquals("us-east-1", bucket.region());
        assertEquals("test-provider", bucket.provider());
        assertEquals("ACTIVE", bucket.status());
    }

    @Test
    void findBucketReturnsBucketWhenExists() {
        CloudBucket created = service.createBucket("find-me", "eu-west-1", "test-provider");
        Optional<CloudBucket> found = service.findBucket(created.id());
        assertTrue(found.isPresent());
        assertEquals("find-me", found.get().name());
    }

    @Test
    void findBucketReturnsEmptyWhenNotFound() {
        Optional<CloudBucket> found = service.findBucket("cb-999");
        assertTrue(found.isEmpty());
    }

    @Test
    void listBucketsReturnsAllCreatedBuckets() {
        service.createBucket("b1", "us-east-1", "test-provider");
        service.createBucket("b2", "eu-west-1", "test-provider");
        assertEquals(2, service.listBuckets().size());
    }

    @Test
    void createQueueReturnsQueueWithGeneratedId() {
        CloudQueue queue = service.createQueue("my-queue", "test-provider", "https://queue.example.com");
        assertNotNull(queue.id());
        assertTrue(queue.id().startsWith("cq-"));
        assertEquals("my-queue", queue.name());
        assertEquals("test-provider", queue.provider());
        assertEquals("https://queue.example.com", queue.url());
    }

    @Test
    void findQueueReturnsQueueWhenExists() {
        CloudQueue created = service.createQueue("q1", "test-provider", "https://q.example.com");
        Optional<CloudQueue> found = service.findQueue(created.id());
        assertTrue(found.isPresent());
        assertEquals("q1", found.get().name());
    }

    @Test
    void listQueuesReturnsAllCreatedQueues() {
        service.createQueue("q1", "test-provider", "https://q1.example.com");
        service.createQueue("q2", "test-provider", "https://q2.example.com");
        assertEquals(2, service.listQueues().size());
    }

    @Test
    void createFunctionReturnsFunctionWithGeneratedId() {
        CloudFunction function = service.createFunction("my-func", "java17", "test-provider");
        assertNotNull(function.id());
        assertTrue(function.id().startsWith("cf-"));
        assertEquals("my-func", function.name());
        assertEquals("java17", function.runtime());
        assertEquals("test-provider", function.provider());
    }

    @Test
    void findFunctionReturnsFunctionWhenExists() {
        CloudFunction created = service.createFunction("f1", "python3.11", "test-provider");
        Optional<CloudFunction> found = service.findFunction(created.id());
        assertTrue(found.isPresent());
        assertEquals("f1", found.get().name());
    }

    @Test
    void listFunctionsReturnsAllCreatedFunctions() {
        service.createFunction("f1", "java17", "test-provider");
        service.createFunction("f2", "python3.11", "test-provider");
        assertEquals(2, service.listFunctions().size());
    }

    @Test
    void createCdnDistributionReturnsDistributionWithGeneratedId() {
        CloudCdnDistribution dist = service.createCdnDistribution("cdn.example.com", "test-provider");
        assertNotNull(dist.id());
        assertTrue(dist.id().startsWith("cdn-"));
        assertEquals("cdn.example.com", dist.domain());
        assertEquals("test-provider", dist.provider());
    }

    @Test
    void findCdnDistributionReturnsDistributionWhenExists() {
        CloudCdnDistribution created = service.createCdnDistribution("assets.example.com", "test-provider");
        Optional<CloudCdnDistribution> found = service.findCdnDistribution(created.id());
        assertTrue(found.isPresent());
        assertEquals("assets.example.com", found.get().domain());
    }

    @Test
    void listCdnDistributionsReturnsAllCreated() {
        service.createCdnDistribution("a.example.com", "test-provider");
        service.createCdnDistribution("b.example.com", "test-provider");
        assertEquals(2, service.listCdnDistributions().size());
    }

    @Test
    void overviewReturnsResourceCounts() {
        service.createBucket("b1", "us-east-1", "test-provider");
        service.createQueue("q1", "test-provider", "https://q.example.com");
        Map<String, Object> overview = service.overview();
        assertEquals("cloud-resource-module", overview.get("module"));
        assertEquals(1, overview.get("buckets"));
        assertEquals(1, overview.get("queues"));
        assertEquals(0, overview.get("functions"));
        assertEquals(0, overview.get("cdnDistributions"));
    }

    @Test
    void listBucketsReturnsImmutableList() {
        service.createBucket("b1", "us-east-1", "test-provider");
        List<CloudBucket> buckets = service.listBuckets();
        assertThrows(UnsupportedOperationException.class, () -> buckets.add(
                new CloudBucket("x", "x", "x", "x", "x")));
    }
}
