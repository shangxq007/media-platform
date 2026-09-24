package com.example.platform.artifact.domain.typed;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class TypedArtifactValidator {
    private TypedArtifactValidator() {}
    public static List<TypedArtifactValidationError> validate(ConversionContract contract, List<TypedArtifact> inputs, List<TypedArtifact> outputs, JsonNode params, String tenant, String workspace, String contractVersion) {
        List<TypedArtifactValidationError> e=new ArrayList<>();
        if (tenant == null || tenant.isBlank()) e.add(new TypedArtifactValidationError("SOURCE_SCOPE_MISSING", "tenantId", "tenant scope is required"));
        if (workspace == null || workspace.isBlank()) e.add(new TypedArtifactValidationError("SOURCE_SCOPE_MISSING", "workspaceId", "workspace scope is required"));
        if (contract==null) {e.add(new TypedArtifactValidationError("CONTRACT_MISSING","contract","conversion contract is required"));return e;}
        if (!Objects.equals(contract.contractVersion(),contractVersion)) e.add(new TypedArtifactValidationError("STALE_CONTRACT_VERSION","contractVersion","contract version does not match"));
        if (inputs==null || inputs.size()<contract.input().minimumCount() || inputs.size()>contract.input().maximumCount()) e.add(new TypedArtifactValidationError("INPUT_CARDINALITY","inputs","input cardinality is unsupported"));
        if (outputs==null || outputs.size()<contract.output().minimumCount() || outputs.size()>contract.output().maximumCount()) e.add(new TypedArtifactValidationError("OUTPUT_CARDINALITY","outputs","output cardinality is unsupported"));
        e.addAll(ArtifactIdentityGuard.validateUniqueAndScoped(inputs, tenant, workspace, "inputs"));
        e.addAll(ArtifactIdentityGuard.validateUniqueAndScoped(outputs, tenant, workspace, "outputs"));
        if(inputs!=null) for(int i=0;i<inputs.size();i++){TypedArtifact a=inputs.get(i); if(a != null && !contract.input().accepts(a))e.add(new TypedArtifactValidationError("INPUT_INCOMPATIBLE","inputs["+i+"]","input artifact does not satisfy contract"));}
        if(outputs!=null) for(int i=0;i<outputs.size();i++){TypedArtifact a=outputs.get(i); if(a != null && !contract.output().accepts(a))e.add(new TypedArtifactValidationError("OUTPUT_INCOMPATIBLE","outputs["+i+"]","output artifact does not satisfy contract"));}
        if(params==null || !params.isObject()) e.add(new TypedArtifactValidationError("PARAMETERS_INVALID","parameters","parameters must be an object"));
        else {Set<String> names=new HashSet<>(); for(ParameterDefinition p:contract.parameters()){names.add(p.name());JsonNode v=params.get(p.name());if(v==null&&p.required())e.add(new TypedArtifactValidationError("PARAMETER_REQUIRED","parameters."+p.name(),"required parameter missing"));if(v!=null&&!accepts(p.type(),v))e.add(new TypedArtifactValidationError("PARAMETER_TYPE_INVALID","parameters."+p.name(),"parameter type invalid"));}params.fieldNames().forEachRemaining(n->{if(!names.contains(n))e.add(new TypedArtifactValidationError("PARAMETER_UNSUPPORTED","parameters."+n,"unsupported parameter"));});}
        return List.copyOf(e);
    }
    private static boolean accepts(ParameterDefinition.ParameterType t,JsonNode n){return switch(t){case STRING,ENUM->n.isTextual();case INTEGER->n.isIntegralNumber();case NUMBER->n.isNumber();case BOOLEAN->n.isBoolean();case OBJECT->n.isObject();case ARRAY->n.isArray();};}
}
