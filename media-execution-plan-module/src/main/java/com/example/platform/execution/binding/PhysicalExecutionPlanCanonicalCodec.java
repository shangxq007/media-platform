package com.example.platform.execution.binding;

import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.CanonicalWriter;
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.planning.ExecutionIoProjection.ExecutionIntentRef;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.render.domain.renderplan.LogicalArtifactId;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Bounded V1 canonical codec for {@link PhysicalExecutionPlan} — one deterministic
 * byte form plus its exact inverse.
 *
 * <p>Bounded V1 surface: plan identity/schema/fingerprint/digest, units with typed
 * inputs, outputs, dependencies, capability requirements, execution intents, exact
 * sample window, exact execution coverage, propagated extent and the deterministic
 * cacheability flag.
 *
 * <p>Explicitly NOT representable (the durable form must never drop semantics, so
 * these fail closed with {@link UnsupportedPersistedConstructException}):
 * {@code ColorDescription} and {@code RasterSampleDescription} on output requirements,
 * and every {@code RenderMaterializationRequirement} payload. Those payloads are not
 * produced by the bounded slice; a plan carrying them is rejected rather than
 * persisted lossily.
 */
public final class PhysicalExecutionPlanCanonicalCodec {

    public static final String FORMAT = "p25a.physical-execution-plan.v1";

    private PhysicalExecutionPlanCanonicalCodec() {
    }

    public static byte[] encode(PhysicalExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return encodeText(plan).getBytes(StandardCharsets.UTF_8);
    }

    public static PhysicalExecutionPlan decode(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        FrameReader reader = FrameReader.of(payload);
        reader.requireTag(FORMAT);
        String formatVersion = reader.field("formatVersion");
        ExecutionPlanId planId = new ExecutionPlanId(reader.field("planId"));
        ExecutionPlanSchemaVersion schema =
                ExecutionPlanSchemaVersion.of(reader.fieldInt("schemaVersion"));
        RenderPlanFingerprint fingerprint = new RenderPlanFingerprint(reader.field("planFingerprint"));
        int unitCount = reader.listCount();
        List<PhysicalPlanUnit> units = new ArrayList<>(unitCount);
        for (int i = 0; i < unitCount; i++) {
            units.add(unit(reader.nested()));
        }
        FrameReader extentReader = reader.optionalNested();
        RenderExtent extent = extentReader == null ? null : extent(extentReader);
        PhysicalExecutionPlanDigest digest =
                new PhysicalExecutionPlanDigest(reader.field("digest"));
        if (reader.hasRemaining()) {
            throw new UnsupportedPersistedConstructException("trailing bytes after physical plan");
        }
        return new PhysicalExecutionPlan(formatVersion, planId, schema, fingerprint, units, extent, digest);
    }

