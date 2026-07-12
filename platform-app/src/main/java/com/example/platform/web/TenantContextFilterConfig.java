package com.example.platform.web;

import com.example.platform.security.JwtProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers {@link TenantContextFilter} without Spring AOP proxy (avoids GenericFilterBean logger NPE). */
@Configuration
public class TenantContextFilterConfig {

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(JwtProperties jwtProperties) {
        FilterRegistrationBean<TenantContextFilter> bean =
                new FilterRegistrationBean<>(new TenantContextFilter(jwtProperties));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }
}
