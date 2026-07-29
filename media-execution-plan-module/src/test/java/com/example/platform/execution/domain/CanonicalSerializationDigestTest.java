package com.example.platform.execution.domain;

import com.example.platform.execution.domain.operation.MediaInspectionOperation;
import com.example.platform.execution.domain.operation.TranscodeOperation;
import com.example.platform.execution.domain.operation.TrimOperation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.platform.execution.domain.MediaExecutionPlanFixtures.*;

@DisplayName("Canonical Serialization and Digest")
class CanonicalSerializationDigestTest {

    @Nested
    @DisplayName("Canonical Form")
    class CanonicalFormTest {

        @Test
        @DisplayName("plan canonical form is deterministic")
        void planCanonicalFormDeterministic() {
            MediaExecutionPlan plan1 = validChain();
            MediaExecutionPlan plan2 = validChain();

            assertThat(plan1.canonicalForm()).isEqualTo(plan2.canonicalForm());
        }

        @Test
        @DisplayName("step canonical form is deterministic")
        void stepCanonicalFormDeterministic() {
            var step1 = step("step-1", MediaInspectionOperation.minimal());
            var step2 = step("step-1", MediaInspectionOperation.minimal());

            assertThat(step1.canonicalForm()).isEqualTo(step2.canonicalForm());
        }

        @Test
        @DisplayName("edge canonical form is deterministic")
        void edgeCanonicalFormDeterministic() {
            var edge1 = dataEdge("e-1", "step-1", "step-2");
            var edge2 = dataEdge("e-1", "step-1", "step-2");

            assertThat(edge1.canonicalForm()).isEqualTo(edge2.canonicalForm());
        }

        @Test
        @DisplayName("input binding canonical form is deterministic")
        void inputBindingCanonicalFormDeterministic() {
            var input1 = primaryInput("in-1", "art-001");
            var input2 = primaryInput("in-1", "art-001");

            assertThat(input1.canonicalForm()).isEqualTo(input2.canonicalForm());
        }

        @Test
        @DisplayName("output declaration canonical form is deterministic")
        void outputDeclarationCanonicalFormDeterministic() {
            var output1 = primaryOutput("out-1", "step-1");
            var output2 = primaryOutput("out-1", "step-1");

            assertThat(output1.canonicalForm()).isEqualTo(output2.canonicalForm());
        }
    }

    @Nested
    @DisplayName("Digest Calculation")
    class DigestCalculationTest {

        @Test
        @DisplayName("same plan yields same digest")
        void samePlanSameDigest() {
            MediaExecutionPlan plan1 = validChain();
            MediaExecutionPlan plan2 = validChain();

            assertThat(plan1.digest()).isEqualTo(plan2.digest());
        }

        @Test
        @DisplayName("different plans yield different digests")
        void differentPlansDifferentDigests() {
            MediaExecutionPlan plan1 = validChain();
            MediaExecutionPlan plan2 = validBranch();

            assertThat(plan1.digest()).isNotEqualTo(plan2.digest());
        }

        @Test
        @DisplayName("digest calculator produces consistent results")
        void digestCalculatorConsistent() {
            MediaExecutionPlan plan = validChain();

            ExecutionPlanDigest digest1 = ExecutionPlanDigestCalculator.calculate(plan);
            ExecutionPlanDigest digest2 = ExecutionPlanDigestCalculator.calculate(plan);

            assertThat(digest1).isEqualTo(digest2);
        }

        @Test
        @DisplayName("digest verification passes for valid plan")
        void digestVerificationPasses() {
            MediaExecutionPlan plan = validChain();

            boolean valid = ExecutionPlanDigestCalculator.verifyDigest(plan);
            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("digest is hex string")
        void digestIsHexString() {
            MediaExecutionPlan plan = validChain();

            String digest = plan.digest().value();
            assertThat(digest).matches("[0-9a-f]+");
        }
    }

    @Nested
    @DisplayName("Canonical Serializer")
    class CanonicalSerializerTest {

        @Test
        @DisplayName("SHA-256 produces 64 hex chars")
        void sha256Produces64HexChars() {
            String hash = ExecutionPlanCanonicalSerializer.sha256Hex("test");
            assertThat(hash).hasSize(64);
            assertThat(hash).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("SHA-256 is deterministic")
        void sha256Deterministic() {
            String hash1 = ExecutionPlanCanonicalSerializer.sha256Hex("test");
            String hash2 = ExecutionPlanCanonicalSerializer.sha256Hex("test");

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("different inputs produce different hashes")
        void differentInputsDifferentHashes() {
            String hash1 = ExecutionPlanCanonicalSerializer.sha256Hex("test1");
            String hash2 = ExecutionPlanCanonicalSerializer.sha256Hex("test2");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("digestPlan produces consistent hash")
        void digestPlanConsistent() {
            MediaExecutionPlan plan = validChain();

            String digest1 = ExecutionPlanCanonicalSerializer.digestPlan(plan);
            String digest2 = ExecutionPlanCanonicalSerializer.digestPlan(plan);

            assertThat(digest1).isEqualTo(digest2);
        }
    }
}