    /** Lowercase SHA-256 hex of the canonical encoded plan bytes. */
    public static String digestHex(PhysicalExecutionPlan plan) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(encode(plan)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String encodeText(PhysicalExecutionPlan plan) {
        List<String> units = new ArrayList<>(plan.units().size());
        for (PhysicalPlanUnit unit : plan.units()) {
            units.add(unit(unit));
        }
        return new CanonicalWriter()
                .tag(FORMAT)
                .field("formatVersion", plan.formatVersion())
                .field("planId", plan.planId().value())
                .field("schemaVersion", Integer.toString(plan.schemaVersion().value()))
                .field("planFingerprint", plan.planFingerprint().sha256Hex())
                .list(units)
                .optional(plan.propagatedExtent() != null, extent(plan.propagatedExtent()))
                .field("digest", plan.digest().sha256Hex())
                .build();
    }

    // ---------- unit ----------

    private static String unit(PhysicalPlanUnit unit) {
        return new CanonicalWriter()
                .tag("PhysicalPlanUnit")
                .field("stepId", unit.stepId().value())
                .field("logicalNodeId", unit.logicalNodeId())
                .field("sourceRenderNodeId", unit.sourceRenderNodeId().value())
                .field("sourceRenderNodeKind", unit.sourceRenderNodeKind().canonicalName())
                .field("operationKey", unit.operationKey())
                .list(unit.typedInputs().stream().map(PhysicalExecutionPlanCanonicalCodec::input).toList())
                .list(unit.typedOutputs().stream().map(PhysicalExecutionPlanCanonicalCodec::output).toList())
                .list(unit.typedDependencies().stream().map(PhysicalExecutionPlanCanonicalCodec::edge).toList())
                .optional(unit.temporalWindow() != null, window(unit.temporalWindow()))
                .optional(unit.executionCoverage() != null, coverage(unit.executionCoverage()))
                .list(unit.capabilityRequirementRefs().stream()
                        .map(PhysicalExecutionPlanCanonicalCodec::capabilityRef).toList())
                .list(unit.executionIntentRefs().stream()
                        .map(PhysicalExecutionPlanCanonicalCodec::intentRef).toList())
                .optional(unit.propagatedExtent() != null, extent(unit.propagatedExtent()))
                .field("deterministicallyCacheable", Boolean.toString(unit.deterministicallyCacheable()))
                .build();
    }

    private static PhysicalPlanUnit unit(FrameReader reader) {
        reader.requireTag("PhysicalPlanUnit");
        ExecutionStepId stepId = new ExecutionStepId(reader.field("stepId"));
        String logicalNodeId = reader.field("logicalNodeId");
        RenderNodeId renderNodeId = new RenderNodeId(reader.field("sourceRenderNodeId"));
        RenderNodeKind kind = nodeKind(reader.field("sourceRenderNodeKind"));
        String operationKey = reader.field("operationKey");
        List<InputBinding> inputs = new ArrayList<>();
        int inputCount = reader.listCount();
        for (int i = 0; i < inputCount; i++) {
            inputs.add(input(reader.nested()));
        }
        List<OutputDeclaration> outputs = new ArrayList<>();
        int outputCount = reader.listCount();
        for (int i = 0; i < outputCount; i++) {
            outputs.add(output(reader.nested()));
        }
        List<LogicalDependencyEdge> dependencies = new ArrayList<>();
        int dependencyCount = reader.listCount();
        for (int i = 0; i < dependencyCount; i++) {
            dependencies.add(edge(reader.nested()));
        }
        FrameReader windowReader = reader.optionalNested();
        RenderSampleWindow temporalWindow = windowReader == null ? null : window(windowReader);
        FrameReader coverageReader = reader.optionalNested();
        RenderExecutionCoverage coverage = coverageReader == null ? null : coverage(coverageReader);
        List<CapabilityRequirementRef> capabilityRefs = new ArrayList<>();
        int capabilityCount = reader.listCount();
        for (int i = 0; i < capabilityCount; i++) {
            capabilityRefs.add(capabilityRef(reader.nested()));
        }
        List<ExecutionIntentRef> intentRefs = new ArrayList<>();
        int intentCount = reader.listCount();
        for (int i = 0; i < intentCount; i++) {
            intentRefs.add(intentRef(reader.nested()));
        }
        FrameReader unitExtentReader = reader.optionalNested();
        RenderExtent unitExtent = unitExtentReader == null ? null : extent(unitExtentReader);
        boolean cacheable = reader.fieldBoolean("deterministicallyCacheable");
        return new PhysicalPlanUnit(
                stepId, logicalNodeId, renderNodeId, kind, operationKey,
                inputs, outputs, dependencies, temporalWindow, coverage,
                capabilityRefs, intentRefs, unitExtent, cacheable);
    }

    // ---------- inputs / outputs / dependencies ----------

    private static String input(InputBinding binding) {
        return new CanonicalWriter()
                .tag("InputBinding")
                .field("inputId", binding.inputId().value())
                .field("consumerLogicalNodeId", binding.consumerLogicalNodeId())
                .optional(binding.consumerStepId() != null,
                        binding.consumerStepId() == null ? null : binding.consumerStepId().value())
                .optional(binding.consumerRenderNodeId() != null,
                        binding.consumerRenderNodeId() == null ? null : binding.consumerRenderNodeId().value())
                .optional(binding.producerLogicalNodeId() != null, binding.producerLogicalNodeId())
                .optional(binding.producerStepId() != null,
                        binding.producerStepId() == null ? null : binding.producerStepId().value())
                .optional(binding.producerRenderNodeId() != null,
                        binding.producerRenderNodeId() == null ? null : binding.producerRenderNodeId().value())
                .optional(binding.dependencyVariant() != null,
                        binding.dependencyVariant() == null
                                ? null : dependency(binding.dependencyVariant()))
                .optional(binding.sourceArtifact() != null,
                        binding.sourceArtifact() == null ? null : sourceArtifact(binding.sourceArtifact()))
                .optional(binding.requiredSampleWindow() != null,
                        binding.requiredSampleWindow() == null
                                ? null : window(binding.requiredSampleWindow()))
                .build();
    }

    private static InputBinding input(FrameReader reader) {
        reader.requireTag("InputBinding");
        ExecutionInputId inputId = new ExecutionInputId(reader.field("inputId"));
        String consumerLogicalNodeId = reader.field("consumerLogicalNodeId");
        String consumerStepId = reader.optional();
        String consumerRenderNodeId = reader.optional();
        String producerLogicalNodeId = reader.optional();
        String producerStepId = reader.optional();
        String producerRenderNodeId = reader.optional();
        FrameReader dependencyReader = reader.optionalNested();
        RenderDependency dependency = dependencyReader == null ? null : dependency(dependencyReader);
        FrameReader sourceReader = reader.optionalNested();
        RenderArtifactReference.SourceArtifact source =
                sourceReader == null ? null : sourceArtifact(sourceReader);
        FrameReader windowReader = reader.optionalNested();
        RenderSampleWindow window = windowReader == null ? null : window(windowReader);
        return new InputBinding(
                inputId,
                consumerLogicalNodeId,
                consumerStepId == null ? null : new ExecutionStepId(consumerStepId),
                consumerRenderNodeId == null ? null : new RenderNodeId(consumerRenderNodeId),
                producerLogicalNodeId,
                producerStepId == null ? null : new ExecutionStepId(producerStepId),
                producerRenderNodeId == null ? null : new RenderNodeId(producerRenderNodeId),
                dependency,
                source,
                window);
    }

    private static String output(OutputDeclaration declaration) {
        if (!declaration.materializationRequirements().isEmpty()) {
            throw new UnsupportedPersistedConstructException("RenderMaterializationRequirement");
        }
        return new CanonicalWriter()
                .tag("OutputDeclaration")
                .field("outputId", declaration.outputId().value())
                .field("logicalNodeId", declaration.logicalNodeId())
                .field("sourceRenderNodeId", declaration.sourceRenderNodeId().value())
                .list(declaration.outputRequirements().stream()
                        .map(PhysicalExecutionPlanCanonicalCodec::outputRequirement).toList())
                .list(List.of())
                .list(declaration.intermediateArtifactExpectations().stream()
                        .map(PhysicalExecutionPlanCanonicalCodec::intermediateArtifact).toList())
                .list(declaration.finalArtifactExpectations().stream()
                        .map(PhysicalExecutionPlanCanonicalCodec::finalArtifact).toList())
                .build();
    }

    private static OutputDeclaration output(FrameReader reader) {
        reader.requireTag("OutputDeclaration");
        ExecutionOutputId outputId = new ExecutionOutputId(reader.field("outputId"));
        String logicalNodeId = reader.field("logicalNodeId");
        RenderNodeId renderNodeId = new RenderNodeId(reader.field("sourceRenderNodeId"));
        List<RenderOutputRequirement> requirements = new ArrayList<>();
        int requirementCount = reader.listCount();
        for (int i = 0; i < requirementCount; i++) {
            requirements.add(outputRequirement(reader.nested()));
        }
        int materializationCount = reader.listCount();
        if (materializationCount != 0) {
            throw new UnsupportedPersistedConstructException("RenderMaterializationRequirement");
        }
        List<RenderArtifactReference.IntermediateArtifactExpectation> intermediates = new ArrayList<>();
        int intermediateCount = reader.listCount();
        for (int i = 0; i < intermediateCount; i++) {
            intermediates.add(intermediateArtifact(reader.nested()));
        }
        List<RenderArtifactReference.FinalArtifactExpectation> finals = new ArrayList<>();
        int finalCount = reader.listCount();
        for (int i = 0; i < finalCount; i++) {
            finals.add(finalArtifact(reader.nested()));
        }
        return new OutputDeclaration(
                outputId, logicalNodeId, renderNodeId, requirements, List.of(), intermediates, finals);
    }

    static String edge(LogicalDependencyEdge edge) {
        return new CanonicalWriter()
                .tag("LogicalDependencyEdge")
                .field("edgeId", edge.edgeId().value())
                .field("producerLogicalNodeId", edge.producerLogicalNodeId())
                .field("consumerLogicalNodeId", edge.consumerLogicalNodeId())
                .field("producerRenderNodeId", edge.producerRenderNodeId().value())
                .field("consumerRenderNodeId", edge.consumerRenderNodeId().value())
                .field("dependency", dependency(edge.dependencyVariant()))
                .build();
    }

    static LogicalDependencyEdge edge(FrameReader reader) {
        reader.requireTag("LogicalDependencyEdge");
        return new LogicalDependencyEdge(
                new ExecutionEdgeId(reader.field("edgeId")),
                reader.field("producerLogicalNodeId"),
                reader.field("consumerLogicalNodeId"),
                new RenderNodeId(reader.field("producerRenderNodeId")),
                new RenderNodeId(reader.field("consumerRenderNodeId")),
                dependency(reader.fieldNested("dependency")));
    }

    // ---------- requirement refs ----------

    private static String capabilityRef(CapabilityRequirementRef reference) {
        CapabilityRequirement requirement = reference.declaration();
        List<String> alternatives = requirement.alternatives().stream()
                .map(CapabilityId::value)
                .toList();
        return new CanonicalWriter()
                .tag("CapabilityRequirementRef")
                .field("capabilityId", requirement.capabilityId().value())
                .field("range", range(requirement.contractRange()))
                .field("required", Boolean.toString(requirement.required()))
                .list(alternatives)
                .build();
    }

    private static CapabilityRequirementRef capabilityRef(FrameReader reader) {
        reader.requireTag("CapabilityRequirementRef");
        CapabilityId capabilityId = CapabilityId.of(reader.field("capabilityId"));
        ContractVersionRange range = range(reader.fieldNested("range"));
        boolean required = reader.fieldBoolean("required");
        int count = reader.listCount();
        List<CapabilityId> alternatives = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            alternatives.add(CapabilityId.of(reader.text()));
        }
        return new CapabilityRequirementRef(
                CapabilityRequirement.of(capabilityId, range, required, alternatives));
    }

