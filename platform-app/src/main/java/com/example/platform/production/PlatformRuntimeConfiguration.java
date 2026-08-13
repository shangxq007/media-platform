package com.example.platform.production;

import com.example.platform.production.PlatformRuntimeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PlatformRuntimeProperties.class, EgressProxySmokeProperties.class})
public class PlatformRuntimeConfiguration {}
