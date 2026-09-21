package com.example.platform.analytics;

import com.example.platform.analytics.infrastructure.*;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

/** Owner composition, with one explicit persistence backend for every analytics path. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.analytics", name = "enabled", havingValue = "true")
@ComponentScan("com.example.platform.analytics")
public class AnalyticsConfiguration {
    @Bean
    SmartInitializingSingleton analyticsPersistenceValidation(ApplicationContext context, Environment environment) {
        return () -> {
            String backend = environment.getProperty("app.analytics.persistence", "jdbc");
            if (!List.of("jdbc", "memory").contains(backend)) {
                throw new IllegalStateException("app.analytics.persistence must be jdbc or memory");
            }
            if (backend.equals("memory") && !environment.matchesProfiles("(dev | test) & !prod")) {
                throw new IllegalStateException("Analytics memory requires explicit dev/test without prod");
            }
            for (Class<?> owner : List.of(UserBehaviorEventRepository.class, UserProfileRepository.class,
                    UserHabitsRepository.class, UserSegmentRepository.class)) {
                if (context.getBeansOfType(owner).size() != 1) {
                    throw new IllegalStateException("Analytics requires exactly one " + owner.getSimpleName());
                }
            }
            if (backend.equals("jdbc")) {
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                // All singletons (including the canonical migration initializer) are ready; no business writes.
                for (String table : List.of("user_behavior_event", "user_profile", "user_habits", "user_segment")) {
                    jdbc.queryForList("SELECT * FROM " + table + " WHERE 1=0");
                }
            }
        };
    }
}