    private static String intentRef(ExecutionIntentRef reference) {
        RenderExecutionRequirement requirement = reference.declaration();
        return new CanonicalWriter()
                .tag("ExecutionIntentRef")
                .field("gpu", requirement.gpu().name())
                .field("determinism", requirement.determinism().name())
                .field("sandboxedIntent", Boolean.toString(requirement.sandboxedIntent()))
                .build();
    }

    private static ExecutionIntentRef intentRef(FrameReader reader) {
        reader.requireTag("ExecutionIntentRef");
        RenderExecutionRequirement.GpuRequirement gpu = enumValue(
                RenderExecutionRequirement.GpuRequirement.class, reader.field("gpu"));
        RenderExecutionRequirement.RenderDeterminismClass determinism = enumValue(
                RenderExecutionRequirement.RenderDeterminismClass.class, reader.field("determinism"));
        boolean sandboxed = reader.fieldBoolean("sandboxedIntent");
        return new ExecutionIntentRef(new RenderExecutionRequirement(gpu, determinism, sandboxed));
    }

    // ---------- render value types ----------

    private static String outputRequirement(RenderOutputRequirement requirement) {
        if (requirement.colorDescription().isPresent()) {
            throw new UnsupportedPersistedConstructException("ColorDescription");
        }
        if (requirement.rasterSample().isPresent()) {
            throw new UnsupportedPersistedConstructException("RasterSampleDescription");
        }
        return new CanonicalWriter()
                .tag("RenderOutputRequirement")
                .field("role", requirement.role().name())
                .build();
    }

