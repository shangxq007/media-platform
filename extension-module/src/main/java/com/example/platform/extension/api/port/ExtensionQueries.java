package com.example.platform.extension.api.port;
import java.util.*;
/** Immutable registration metadata, not evidence of successful execution or health. */
public interface ExtensionQueries {
    List<ExtensionInfo> listExtensions();
    Optional<ExtensionInfo> getExtension(String key);
    record ExtensionInfo(String key, String version, String extensionType, String category, String status, String trustLevel) {}
}
