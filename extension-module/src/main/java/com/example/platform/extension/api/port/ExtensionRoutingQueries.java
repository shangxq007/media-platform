package com.example.platform.extension.api.port;
import com.example.platform.extension.domain.RoutingRule;
import java.util.List;
public interface ExtensionRoutingQueries { List<RoutingRule> getRules(String key); }
