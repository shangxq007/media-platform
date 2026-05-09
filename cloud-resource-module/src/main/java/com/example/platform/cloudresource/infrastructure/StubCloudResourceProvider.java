package com.example.platform.cloudresource.infrastructure;

import com.example.platform.cloudresource.domain.CloudResourceProvider;
import org.springframework.stereotype.Component;

@Component
public class StubCloudResourceProvider implements CloudResourceProvider {
    @Override
    public String code() {
        return "stub";
    }

    @Override
    public String ensureBucket(String logicalName) {
        return "stub://bucket/" + logicalName;
    }
}
