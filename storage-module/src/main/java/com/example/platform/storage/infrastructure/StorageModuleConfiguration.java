package com.example.platform.storage.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageS3Properties.class)
public class StorageModuleConfiguration {}
