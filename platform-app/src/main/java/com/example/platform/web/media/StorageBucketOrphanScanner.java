package com.example.platform.web.media;

import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.StoredObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Enumerates bucket objects and flags those absent from {@link KnownStorageUriIndexService}.
 */
@Service
public class StorageBucketOrphanScanner {

    private final BlobStorage blobStorage;
    private final KnownStorageUriIndexService uriIndexService;

    @Value("${platform.storage.orphan-scan.buckets:artifacts,render-cache}")
    private List<String> scanBuckets;

    @Value("${platform.storage.orphan-scan.max-objects-per-bucket:500}")
    private int maxObjectsPerBucket;

    public StorageBucketOrphanScanner(
            BlobStorage blobStorage,
            KnownStorageUriIndexService uriIndexService) {
        this.blobStorage = blobStorage;
        this.uriIndexService = uriIndexService;
    }

    public OrphanScanResult scanBuckets() {
        Set<String> known = uriIndexService.buildKnownUriIndex();
        String provider = uriIndexService.providerCode();
        List<OrphanFinding> orphans = new ArrayList<>();
        int objectsScanned = 0;

        for (String bucket : scanBuckets) {
            List<StoredObject> objects = blobStorage.listObjects(bucket, "", maxObjectsPerBucket);
            objectsScanned += objects.size();
            for (StoredObject object : objects) {
                String uri = object.toStorageUri(provider);
                if (!known.contains(KnownStorageUriIndexService.normalize(uri))) {
                    orphans.add(new OrphanFinding(
                            "AST-005",
                            bucket,
                            object.objectKey(),
                            uri,
                            object.sizeBytes(),
                            "Storage object has no catalog/timeline/delivery/render reference"));
                }
            }
        }

        return new OrphanScanResult(objectsScanned, orphans.size(), orphans, known.size());
    }

    public record OrphanFinding(
            String ruleId,
            String bucket,
            String objectKey,
            String storageUri,
            long sizeBytes,
            String message) {}

    public record OrphanScanResult(
            int objectsScanned,
            int orphanCount,
            List<OrphanFinding> orphans,
            int knownUriCount) {}
}
