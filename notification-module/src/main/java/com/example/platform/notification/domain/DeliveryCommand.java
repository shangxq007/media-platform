package com.example.platform.notification.domain;

import java.util.Map;

public record DeliveryCommand(String eventId, String channel, String subject, String body, Map<String, Object> metadata) {}