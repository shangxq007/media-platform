package com.example.platform.notification.app;

import com.example.platform.shared.web.ConfigurableErrorCode;
import java.util.Map;

/** Localized transport codes owned by the notification bounded context. */
final class NotificationErrorCodes {
    static final ConfigurableErrorCode EVENT_NOT_SUBSCRIBABLE = code("NOTIFICATION-400-001", 4002001, 400, "Notification event is not subscribable", "通知事件不可订阅");
    static final ConfigurableErrorCode SUBSCRIPTION_NOT_FOUND = code("NOTIFICATION-404-002", 4042002, 404, "Notification subscription not found", "通知订阅不存在");
    static final ConfigurableErrorCode CHANNEL_NOT_FOUND = code("NOTIFICATION-404-003", 4042003, 404, "Notification channel binding not found", "通知渠道绑定不存在");
    static final ConfigurableErrorCode CHANNEL_UNSUPPORTED = code("NOTIFICATION-400-003", 4002003, 400, "Notification channel type unsupported", "不支持的通知渠道类型");
    static final ConfigurableErrorCode CHANNEL_TEST_FAILED = code("NOTIFICATION-400-005", 4002005, 400, "Notification channel test failed", "通知渠道测试失败");
    static final ConfigurableErrorCode WEBHOOK_URL_INVALID = code("NOTIFICATION-400-006", 4002006, 400, "Invalid webhook URL", "Webhook URL 无效");
    static final ConfigurableErrorCode WEBHOOK_PRIVATE_IP_BLOCKED = code("NOTIFICATION-403-001", 4032001, 403, "Webhook URL resolved to private/internal IP and was blocked", "Webhook URL 解析到内部/私有 IP，已被阻止");
    static final ConfigurableErrorCode CRITICAL_CANNOT_DISABLE = code("NOTIFICATION-400-010", 4002010, 400, "Critical notification event cannot be disabled", "关键通知事件不可关闭");

    private NotificationErrorCodes() {}

    private static ConfigurableErrorCode code(String value, int numericCode, int status, String en, String zh) {
        return new ConfigurableErrorCode(value, numericCode, Map.of("en", en, "zh", zh), "notification", status);
    }
}
