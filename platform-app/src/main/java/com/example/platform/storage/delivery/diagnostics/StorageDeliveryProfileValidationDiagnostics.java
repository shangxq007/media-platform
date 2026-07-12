package com.example.platform.storage.delivery.diagnostics;

import java.util.List;

public record StorageDeliveryProfileValidationDiagnostics(
    boolean valid,
    int errorCount,
    int warningCount,
    List<String> errorCodes,
    List<String> warningCodes
) {}
