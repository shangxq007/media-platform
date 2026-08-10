package com.example.platform.storage.opendal;

import com.example.platform.storage.contract.provider.CapabilitySupport;
import com.example.platform.storage.contract.provider.ProviderCapability;
import org.apache.opendal.Capability;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maps OpenDAL Capability to platform CapabilitySupport.
 * Reads actual capabilities from Operator.info — no hardcoded assumptions.
 */
public final class OpenDalCapabilityMapper {
    private OpenDalCapabilityMapper() {}

    public static Map<ProviderCapability, CapabilitySupport> mapCapability(Capability nativeCapability) {
        Map<ProviderCapability, CapabilitySupport> result = new EnumMap<>(ProviderCapability.class);
        if (nativeCapability == null) return result;

        result.put(ProviderCapability.RANGE_READ, nativeCapability.isRead() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.STREAMING_READ, nativeCapability.isRead() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.STREAMING_WRITE, nativeCapability.isWrite() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.MULTIPART_WRITE, nativeCapability.isWriteCanMulti() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.CONDITIONAL_CREATE, CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.CONDITIONAL_WRITE, CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.NATIVE_CHECKSUM, CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.COPY, nativeCapability.isCopy() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.RENAME, nativeCapability.isRename() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.DELETE, nativeCapability.isDelete() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.BATCH_DELETE, nativeCapability.isBatchDelete() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.PRESIGN_READ, nativeCapability.isPresignRead() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.PRESIGN_WRITE, nativeCapability.isPresignWrite() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.VERSIONING, CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.OBJECT_METADATA, nativeCapability.isStat() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);
        result.put(ProviderCapability.LIST, nativeCapability.isList() ? CapabilitySupport.SUPPORTED : CapabilitySupport.UNSUPPORTED);

        return result;
    }
}
