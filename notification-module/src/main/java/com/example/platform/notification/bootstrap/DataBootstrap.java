package com.example.platform.notification.bootstrap;

import com.example.platform.notification.app.NotificationTemplateService;
import com.example.platform.notification.domain.NotificationTemplate;
import com.example.platform.notification.domain.NotificationTemplateChannel;
import com.example.platform.notification.domain.NotificationTemplateCode;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class DataBootstrap {
    @Bean
    CommandLineRunner seedTemplates(NotificationTemplateService templates) {
        return args -> templates.ensureTemplate(new NotificationTemplate(
                null,
                NotificationTemplateCode.RENDER_CREATED,
                NotificationTemplateChannel.WEBHOOK,
                "en",
                1,
                "Render created",
                "{\"eventType\":\"{{eventType}}\",\"subjectId\":\"{{subjectId}}\",\"payload\":{{payloadJson}}}"
        ));
    }
}
