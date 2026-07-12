package com.example.platform.storage.delivery.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage delivery profile configuration properties.
 * Disabled by default. No runtime profile switching.
 */
@ConfigurationProperties(prefix = "storage.delivery")
public class StorageDeliveryProfileConfigProperties {

    private String defaultProfile = "preview-r2-signed-url";
    private Map<String, ProfileConfig> profiles = Map.of();

    public String getDefaultProfile() { return defaultProfile; }
    public void setDefaultProfile(String defaultProfile) { this.defaultProfile = defaultProfile; }
    public Map<String, ProfileConfig> getProfiles() { return profiles; }
    public void setProfiles(Map<String, ProfileConfig> profiles) { this.profiles = profiles; }

    public static class ProfileConfig {
        private String status;
        private boolean enabled;
        private boolean runtimeSelectable;
        private String provider;
        private String backend;
        private String accessMode;
        private String accessDescriptorType;
        private CapabilitiesConfig capabilities;
        private SecurityConfig security;

        // Getters/setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isRuntimeSelectable() { return runtimeSelectable; }
        public void setRuntimeSelectable(boolean runtimeSelectable) { this.runtimeSelectable = runtimeSelectable; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }
        public String getAccessMode() { return accessMode; }
        public void setAccessMode(String accessMode) { this.accessMode = accessMode; }
        public String getAccessDescriptorType() { return accessDescriptorType; }
        public void setAccessDescriptorType(String accessDescriptorType) { this.accessDescriptorType = accessDescriptorType; }
        public CapabilitiesConfig getCapabilities() { return capabilities; }
        public void setCapabilities(CapabilitiesConfig capabilities) { this.capabilities = capabilities; }
        public SecurityConfig getSecurity() { return security; }
        public void setSecurity(SecurityConfig security) { this.security = security; }
    }

    public static class CapabilitiesConfig {
        private boolean writeArtifact;
        private boolean readArtifact;
        private boolean presignRead;
        private boolean internalStream;
        private boolean externalBucket;
        private boolean exportBundle;
        private boolean deleteArtifact;
        private boolean supportsRangeRead;
        private boolean supportsContentMetadata;

        // Getters/setters
        public boolean isWriteArtifact() { return writeArtifact; }
        public void setWriteArtifact(boolean writeArtifact) { this.writeArtifact = writeArtifact; }
        public boolean isReadArtifact() { return readArtifact; }
        public void setReadArtifact(boolean readArtifact) { this.readArtifact = readArtifact; }
        public boolean isPresignRead() { return presignRead; }
        public void setPresignRead(boolean presignRead) { this.presignRead = presignRead; }
        public boolean isInternalStream() { return internalStream; }
        public void setInternalStream(boolean internalStream) { this.internalStream = internalStream; }
        public boolean isExternalBucket() { return externalBucket; }
        public void setExternalBucket(boolean externalBucket) { this.externalBucket = externalBucket; }
        public boolean isExportBundle() { return exportBundle; }
        public void setExportBundle(boolean exportBundle) { this.exportBundle = exportBundle; }
        public boolean isDeleteArtifact() { return deleteArtifact; }
        public void setDeleteArtifact(boolean deleteArtifact) { this.deleteArtifact = deleteArtifact; }
        public boolean isSupportsRangeRead() { return supportsRangeRead; }
        public void setSupportsRangeRead(boolean supportsRangeRead) { this.supportsRangeRead = supportsRangeRead; }
        public boolean isSupportsContentMetadata() { return supportsContentMetadata; }
        public void setSupportsContentMetadata(boolean supportsContentMetadata) { this.supportsContentMetadata = supportsContentMetadata; }
    }

    public static class SecurityConfig {
        private boolean exposeStorageReference;
        private boolean exposeBucket;
        private boolean exposeObjectKey;
        private boolean exposeLocalPath;
        private boolean persistSignedUrl;
        private boolean requireTenantProjectScope;
        private boolean userFacing;

        // Getters/setters
        public boolean isExposeStorageReference() { return exposeStorageReference; }
        public void setExposeStorageReference(boolean exposeStorageReference) { this.exposeStorageReference = exposeStorageReference; }
        public boolean isExposeBucket() { return exposeBucket; }
        public void setExposeBucket(boolean exposeBucket) { this.exposeBucket = exposeBucket; }
        public boolean isExposeObjectKey() { return exposeObjectKey; }
        public void setExposeObjectKey(boolean exposeObjectKey) { this.exposeObjectKey = exposeObjectKey; }
        public boolean isExposeLocalPath() { return exposeLocalPath; }
        public void setExposeLocalPath(boolean exposeLocalPath) { this.exposeLocalPath = exposeLocalPath; }
        public boolean isPersistSignedUrl() { return persistSignedUrl; }
        public void setPersistSignedUrl(boolean persistSignedUrl) { this.persistSignedUrl = persistSignedUrl; }
        public boolean isRequireTenantProjectScope() { return requireTenantProjectScope; }
        public void setRequireTenantProjectScope(boolean requireTenantProjectScope) { this.requireTenantProjectScope = requireTenantProjectScope; }
        public boolean isUserFacing() { return userFacing; }
        public void setUserFacing(boolean userFacing) { this.userFacing = userFacing; }
    }
}
