package com.example.platform.workflow.plan;

import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.MediaTimeJsonCodec;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.*;

/**
 * Transport codec for the published typed invocation values; never enables arbitrary class-name
 * typing.
 */
final class WorkflowOperationValueJson {
    private WorkflowOperationValueJson() {}

    public static void configure(ObjectMapper mapper) {
        var module = new SimpleModule("operation-exact-values");
        module.addSerializer(MediaTime.class, new MediaTimeJsonCodec.Serializer());
        module.addDeserializer(MediaTime.class, new MediaTimeJsonCodec.Deserializer());
        mapper.registerModule(module);
        register(mapper, OperationParameters.class, new HashSet<>());
        register(mapper, OperationTargetRequest.class, new HashSet<>());
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "variant")
    private interface Variant {}

    private static void register(ObjectMapper mapper, Class<?> type, Set<Class<?>> seen) {
        if (!seen.add(type)) return;
        if (type.isSealed()) {
            boolean ownsDiscriminator = type.isAnnotationPresent(JsonTypeInfo.class);
            if (!ownsDiscriminator) mapper.addMixIn(type, Variant.class);
            for (Class<?> child : type.getPermittedSubclasses()) {
                if (!ownsDiscriminator)
                    mapper.registerSubtypes(new NamedType(child, child.getSimpleName()));
                register(mapper, child, seen);
            }
        }
        if (type.isRecord())
            for (var field : type.getRecordComponents()) register(mapper, field.getType(), seen);
    }
}
