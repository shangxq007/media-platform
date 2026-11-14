package com.example.platform.web.media;

import com.example.platform.artifact.app.*;
import com.example.platform.config.render.*;
import com.example.platform.delivery.app.DeliveryService;
import com.example.platform.delivery.app.DeliveryFailure;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.operation.invocation.*;
import com.example.platform.render.api.GraphRenderLifecycle;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.operation.TimelineClipEditCommand;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries;
import com.example.platform.web.render.TimelineProjectAuthorizationService;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Stateless MCP JSON-RPC product ingress. All business authority remains in the existing owners. */
@RestController
@RequestMapping("/api/mcp/media")
public class McpMediaToolsController {
    private final CanonicalActorResolver actors;
    private final RevisionRenderSubmissionService submissions;
    private final TimelineRevisionQueries revisions;
    private final TimelineProjectAuthorizationService projectAccess;
    private final OperationInvocationPort operations;
    private final RenderJobService jobs;
    private final GraphRenderLifecycle lifecycle;
    private final DeliveryService deliveries;
    private final ArtifactApplicationService artifacts;
    private final ArtifactProjectAuthorizationPort artifactAccess;
    private final ObjectMapper json;
    private final Validator validator;
    public McpMediaToolsController(CanonicalActorResolver actors,RevisionRenderSubmissionService submissions,
            TimelineRevisionQueries revisions,TimelineProjectAuthorizationService projectAccess,
            OperationInvocationPort operations,RenderJobService jobs,GraphRenderLifecycle lifecycle,
            DeliveryService deliveries,ArtifactApplicationService artifacts,
            ArtifactProjectAuthorizationPort artifactAccess,ObjectMapper json,Validator validator) {
        this.actors=actors;this.submissions=submissions;this.revisions=revisions;this.projectAccess=projectAccess;
        this.operations=operations;this.jobs=jobs;this.lifecycle=lifecycle;this.deliveries=deliveries;
        this.artifacts=artifacts;this.artifactAccess=artifactAccess;this.json=json;this.validator=validator;
    }
    public record Rpc(String jsonrpc,Object id,String method,Map<String,Object> params) {}
    public record RevisionArgs(@NotBlank String projectId,@NotBlank String revisionId) {}
    public record OperationArgs(@NotBlank String projectId,@NotNull @Valid TimelineClipEditCommand command) {}
    public record RenderArgs(@NotNull @Valid RenderJobSubmissionHttpRequest request,@NotBlank @Size(max=255) String idempotencyKey) {}
    public record IncrementalArgs(@NotNull @Valid IncrementalRenderHttpRequest request,@NotBlank @Size(max=255) String idempotencyKey) {}
    public record JobArgs(@NotBlank String projectId,@NotBlank String renderJobId) {}
    public record DeliveryArgs(@NotBlank String projectId,@NotBlank String sourceRenderJobId,String artifactId,
            @NotBlank @Size(max=200) String idempotencyKey) {}
    public record DeliveryResultArgs(@NotBlank String projectId,@NotBlank String deliveryId) {}
    public record ArtifactArgs(@NotBlank String projectId,@NotBlank String renderJobId,String artifactId) {}

