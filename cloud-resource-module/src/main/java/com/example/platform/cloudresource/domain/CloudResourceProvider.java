package com.example.platform.cloudresource.domain;

public interface CloudResourceProvider {
    String code();
    String ensureBucket(String logicalName);
}
