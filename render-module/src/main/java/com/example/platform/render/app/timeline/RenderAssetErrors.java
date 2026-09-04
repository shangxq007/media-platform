package com.example.platform.render.app.timeline;

import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import java.util.Map;

/** Asset and storage error information owned by render timeline services. */
final class RenderAssetErrors {
    static final String ASSET_NOT_FOUND = "ASSET-404-001";
    static final String ASSET_TOMBSTONED = "ASSET-410-001";
    static final String STORAGE_NOT_FOUND = "STORAGE-404-001";
    private static final ConfigurableErrorCode ASSET_NOT_FOUND_CODE = code(ASSET_NOT_FOUND, 404301, 404, "Timeline asset not found in registry", "时间线资产未在注册表中找到");
    private static final ConfigurableErrorCode ASSET_STILL_REFERENCED_CODE = code("ASSET-409-001", 409301, 409, "Timeline asset is still referenced and cannot be removed", "时间线资产仍被引用，无法删除");
    private static final ConfigurableErrorCode ASSET_TOMBSTONED_CODE = code(ASSET_TOMBSTONED, 410301, 410, "Timeline asset has been tombstoned", "时间线资产已标记删除（tombstone）");
    private static final ConfigurableErrorCode STORAGE_NOT_FOUND_CODE = code(STORAGE_NOT_FOUND, 404401, 404, "Storage object not found", "存储对象不存在");

    private RenderAssetErrors() {}

    static PlatformException assetNotFound(String assetId) { return exception(ASSET_NOT_FOUND_CODE, "assetId", assetId); }
    static PlatformException assetStillReferenced(String assetId) { return exception(ASSET_STILL_REFERENCED_CODE, "assetId", assetId); }
    static PlatformException assetTombstoned(String assetId) { return exception(ASSET_TOMBSTONED_CODE, "assetId", assetId); }
    static PlatformException storageNotFound(String storageUri) { return exception(STORAGE_NOT_FOUND_CODE, "storageUri", storageUri); }

    private static PlatformException exception(ConfigurableErrorCode code, String detailKey, String detailValue) {
        return new PlatformException(code, detailValue, Map.of(detailKey, detailValue), "en");
    }

    private static ConfigurableErrorCode code(String value, int numericCode, int status, String en, String zh) {
        return new ConfigurableErrorCode(value, numericCode, Map.of("en", en, "zh", zh), "render", status);
    }
}
