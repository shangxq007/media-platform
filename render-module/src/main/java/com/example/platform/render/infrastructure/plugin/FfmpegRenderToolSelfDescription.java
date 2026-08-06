package com.example.platform.render.infrastructure.plugin;

import com.example.platform.extension.app.PluginDescriptorValidator;
import com.example.platform.extension.app.PluginHealthRegistry;
import com.example.platform.extension.app.PluginRegistryImpl;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginHealth;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.extension.domain.ResourceRequirement;
import com.example.platform.extension.domain.ToolEnvironmentReport;
import com.example.platform.extension.domain.ToolEnvironmentReport.ToolAvailability;
import com.example.platform.extension.app.ToolRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * FFmpeg/render tool self-description contributor (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>Describes the EXISTING render tool capability represented by the
 * ToolRegistry ffmpeg allowlist entry + DefaultProcessToolRunner execution.
 * This is a self-description only: it registers into {@link PluginRegistryImpl}
 * (the descriptor authority) at startup and derives plugin health from the
 * existing tool availability check ({@code ToolRegistry.validateEnvironment()}).
 * It does NOT create a second FFmpeg provider, does NOT change the existing
 * provider identity, does NOT reroute render invocation, does NOT change
 * command generation, ToolRegistry execution, DefaultProcessToolRunner, output
 * registration or Product READY behavior.</p>
 *
 * <p>Registration mechanism (frozen A074): Spring startup adapter — @Component
 * registering at ApplicationReadyEvent, mirroring the established
 * provider-extension pattern. No ExtensionRegistryService involvement, no
 * ToolRegistry change, no platform-app configuration change.</p>
 */
@Component
public class FfmpegRenderToolSelfDescription {

    private static final Logger log = LoggerFactory.getLogger(FfmpegRenderToolSelfDescription.class);

    public static final String PLUGIN_ID = "media.render.ffmpeg";
    public static final String PLUGIN_VERSION = "1.0.0";
    public static final String PLATFORM_API_VERSION = "1";
    public static final String VENDOR = "media-platform";
    public static final String CAPABILITY_MEDIA_RENDER = "media.render";
    public static final String CAPABILITY_SUBTITLE_BURN_IN = "subtitle.burn-in";
    public static final String CAPABILITY_CONTRACT_VERSION = "1";
    public static final String HANDLED_OBJECT_RENDER_EXECUTION_PLAN = "RenderExecutionPlan";
    public static final String HANDLED_OBJECT_SCHEMA_VERSION = "1";
    public static final String FFMPEG_TOOL_KEY = "ffmpeg";

    private final PluginRegistryImpl pluginRegistry;
    private final PluginHealthRegistry healthRegistry;
    private final ToolRegistry toolRegistry;

    public FfmpegRenderToolSelfDescription(
            PluginRegistryImpl pluginRegistry,
            PluginHealthRegistry healthRegistry,
            ToolRegistry toolRegistry) {
        this.pluginRegistry = pluginRegistry;
        this.healthRegistry = healthRegistry;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Startup registration at ApplicationReadyEvent (mirrors the 4 AI provider
     * extensions' self-registration pattern). Registration performs NO render
     * execution and instantiates NO second production FFmpeg runner.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerSelfDescription() {
        PluginDescriptor descriptor = buildDescriptor();
        var issues = pluginRegistry.register(descriptor);
        if (issues.isEmpty()) {
            PluginHealth.State health = deriveHealth();
            healthRegistry.record(PLUGIN_ID, health);
            log.info("Registered FFmpeg/render plugin self-description {} v{} "
                    + "capabilities=[{}, {}] health={}",
                    PLUGIN_ID, PLUGIN_VERSION,
                    CAPABILITY_MEDIA_RENDER, CAPABILITY_SUBTITLE_BURN_IN, health);
        } else {
            log.warn("FFmpeg/render plugin self-description rejected: {}",
                    issues.stream().map(i -> i.code().code()).toList());
        }
    }

    /**
     * Frozen self-description descriptor. Registers by stable capability IDs,
     * never by concrete provider-key strings (A082).
     */
    public PluginDescriptor buildDescriptor() {
        CapabilityDescriptor mediaRender = new CapabilityDescriptor(
                CAPABILITY_MEDIA_RENDER,
                CAPABILITY_CONTRACT_VERSION,
                "render",
                HANDLED_OBJECT_RENDER_EXECUTION_PLAN,
                "ArtifactReference",
                CapabilityDescriptor.InvocationMode.SYNC_ONLY);
        CapabilityDescriptor subtitleBurnIn = new CapabilityDescriptor(
                CAPABILITY_SUBTITLE_BURN_IN,
                CAPABILITY_CONTRACT_VERSION,
                "render",
                HANDLED_OBJECT_RENDER_EXECUTION_PLAN,
                "ArtifactReference",
                CapabilityDescriptor.InvocationMode.SYNC_ONLY);

        HandledObjectDescriptor handledObject = new HandledObjectDescriptor(
                HANDLED_OBJECT_RENDER_EXECUTION_PLAN,
                HANDLED_OBJECT_SCHEMA_VERSION,
                "com.example.platform.render.domain.timeline.compile.executionplan.RenderExecutionPlan",
                List.of("profile", "timelineSnapshotId"),
                List.of(),
                HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED);

        InvocationContract invocation = InvocationContract.syncOnlyDefault();

        List<PermissionDescriptor> permissions = List.of(
                new PermissionDescriptor("ffmpeg.execute"),
                new PermissionDescriptor("temporary-file.write"),
                new PermissionDescriptor("asset.read"),
                new PermissionDescriptor("storage.read"),
                new PermissionDescriptor("cpu.use"),
                new PermissionDescriptor("memory.use"),
                new PermissionDescriptor("font.read"));

        ResourceRequirement resources = ResourceRequirement.ffmpegDefaults();

        PluginRuntimeRequirement runtime = PluginRuntimeRequirement.trustedInProcess();

        PluginGuarantee guarantees = PluginGuarantee.ffmpegDefaults();

        return new PluginDescriptor(
                PLUGIN_ID,
                PLUGIN_VERSION,
                PLATFORM_API_VERSION,
                VENDOR,
                List.of(mediaRender, subtitleBurnIn),
                List.of(handledObject),
                invocation,
                permissions,
                resources,
                runtime,
                guarantees);
    }

    /**
     * Derives initial plugin health from the EXISTING tool check
     * ({@code ToolRegistry.validateEnvironment()}) — no new process execution,
     * no scheduled probing. ffmpeg available =&gt; HEALTHY; unavailable =&gt;
     * UNHEALTHY; not yet evaluated =&gt; UNKNOWN.
     */
    public PluginHealth.State deriveHealth() {
        ToolEnvironmentReport report = toolRegistry.validateEnvironment();
        for (ToolAvailability availability : report.tools()) {
            if (FFMPEG_TOOL_KEY.equals(availability.toolKey())) {
                return availability.available()
                        ? PluginHealth.State.HEALTHY
                        : PluginHealth.State.UNHEALTHY;
            }
        }
        return PluginHealth.State.UNKNOWN;
    }

    /** Exposed for tests: validator used for the self-description. */
    public static PluginDescriptorValidator validator() {
        return new PluginDescriptorValidator();
    }
}
