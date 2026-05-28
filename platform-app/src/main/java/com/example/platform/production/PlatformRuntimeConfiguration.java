package com.example.platform.production;

import com.example.platform.shared.runtime.PlatformRuntimeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PlatformRuntimeProperties.class, EgressProxySmokeProperties.class})
public class PlatformRuntimeConfiguration {}
