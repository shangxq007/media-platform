package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageQuery;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade with no state of its own. All commands and reads retain the
 * canonical tenant/principal/period identity and delegate to QuotaUsageAuthority.
 */
@Service
public class QuotaUsageService {

    private final QuotaUsageAuthority authority;

    public QuotaUsageService(QuotaUsageAuthority authority) {
        this.authority = authority;
    }

    public QuotaUsageResult execute(QuotaUsageCommand command) {
        return authority.execute(command);
    }

    public long currentUsage(QuotaUsageQuery query) {
        return authority.currentUsage(query);
    }
}