    @PostMapping
    public ResponseEntity<?> rpc(@RequestBody Rpc request) {
        actor();
        if(!"2.0".equals(request.jsonrpc()) || request.method()==null || request.method().isBlank() || (request.id()!=null && !(request.id() instanceof String) && !(request.id() instanceof Number)))return response(error(request.id(),-32600,"Invalid JSON-RPC request"));
        if("notifications/initialized".equals(request.method()) && request.id()==null)return ResponseEntity.accepted().build();
        if(request.id()==null)return response(error(null,-32600,"Request id required"));
        try {
            Object result=switch(request.method()) {
                case "initialize" -> Map.of("protocolVersion","2025-03-26","capabilities",Map.of("tools",Map.of("listChanged",false)),
                        "serverInfo",Map.of("name","media-platform","version","1.0"));
                case "ping" -> Map.of();
                case "tools/list" -> Map.of("tools",catalog());
                case "tools/call" -> call(json.valueToTree(request.params()));
                default -> null;
            };
            return response(result==null?error(request.id(),-32601,"Method not found"):envelope(request.id(),"result",result));
        } catch(IllegalArgumentException invalid) {
            return response(error(request.id(),-32602,"Invalid tool arguments"));
        }
    }
    private Object call(JsonNode params) {
        if(params==null || !params.hasNonNull("name") || !params.has("arguments"))throw new IllegalArgumentException();
        String name=params.get("name").asText();JsonNode args=params.get("arguments");
        if(!TOOL_NAMES.contains(name))throw new IllegalArgumentException("Unknown tool");
        try {
            Object value=switch(name) {
                case "timeline.revision" -> revision(bind(args,RevisionArgs.class),false);
                case "timeline.export_otio" -> revision(bind(args,RevisionArgs.class),true);
                case "timeline.apply_operation" -> {
                    var a=bind(args,OperationArgs.class);var actor=actor();
                    yield operations.invoke(a.command().toOperationRequest(a.projectId()),
                            new OperationInvocationContext(actor,a.command().commandId(),null,"mcp-media"));
                }
                case "render.submit" -> {
                    var a=bind(args,RenderArgs.class);
                    yield submissions.submit(a.request(),a.request().extent().toExtent(),RenderInitiator.from(actor()),a.idempotencyKey()).response();
                }
                case "render.submit_incremental" -> {
                    var a=bind(args,IncrementalArgs.class);
                    yield submissions.submitIncremental(a.request(),a.request().extent().toExtent(),RenderInitiator.from(actor()),a.idempotencyKey()).response();
                }
                case "render.job" -> {
                    var a=bind(args,JobArgs.class);var job=jobs.getByIdAndProject(tenant(),a.projectId(),a.renderJobId());
                    var status=lifecycle.find(tenant(),a.projectId(),a.renderJobId());
                    var result=new LinkedHashMap<String,Object>();result.put("renderJobId",job.id());result.put("projectId",job.projectId());
                    result.put("status",job.status());result.put("finalArtifactId",status.map(GraphRenderLifecycle.Status::finalArtifactId).orElse(null));
                    result.put("failureCode",status.map(GraphRenderLifecycle.Status::failureCode).orElse(null));yield result;
                }
                case "delivery.create" -> {
                    var a=bind(args,DeliveryArgs.class);yield deliveries.create(a.projectId(),a.sourceRenderJobId(),a.artifactId(),a.idempotencyKey()).delivery();
                }
                case "delivery.result" -> {
                    var a=bind(args,DeliveryResultArgs.class);var content=deliveries.result(a.projectId(),a.deliveryId());
                    yield Map.of("delivery",deliveries.get(a.projectId(),a.deliveryId()),"contentType",content.contentType(),"contentLength",content.bytes().length);
                }
                case "artifact.query" -> {
                    var a=bind(args,ArtifactArgs.class);jobs.getByIdAndProject(tenant(),a.projectId(),a.renderJobId());
                    artifactAccess.requireRead(tenant(),a.projectId());var scope=new ArtifactScope(tenant(),a.projectId(),a.renderJobId());
                    yield a.artifactId()==null?Map.of("items",artifacts.listArtifacts(scope,100)):
                            artifacts.requestAccess(scope,new ArtifactId(a.artifactId()));
                }
                default -> throw new IllegalArgumentException();
            };
            return toolResult(value,false);
        } catch(IllegalArgumentException invalid) {throw invalid;}
        catch(RuntimeException failure) {
            String code=failure instanceof DeliveryFailure d?d.code():
                    failure instanceof OperationInvocationException o?o.code().name():
                    failure instanceof com.example.platform.shared.web.PlatformException p?p.getErrorCode().code():
                    failure instanceof com.example.platform.identity.api.authorization.AuthorizationDeniedException?"AUTHORIZATION_DENIED":
                    failure instanceof org.springframework.web.server.ResponseStatusException s?safeStatusCode(s):
                    failure instanceof ArtifactApplicationService.ArtifactAccessException a?a.failure().name():"TOOL_FAILED";
            return toolResult(Map.of("code",code),true);
        }
    }
    private Object revision(RevisionArgs a,boolean otio) {
        projectAccess.requireRead(tenant(),a.projectId());
        var info=revisions.findById(a.projectId(),tenant(),a.revisionId()).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
        if(otio)return Map.of("projectId",a.projectId(),"revisionId",a.revisionId(),"otioJson",new com.example.platform.timeline.otio.TimelineOtioExportService(revisions).export(tenant(),a.projectId(),a.revisionId()));
        var snapshot=revisions.getRevisionSnapshotPayload(a.projectId(),tenant(),a.revisionId()).orElseThrow();
        return Map.of("projectId",a.projectId(),"revisionId",info.id(),"contentHash",info.contentHash(),"revisionNumber",info.revisionNumber(),"canonicalTimelineJson",snapshot.canonicalTimelineJson());
    }
    private <T> T bind(JsonNode args,Class<T> type) {
        try {
            T value=json.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(args);
            if(!validator.validate(value).isEmpty())throw new IllegalArgumentException();return value;
        } catch(java.io.IOException | jakarta.validation.ValidationException e) {throw new IllegalArgumentException("Invalid tool arguments");}
    }
    private Object toolResult(Object value,boolean failed) {
        JsonNode data=json.valueToTree(value);
        return Map.of("content",List.of(Map.of("type","text","text",data.toString())),"structuredContent",json.convertValue(data,Object.class),"isError",failed);
    }
    private CanonicalActor actor() {
        var actor=actors.resolveCurrentActor().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if(actor.isSystem() || !Objects.equals(actor.tenantId(),TenantContext.get()))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        return actor;
    }
    private String tenant(){return actor().tenantId();}
    private ResponseEntity<?> response(Object value){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(value);}
    private static Map<String,Object> envelope(Object id,String field,Object value) {
        var result=new LinkedHashMap<String,Object>();result.put("jsonrpc","2.0");result.put("id",id);result.put(field,value);return result;
    }
    private static Map<String,Object> error(Object id,int code,String message){return envelope(id,"error",Map.of("code",code,"message",message));}
    private static String safeStatusCode(org.springframework.web.server.ResponseStatusException failure) {
        String reason=failure.getReason();return reason!=null && reason.matches("[A-Z][A-Z0-9_]{0,99}")?reason:"HTTP_"+failure.getStatusCode().value();
    }
    private static final List<String> TOOL_NAMES=List.of("timeline.revision","timeline.export_otio","timeline.apply_operation","render.submit","render.submit_incremental","render.job","delivery.create","delivery.result","artifact.query");
    private static Map<String,Object> object(Map<String,Object> properties,String... required) {
        return Map.of("type","object","properties",properties,"required",List.of(required),"additionalProperties",false);
    }
    private static Map<String,Object> input(String... fields) {
        var properties=new LinkedHashMap<String,Object>();for(String field:fields)properties.put(field,Map.of("type","string","minLength",1));
        return object(properties,fields);
    }
    private static Map<String,Object> renderInput(boolean incremental) {
        var properties=new LinkedHashMap<String,Object>();
        properties.put("extent",object(Map.of("startMillis",Map.of("type","integer","minimum",0),"endMillis",Map.of("type","integer","minimum",0),
                "frameRateNumerator",Map.of("type","integer","minimum",1),"frameRateDenominator",Map.of("type","integer","minimum",1)),
                "startMillis","endMillis","frameRateNumerator","frameRateDenominator"));
        properties.put("outputFormat",Map.of("type","string","enum",List.of("MP4")));properties.put("profile",Map.of("type","string","minLength",1));
        List<String> identities=incremental?List.of("targetRevisionId","referenceRevisionId","referenceRenderJobId"):List.of("revisionId");
        identities.forEach(field->properties.put(field,Map.of("type","string","minLength",1)));
        return object(Map.of("request",object(properties,properties.keySet().toArray(String[]::new)),"idempotencyKey",Map.of("type","string","minLength",1,"maxLength",255)),"request","idempotencyKey");
    }
    private static Map<String,Object> operationInput() {
        var command=new LinkedHashMap<String,Object>();for(String field:List.of("baseRevisionId","baseContentHash","clipId","commandId"))command.put(field,Map.of("type","string","minLength",1));
        command.put("type",Map.of("type","string","enum",List.of("TRIM_CLIP","MOVE_CLIP","DELETE_CLIP")));
        command.put("delta",Map.of("type",List.of("string","null")));command.put("absolute",Map.of("type",List.of("boolean","null")));
        command.put("edge",Map.of("enum",Arrays.asList("START","END",null)));
        return object(Map.of("projectId",Map.of("type","string","minLength",1),"command",object(command,"type","baseRevisionId","baseContentHash","clipId","commandId")),"projectId","command");
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> artifactInput(boolean create) {
        String[] required=create?new String[]{"projectId","sourceRenderJobId","idempotencyKey"}:new String[]{"projectId","renderJobId"};
        var schema=new LinkedHashMap<>(input(required));var properties=new LinkedHashMap<>((Map<String,Object>)schema.get("properties"));
        properties.put("artifactId",Map.of("type",List.of("string","null")));schema.put("properties",properties);return schema;
    }
    private static List<Map<String,Object>> catalog() {
        var definitions=Map.of("timeline.revision",input("projectId","revisionId"),"timeline.export_otio",input("projectId","revisionId"),
                "timeline.apply_operation",operationInput(),"render.submit",renderInput(false),"render.submit_incremental",renderInput(true),
                "render.job",input("projectId","renderJobId"),"delivery.create",artifactInput(true),"delivery.result",input("projectId","deliveryId"),"artifact.query",artifactInput(false));
        return TOOL_NAMES.stream().map(name->Map.<String,Object>of("name",name,"description","Authorized media product operation: "+name,"inputSchema",definitions.get(name))).toList();
    }
}
