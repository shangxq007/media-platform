package com.example.platform.operation.application;

import com.example.platform.operation.operation.OperationParameters.SourceStreamInput;
import java.util.Map;

public record TimelineOperationStep(String definitionId, String version, TimelineBatchCommand.Target target,
        Map<String, Object> parameters, SourceStreamInput input) {
    public TimelineOperationStep(String definitionId, TimelineBatchCommand.Target target, Map<String, Object> parameters) {
        this(definitionId, null, target, parameters, null);
    }
}
