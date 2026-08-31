package com.example.platform.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Discovers application routes from Spring MVC's initialized runtime mapping registry. */
@Component
public final class RuntimeMvcRouteDiscovery {

    private static final String APPLICATION_PACKAGE_PREFIX = "com.example.platform.";
    private static final List<HttpMethod> ALL_HTTP_METHODS = List.of(HttpMethod.values());

    private final RequestMappingHandlerMapping handlerMapping;

    public RuntimeMvcRouteDiscovery(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    public List<RuntimeRoute> discoverApplicationRoutes() {
        List<RuntimeRoute> discovered = new ArrayList<>();
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith(APPLICATION_PACKAGE_PREFIX)) {
                continue;
            }
            for (String path : entry.getKey().getPatternValues()) {
                for (HttpMethod method : methodsFor(entry.getKey())) {
                    discovered.add(new RuntimeRoute(
                            method,
                            path,
                            handler.getBeanType().getName(),
                            handler.getMethod().toGenericString()));
                }
            }
        }
        return discovered.stream()
                .distinct()
                .sorted(Comparator.comparing(RuntimeRoute::path)
                        .thenComparing(route -> route.method().name())
                        .thenComparing(RuntimeRoute::controller)
                        .thenComparing(RuntimeRoute::handlerMethod))
                .toList();
    }

    private static Set<HttpMethod> methodsFor(RequestMappingInfo mapping) {
        Set<RequestMethod> declared = mapping.getMethodsCondition().getMethods();
        if (declared.isEmpty()) {
            return new LinkedHashSet<>(ALL_HTTP_METHODS);
        }
        Set<HttpMethod> methods = new LinkedHashSet<>();
        for (RequestMethod requestMethod : declared) {
            HttpMethod method = HttpMethod.valueOf(requestMethod.name());
            methods.add(method);
            if (method == HttpMethod.GET) {
                methods.add(HttpMethod.HEAD);
            }
        }
        return methods;
    }

    public record RuntimeRoute(
            HttpMethod method,
            String path,
            String controller,
            String handlerMethod) {

        public String displayName() {
            return method + " " + path + " -> " + controller + "#" + handlerMethod;
        }
    }
}
