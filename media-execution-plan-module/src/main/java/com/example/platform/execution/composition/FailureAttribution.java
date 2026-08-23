package com.example.platform.execution.composition;

import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import java.util.Objects;

/**
 * Immutable Phase 5 structure for attributing a future provider failure.
 * This type performs no runtime failure handling and never guesses a member.
 */
public sealed interface FailureAttribution
        permits FailureAttribution.MemberAttribution,
                FailureAttribution.UnknownMemberAttribution {

    /** Typed attribution to the original canonical #21 unit. */
    record MemberAttribution(PhysicalPlanUnit member) implements FailureAttribution {
        public MemberAttribution {
            Objects.requireNonNull(member, "member");
        }
    }

    /** Explicit result when a future failure cannot be mapped to a member. */
    enum UnknownMemberAttribution implements FailureAttribution {
        UNKNOWN_MEMBER_ATTRIBUTION
    }
}
