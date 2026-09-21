package com.example.platform.extension.api.port;
import com.example.platform.extension.domain.ExtensionResourceLimits;
public interface ExtensionLimitQueries { ExtensionResourceLimits getLimits(String key);
 record Limits(long timeoutMs,int maxConcurrency,long maxOutputBytes) {}
 Limits limits(String key); }
