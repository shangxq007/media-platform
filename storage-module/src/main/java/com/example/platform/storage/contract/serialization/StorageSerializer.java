package com.example.platform.storage.contract.serialization;
import com.example.platform.storage.contract.ContentDigest;
import com.example.platform.storage.contract.error.StorageError;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.namespace.*;
import com.example.platform.storage.contract.provider.*;
import com.example.platform.storage.contract.replica.*;
import com.example.platform.storage.contract.validation.StorageValidationModel;
import com.example.platform.storage.contract.write.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
public final class StorageSerializer {
    public static final String CURRENT_SCHEMA_VERSION = "storage-semantics-v1";
    private StorageSerializer() {}
    public static void validateSchemaVersion(String version) {
        if (!CURRENT_SCHEMA_VERSION.equals(version)) {
            throw new IllegalArgumentException("Unsupported schema version: " + version + ". Expected: " + CURRENT_SCHEMA_VERSION);
        }
    }
    public static String serialize(StorageValidationModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"schemaVersion\":\"").append(model.schemaVersion()).append("\"");
        sb.append(",\"objects\":").append(model.objects().size());
        sb.append(",\"replicas\":").append(model.replicas().size());
        sb.append(",\"providers\":").append(model.providers().size());
        sb.append(",\"writeSessions\":").append(model.writeSessions().size());
        sb.append(",\"locations\":").append(model.locations().size());
        sb.append("}");
        return sb.toString();
    }
    public static String digest(StorageValidationModel model) {
        String canonical = serialize(model);
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