    private static RenderOutputRequirement outputRequirement(FrameReader reader) {
        reader.requireTag("RenderOutputRequirement");
        return RenderOutputRequirement.of(
                enumValue(RenderOutputRole.class, reader.field("role")));
    }

    private static String sourceArtifact(RenderArtifactReference.SourceArtifact source) {
        return new CanonicalWriter()
                .tag("SourceArtifact")
                .field("artifactId", source.artifactId().value())
                .field("contentDigest", source.contentDigest().value())
                .build();
    }

    private static RenderArtifactReference.SourceArtifact sourceArtifact(FrameReader reader) {
        reader.requireTag("SourceArtifact");
        return new RenderArtifactReference.SourceArtifact(
                new ArtifactId(reader.field("artifactId")),
                ContentDigest.sha256(reader.field("contentDigest")));
    }

    private static String intermediateArtifact(
            RenderArtifactReference.IntermediateArtifactExpectation expectation) {
        return new CanonicalWriter()
                .tag("IntermediateArtifactExpectation")
                .field("logicalId", expectation.logicalId().value())
                .field("role", expectation.role().name())
                .build();
    }

    private static RenderArtifactReference.IntermediateArtifactExpectation intermediateArtifact(
            FrameReader reader) {
        reader.requireTag("IntermediateArtifactExpectation");
        return new RenderArtifactReference.IntermediateArtifactExpectation(
                new LogicalArtifactId(reader.field("logicalId")),
                enumValue(RenderOutputRole.class, reader.field("role")));
    }

