package com.example.platform.federation.nlq.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class ResultRedactionService {

    private static final Logger log = LoggerFactory.getLogger(ResultRedactionService.class);

    private static final String EMAIL_MASK = "email_mask";
    private static final String PHONE_MASK = "phone_mask";
    private static final String USER_ID_HASH = "user_id_hash";
    private static final String IP_MASK = "ip_mask";
    private static final String FULL_REDACT = "full_redact";
    private static final String PARTIAL_REDACT = "partial_redact";

    public void redact(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                if (value == null) continue;

                String strategy = resolveStrategy(fieldName);
                if (strategy == null) continue;

                entry.setValue(applyRedaction(value.toString(), strategy));
            }
        }

        log.debug("ResultRedactionService: redacted {} rows", rows.size());
    }

    private String resolveStrategy(String fieldName) {
        String lower = fieldName.toLowerCase();
        if (lower.contains("email")) return EMAIL_MASK;
        if (lower.contains("phone") || lower.contains("mobile")) return PHONE_MASK;
        if (lower.contains("user_id") || lower.equals("userid")) return USER_ID_HASH;
        if (lower.contains("ip_address") || lower.equals("ip")) return IP_MASK;
        if (lower.contains("password") || lower.contains("secret") || lower.contains("token")
                || lower.contains("api_key") || lower.contains("credential")) return FULL_REDACT;
        if (lower.contains("name") || lower.contains("address")) return PARTIAL_REDACT;
        return null;
    }

    private String applyRedaction(String value, String strategy) {
        if (value == null || value.isEmpty()) return value;

        return switch (strategy) {
            case EMAIL_MASK -> maskEmail(value);
            case PHONE_MASK -> maskPhone(value);
            case USER_ID_HASH -> hashUserId(value);
            case IP_MASK -> maskIp(value);
            case FULL_REDACT -> "***REDACTED***";
            case PARTIAL_REDACT -> partialRedact(value);
            default -> value;
        };
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***" + email.substring(atIndex);
        return email.charAt(0) + "***" + email.substring(atIndex - 1);
    }

    private String maskPhone(String phone) {
        if (phone.length() <= 4) return "****";
        return "****" + phone.substring(phone.length() - 4);
    }

    private String hashUserId(String userId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(userId.getBytes());
            return "h_" + HexFormat.of().withUpperCase().formatHex(hash).substring(0, 12);
        } catch (Exception e) {
            return "h_" + userId.hashCode();
        }
    }

    private String maskIp(String ip) {
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) return "***";
        return ip.substring(0, lastDot) + ".*";
    }

    private String partialRedact(String value) {
        if (value.length() <= 2) return "**";
        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }
}
