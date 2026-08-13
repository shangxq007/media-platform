package com.example.platform.render.infrastructure.font;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for font security scanning.
 *
 * <p>{@link BasicFontSecurityScanner} is registered as the production-safe
 * scanner (R1-REISSUE: the historical {@code render.font.security.scanner=noop}
 * test-only toggle was retired; noop implementations live in testFixtures).</p>
 */
@Configuration
public class FontSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(FontSecurityScanner.class)
    public FontSecurityScanner basicFontSecurityScanner() {
        return new BasicFontSecurityScanner();
    }
}
