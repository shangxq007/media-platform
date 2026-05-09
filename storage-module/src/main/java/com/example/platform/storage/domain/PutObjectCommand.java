package com.example.platform.storage.domain;

public record PutObjectCommand(String bucket, String objectKey, byte[] content, String contentType) {}
