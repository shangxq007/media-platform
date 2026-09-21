package com.example.platform.render.app;
import com.example.platform.render.api.ExportOptionQuery;
import com.example.platform.render.infrastructure.ExportPolicyService;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class ExportOptionProjection implements ExportOptionQuery {
 private final ExportPolicyService policy;
 public ExportOptionProjection(ExportPolicyService policy){this.policy=policy;}
 public List<Option> options(String tier){return policy.getAvailablePresets(tier).stream().map(p->new Option(p.name(),policy.isPresetAvailable(p.name(),tier),policy.getDefaultPreset(tier).name(),policy.resolveProvider(p.name(),tier))).toList();}
}
