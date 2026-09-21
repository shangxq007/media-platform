package com.example.platform.extension.api.port;
import com.example.platform.extension.domain.RoutingRule;
import java.util.List;
public interface ExtensionRoutingQueries { List<RoutingRule> getRules(String key);
 record Route(String scene,int priority,boolean enabled) {}
 List<Route> routes(String key,com.example.platform.shared.authorization.CanonicalActor actor); }
