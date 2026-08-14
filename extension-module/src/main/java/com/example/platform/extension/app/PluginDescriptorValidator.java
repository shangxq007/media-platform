package com.example.platform.extension.app;

import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.CapabilityNamespaceValidator;
import com.example.platform.extension.domain.ExtensionTrustLevel;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginDescriptorValidationIssue;
import com.example.platform.extension.domain.PluginDiagnosticCode;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic descriptor validator (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>Validates in the exact frozen order and emits stable diagnostics
 * PLG-001 through PLG-016. Rejection behavior: invalid descriptor -> NOT
 * registered (0 registry mutation, 0 partial state); a subsequent valid
 * registration still passes.</p>
 *
 * <p>Diagnostics are machine-readable, stable-ordered and safe (no raw
 * descriptor payloads, no stack traces). TimelineDiagnostic is deliberately
 * NOT reused.</p>
 */
@Component
public class PluginDescriptorValidator {

    /** Supported platform API version for P1 (frozen). */
    public static final Set<String> SUPPORTED_PLATFORM_API_VERSIONS = Set.of("1");

    /** Recognized handled-object type IDs for P1 (frozen: RenderExecutionPlan). */
    public static final Set<String> RECOGNIZED_HANDLED_OBJECT_TYPE_IDS = Set.of("RenderExecutionPlan");

    /** Stable plugin ID pattern: reverse-dns style, lower-case alphanumeric segments. */
    private static final Pattern PLUGIN_ID_PATTERN = Pattern.compile("[a-z0-9]+(\\.[a-z0-9]+)+");

    /** Semantic-version-shaped plugin version pattern (evidence-selected representation). */
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?(\\+[0-9A-Za-z.-]+)?");

    /** Optional provider-implementation availability check (step 13). */
    private final Predicate<String> implementationAvailability;

    public PluginDescriptorValidator() {
        this(null);
    }

    /**
     * @param implementationAvailability optional check resolving a plugin ID to
     *                                   implementation availability; when null,
     *                                   step 13 is not evaluated (declaration-only).
     */
    public PluginDescriptorValidator(Predicate<String> implementationAvailability) {
        this.implementationAvailability = implementationAvailability;
    }

    /**
     * Validates a descriptor in the frozen deterministic order.
     *
     * @param descriptor descriptor to validate
     * @return ordered diagnostics; empty when valid
     */
    public List<PluginDescriptorValidationIssue> validate(PluginDescriptor descriptor) {
        List<PluginDescriptorValidationIssue> issues = new ArrayList<>();

        // 1. pluginId (valid format, non-blank)
        if (descriptor.pluginId() == null || descriptor.pluginId().isBlank()) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_001, "pluginId", issues.size() + 1));
        } else if (!PLUGIN_ID_PATTERN.matcher(descriptor.pluginId()).matches()) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_001, "pluginId", issues.size() + 1));
        }

        // 2. pluginVersion (semver-shaped, non-blank)
        if (descriptor.pluginVersion() == null || descriptor.pluginVersion().isBlank()) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_002, "pluginVersion", issues.size() + 1));
        } else if (!VERSION_PATTERN.matcher(descriptor.pluginVersion()).matches()) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_002, "pluginVersion", issues.size() + 1));
        }

        // 3. platformApiVersion (supported range check)
        if (descriptor.platformApiVersion() == null
                || !SUPPORTED_PLATFORM_API_VERSIONS.contains(descriptor.platformApiVersion())) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_003, "platformApiVersion", issues.size() + 1));
        }

        // 4. vendor (non-blank)
        if (descriptor.vendor() == null || descriptor.vendor().isBlank()) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_004, "vendor", issues.size() + 1));
        }

        // 5. capability list (non-empty)
        if (descriptor.capabilities() == null || descriptor.capabilities().isEmpty()) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_005, "capabilities", issues.size() + 1));
        }

        // 6. duplicate capability IDs within plugin
        Set<String> seenCapabilityIds = new HashSet<>();
        if (descriptor.capabilities() != null) {
            for (CapabilityDescriptor capability : descriptor.capabilities()) {
                if (!seenCapabilityIds.add(capability.capabilityId())) {
                    issues.add(PluginDescriptorValidationIssue.error(
                            PluginDiagnosticCode.PLG_006,
                            "capabilities[" + capability.capabilityId() + "].capabilityId",
                            issues.size() + 1));
                }
                // #16 (R1): namespace validation (platform-reserved vs vendor reverse-DNS)
                if (!CapabilityNamespaceValidator.isValid(capability.capabilityId())) {
                    issues.add(PluginDescriptorValidationIssue.error(
                            PluginDiagnosticCode.PLG_017,
                            "capabilities[" + capability.capabilityId() + "].capabilityId",
                            issues.size() + 1));
                }
            }
        }

        // 7. handled objects (non-empty; recognized type IDs)
        if (descriptor.handledObjects() == null || descriptor.handledObjects().isEmpty()) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_007, "handledObjects", issues.size() + 1));
        } else {
            for (HandledObjectDescriptor handledObject : descriptor.handledObjects()) {
                if (!RECOGNIZED_HANDLED_OBJECT_TYPE_IDS.contains(handledObject.objectTypeId())) {
                    issues.add(PluginDescriptorValidationIssue.error(
                            PluginDiagnosticCode.PLG_008,
                            "handledObjects[" + handledObject.objectTypeId() + "].objectTypeId",
                            issues.size() + 1));
                }
            }
        }

        // 8. invocation contract (valid enum/flag values)
        if (descriptor.invocationContract() == null
                || !descriptor.invocationContract().synchronous()) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_009, "invocationContract", issues.size() + 1));
        }

        // 9. permissions (recognized IDs)
        if (descriptor.permissions() != null) {
            for (PermissionDescriptor permission : descriptor.permissions()) {
                if (!PermissionDescriptor.KNOWN_PERMISSION_IDS.contains(permission.permissionId())) {
                    issues.add(PluginDescriptorValidationIssue.error(
                            PluginDiagnosticCode.PLG_010,
                            "permissions[" + permission.permissionId() + "].permissionId",
                            issues.size() + 1));
                }
            }
        }

        // 10. resources (bounds valid)
        if (descriptor.resourceRequirements() == null
                || !descriptor.resourceRequirements().boundsValid()) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_011, "resourceRequirements", issues.size() + 1));
        }

        // 11. runtime requirements (trust accepted; environment recognized)
        if (descriptor.runtimeRequirements() == null
                || descriptor.runtimeRequirements().runtime()
                        != PluginRuntimeRequirement.RuntimeMode.TRUSTED_IN_PROCESS
                || descriptor.runtimeRequirements().executionEnvironment()
                        != PluginRuntimeRequirement.ExecutionEnvironment.LOCAL_PROCESS) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_012, "runtimeRequirements", issues.size() + 1));
        }
        if (descriptor.runtimeRequirements() != null
                && descriptor.runtimeRequirements().trustLevel() != ExtensionTrustLevel.FULLY_TRUSTED) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_016,
                    "runtimeRequirements.trustLevel", issues.size() + 1));
        }

        // 12. guarantees (legal values; prohibitions enforced)
        if (descriptor.guarantees() == null || !descriptor.guarantees().legal()) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_013, "guarantees", issues.size() + 1));
        }

        // 13. provider implementation availability (entry implementation resolvable)
        if (implementationAvailability != null && descriptor.pluginId() != null
                && !implementationAvailability.test(descriptor.pluginId())) {
            issues.add(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_014, "providerImplementation", issues.size() + 1));
        }

        return List.copyOf(issues);
    }
}
