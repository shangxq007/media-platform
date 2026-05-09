package com.example.platform.notification.domain;

public record NotificationTemplate(Long id, NotificationTemplateCode templateCode, NotificationTemplateChannel channel, String locale, int version, String subjectTemplate, String bodyTemplate) {}