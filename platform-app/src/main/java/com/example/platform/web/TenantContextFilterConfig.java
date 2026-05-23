package com.example.platform.web;

import com.example.platform.security.OAuth2SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/** Registers {@link TenantContextFilter} without Spring AOP proxy (avoids GenericFilterBean logger NPE). */
@Configuration
@EnableConfigurationProperties(OAuth2SecurityProperties.class)
public class TenantContextFilterConfig {

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
            Environment environment,
            OAuth2SecurityProperties oauth2Properties) {
        boolean securityEnabled = environment.getProperty("app.security.enabled", Boolean.class, false);
        boolean allowHeader = !securityEnabled
                || !(oauth2Properties.enabled() && oauth2Properties.trustJwtTenantOnly());
        FilterRegistrationBean<TenantContextFilter> bean =
                new FilterRegistrationBean<>(new TenantContextFilter(allowHeader));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }
}