    private static String finalArtifact(RenderArtifactReference.FinalArtifactExpectation expectation) {
        return new CanonicalWriter()
                .tag("FinalArtifactExpectation")
                .field("role", expectation.role().name())
                .build();
    }

    private static RenderArtifactReference.FinalArtifactExpectation finalArtifact(FrameReader reader) {
        reader.requireTag("FinalArtifactExpectation");
        return new RenderArtifactReference.FinalArtifactExpectation(
                enumValue(RenderOutputRole.class, reader.field("role")));
    }

    // ---------- typed dependencies ----------

    private static String dependency(RenderDependency dependency) {
        CanonicalWriter writer = new CanonicalWriter().tag("RenderDependency");
        if (dependency instanceof RenderDependency.DecodedFrames) {
            return writer.field("variant", "DECODED_FRAMES").build();
        }
        if (dependency instanceof RenderDependency.EffectInput) {
            return writer.field("variant", "EFFECT_INPUT").build();
        }
        if (dependency instanceof RenderDependency.AudioInput audio) {
            return writer.field("variant", "AUDIO_INPUT")
                    .field("trackId", audio.mixInput().trackId())
                    .field("clipId", audio.mixInput().clipId())
                    .build();
        }
        if (dependency instanceof RenderDependency.SubtitleRaster) {
            return writer.field("variant", "SUBTITLE_RASTER").build();
        }
        if (dependency instanceof RenderDependency.CompositeInput) {
            return writer.field("variant", "COMPOSITE_INPUT").build();
        }
        throw new UnsupportedPersistedConstructException(
                "RenderDependency " + dependency.getClass().getName());
    }

    private static RenderDependency dependency(FrameReader reader) {
        reader.requireTag("RenderDependency");
        String variant = reader.field("variant");
        return switch (variant) {
            case "DECODED_FRAMES" -> new RenderDependency.DecodedFrames();
            case "EFFECT_INPUT" -> new RenderDependency.EffectInput();
            case "AUDIO_INPUT" -> new RenderDependency.AudioInput(
                    AudioMixInput.of(reader.field("trackId"), reader.field("clipId")));
            case "SUBTITLE_RASTER" -> new RenderDependency.SubtitleRaster();
            case "COMPOSITE_INPUT" -> new RenderDependency.CompositeInput();
            default -> throw new UnsupportedPersistedConstructException("RenderDependency " + variant);
        };
    }

