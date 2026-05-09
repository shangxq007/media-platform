package com.example.platform.notification.domain;

import java.util.Map;

public record NotificationInboundEvent(String eventType, String subjectId, Map<String, Object> payload) {}