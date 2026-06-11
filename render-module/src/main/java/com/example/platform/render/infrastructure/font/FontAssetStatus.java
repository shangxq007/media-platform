package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Set;

public enum FontAssetStatus {
    UPLOADED,
    QUARANTINED,
    SECURITY_CHECK_PENDING,
    SECURITY_REJECTED,
    VALIDATION_PENDING,
    VALIDATION_FAILED,
    READY,
    SUBSETTING_PENDING,
    SUBSETTING_FAILED,
    READY_WITH_SUBSETS,
    DISABLED
}
