package com.example.platform.cloudresource.app;

import com.example.platform.cloudresource.domain.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class CloudResourceCatalogService {

    private final List<CloudResourceProvider> providers;
    private final Map<String, CloudBucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, CloudQueue> queues = new ConcurrentHashMap<>();
    private final Map<String, CloudFunction> functions = new ConcurrentHashMap<>();
    private final Map<String, CloudCdnDistribution> cdnDistributions = new ConcurrentHashMap<>();
    private final AtomicLong bucketSeq = new AtomicLong(0);
    private final AtomicLong queueSeq = new AtomicLong(0);
    private final AtomicLong functionSeq = new AtomicLong(0);
    private final AtomicLong cdnSeq = new AtomicLong(0);

    public CloudResourceCatalogService(List<CloudResourceProvider> providers) {
        this.providers = providers;
    }

    public List<String> providerCodes() {
        return providers.stream().map(CloudResourceProvider::code).toList();
    }

    public CloudBucket createBucket(String name, String region, String provider) {
        String id = "cb-" + bucketSeq.incrementAndGet();
        CloudBucket bucket = new CloudBucket(id, name, region, provider, "ACTIVE");
        buckets.put(id, bucket);
        return bucket;
    }

    public Optional<CloudBucket> findBucket(String id) {
        return Optional.ofNullable(buckets.get(id));
    }

    public List<CloudBucket> listBuckets() {
        return List.copyOf(buckets.values());
    }

    public CloudQueue createQueue(String name, String provider, String url) {
        String id = "cq-" + queueSeq.incrementAndGet();
        CloudQueue queue = new CloudQueue(id, name, provider, url);
        queues.put(id, queue);
        return queue;
    }

    public Optional<CloudQueue> findQueue(String id) {
        return Optional.ofNullable(queues.get(id));
    }

    public List<CloudQueue> listQueues() {
        return List.copyOf(queues.values());
    }

    public CloudFunction createFunction(String name, String runtime, String provider) {
        String id = "cf-" + functionSeq.incrementAndGet();
        CloudFunction function = new CloudFunction(id, name, runtime, provider);
        functions.put(id, function);
        return function;
    }

    public Optional<CloudFunction> findFunction(String id) {
        return Optional.ofNullable(functions.get(id));
    }

    public List<CloudFunction> listFunctions() {
        return List.copyOf(functions.values());
    }

    public CloudCdnDistribution createCdnDistribution(String domain, String provider) {
        String id = "cdn-" + cdnSeq.incrementAndGet();
        CloudCdnDistribution dist = new CloudCdnDistribution(id, domain, provider);
        cdnDistributions.put(id, dist);
        return dist;
    }

    public Optional<CloudCdnDistribution> findCdnDistribution(String id) {
        return Optional.ofNullable(cdnDistributions.get(id));
    }

    public List<CloudCdnDistribution> listCdnDistributions() {
        return List.copyOf(cdnDistributions.values());
    }

    public Map<String, Object> overview() {
        return Map.of(
                "module", "cloud-resource-module",
                "providers", providerCodes(),
                "buckets", buckets.size(),
                "queues", queues.size(),
                "functions", functions.size(),
                "cdnDistributions", cdnDistributions.size()
        );
    }
}
