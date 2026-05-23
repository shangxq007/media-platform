package com.example.platform.delivery.infrastructure;

import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.delivery.spi.DeliveryAdapter;
import com.example.platform.delivery.spi.DeliveryContext;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Copies platform artifact bytes into a destination bucket (S3-compatible or local FS via platform BlobStorage).
 */
@Component
public class S3MirrorDeliveryAdapter implements DeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(S3MirrorDeliveryAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BlobStorage platformStorage;

    public S3MirrorDeliveryAdapter(BlobStorage platformStorage) {
        this.platformStorage = platformStorage;
    }

    @Override
    public DeliveryProtocol protocol() {
        return DeliveryProtocol.S3_MIRROR;
    }

    @Override
    public ProbeResult probe(DeliveryContext context) {
        String bucket = stringVal(context.destinationConfig(), "bucket");
        if (bucket.isBlank()) {
            return ProbeResult.failure("bucket is required in destination config");
        }
        return ProbeResult.success();
    }

    @Override
    public DeliveryResult deliver(DeliveryContext context) {
        try {
            String bucket = stringVal(context.destinationConfig(), "bucket", "render-cache");
            String keyPrefix = stringVal(context.destinationConfig(), "keyPrefix", "");
            String objectKey = (keyPrefix.isBlank() ? "" : keyPrefix.replaceAll("/$", "") + "/")
                    + context.remotePath().replace('\\', '/');
            byte[] bytes = context.sourceStream().readAllBytes();
            StorageObjectRef ref = platformStorage.put(
                    new PutObjectCommand(bucket, objectKey, bytes, context.contentType()));
            String remoteUri = ref.provider() + "://" + ref.bucket() + "/" + ref.objectKey();
            log.info("S3_MIRROR delivery job={} uri={} bytes={}", context.deliveryJobId(), remoteUri, bytes.length);
            return DeliveryResult.ok(context.remotePath(), remoteUri, bytes.length);
        } catch (Exception e) {
            log.warn("S3_MIRROR delivery failed job={}: {}", context.deliveryJobId(), e.getMessage());
            return DeliveryResult.fail(e.getMessage());
        }
    }

    private static String stringVal(Map<String, Object> map, String key) {
        return DeliveryConfigParser.stringVal(map, key);
    }

    private static String stringVal(Map<String, Object> map, String key, String defaultValue) {
        return DeliveryConfigParser.stringVal(map, key, defaultValue);
    }
}
