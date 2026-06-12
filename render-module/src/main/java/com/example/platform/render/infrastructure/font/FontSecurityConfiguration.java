package com.example.platform.render.infrastructure.font;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for font security scanning.
 *
 * <p>By default, {@link BasicFontSecurityScanner} is registered as the
 * production-safe scanner. To use the noop scanner (for testing only),
 * set {@code render.font.security.scanner=noop}.
 */
@Configuration
public class FontSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(FontSecurityScanner.class)
    @ConditionalOnProperty(name = "render.font.security.scanner", havingValue = "noop")
    public FontSecurityScanner noopFontSecurityScanner() {
        return new NoopFontSecurityScanner();
    }

    @Bean
    @ConditionalOnMissingBean(FontSecurityScanner.class)
    public FontSecurityScanner basicFontSecurityScanner() {
        return new BasicFontSecurityScanner();
    }
}
