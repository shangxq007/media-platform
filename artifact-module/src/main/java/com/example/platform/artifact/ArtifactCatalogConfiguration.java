package com.example.platform.artifact;

import com.example.platform.artifact.infrastructure.ArtifactGcProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ArtifactGcProperties.class)
public class ArtifactCatalogConfiguration {}