    // ---------- exact time / extent / node kind ----------

    private static String extent(RenderExtent extent) {
        return time("RenderExtent", extent.start(), extent.end(), extent.frameRate());
    }

    private static RenderExtent extent(FrameReader reader) {
        reader.requireTag("RenderExtent");
        MediaTime start = time(reader);
        MediaTime end = time(reader);
        return new RenderExtent(start, end, frameRate(reader));
    }

    private static String window(RenderSampleWindow window) {
        return time("RenderSampleWindow", window.start(), window.end(), window.frameRate());
    }

    private static RenderSampleWindow window(FrameReader reader) {
        reader.requireTag("RenderSampleWindow");
        MediaTime start = time(reader);
        MediaTime end = time(reader);
        return new RenderSampleWindow(start, end, frameRate(reader));
    }

    private static String coverage(RenderExecutionCoverage coverage) {
        return time("RenderExecutionCoverage", coverage.start(), coverage.end(), coverage.frameRate());
    }

    private static RenderExecutionCoverage coverage(FrameReader reader) {
        reader.requireTag("RenderExecutionCoverage");
        MediaTime start = time(reader);
        MediaTime end = time(reader);
        return new RenderExecutionCoverage(start, end, frameRate(reader));
    }

    private static String time(String tag, MediaTime start, MediaTime end, FrameRate frameRate) {
        return new CanonicalWriter()
                .tag(tag)
                .exactLong(start.ticks())
                .exactLong(start.timeScale())
                .exactLong(end.ticks())
                .exactLong(end.timeScale())
                .exactLong(exact(frameRate.numerator()))
                .exactLong(frameRate.denominator())
                .build();
    }

    private static MediaTime time(FrameReader reader) {
        return MediaTime.ofTicks(reader.exactLong(), reader.exactLong());
    }

    private static FrameRate frameRate(FrameReader reader) {
        return FrameRate.of(java.math.BigInteger.valueOf(reader.exactLong()), reader.exactLong());
    }

    private static long exact(java.math.BigInteger value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException overflow) {
            throw new UnsupportedPersistedConstructException("frame rate numerator out of range");
        }
    }

    private static String range(ContractVersionRange range) {
        return new CanonicalWriter()
                .tag("ContractVersionRange")
                .exactLong(range.min().major())
                .exactLong(range.min().minor())
                .exactLong(range.max().major())
                .exactLong(range.max().minor())
                .build();
    }

    private static ContractVersionRange range(FrameReader reader) {
        reader.requireTag("ContractVersionRange");
        ContractVersion min = ContractVersion.of(reader.exactInt(), reader.exactInt());
        ContractVersion max = ContractVersion.of(reader.exactInt(), reader.exactInt());
        return ContractVersionRange.between(min, max);
    }

    private static RenderNodeKind nodeKind(String canonicalName) {
        return switch (canonicalName) {
            case "SOURCE" -> new RenderNodeKind.Source();
            case "DECODE" -> new RenderNodeKind.Decode();
            case "TRANSFORM" -> new RenderNodeKind.Transform();
            case "EFFECT" -> new RenderNodeKind.Effect();
            case "TRANSITION" -> new RenderNodeKind.Transition();
            case "AUDIO_PROCESS" -> new RenderNodeKind.AudioProcess();
            case "AUDIO_MIX" -> new RenderNodeKind.AudioMix();
            case "TIMED_TEXT" -> new RenderNodeKind.TimedText();
            case "COMPOSITE" -> new RenderNodeKind.Composite();
            case "COLOR_TRANSFORM" -> new RenderNodeKind.ColorTransform();
            case "MUX" -> new RenderNodeKind.Mux();
            case "OUTPUT" -> new RenderNodeKind.Output();
            default -> throw new UnsupportedPersistedConstructException("RenderNodeKind " + canonicalName);
        };
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException failure) {
            throw new UnsupportedPersistedConstructException(type.getSimpleName() + " value " + name);
        }
    }
}
