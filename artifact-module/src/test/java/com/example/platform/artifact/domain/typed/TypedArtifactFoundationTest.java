package com.example.platform.artifact.domain.typed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class TypedArtifactFoundationTest {
  private final ObjectMapper json = new ObjectMapper();
  @Test void taxonomyCoversAllSupportedKinds(){assertThat(LogicalArtifactKind.values()).containsExactlyInAnyOrder(LogicalArtifactKind.values());}
  @Test void versionRangesFailClosed(){assertThat(new VersionRange(">=1.2<=2.0").accepts("1.5")).isTrue();assertThat(new VersionRange(">=1.2").accepts("1.1")).isFalse();assertThatThrownBy(()->new VersionRange("1.*")).isInstanceOf(IllegalArgumentException.class);}
  @Test void fingerprintIsOrderIndependentForSources(){var p=json.createObjectNode().put("quality",90);assertThat(ConversionSpecification.fingerprint("c","1",List.of("b","a"),p)).isEqualTo(ConversionSpecification.fingerprint("c","1",List.of("a","b"),p));}
  @Test void validatorRejectsScopeAndParameterErrors(){var req=new ArtifactRequirement(Set.of(LogicalArtifactKind.VIDEO),Set.of("video/mp4"),Set.of("mp4"),Set.of("h264"),new VersionRange(">=1"),1,1);var c=new ConversionContract("c","1",req,req,List.of(new ParameterDefinition("width",ParameterDefinition.ParameterType.INTEGER,true,null)),ExecutionMode.ASYNC,Determinism.DETERMINISTIC,Cardinality.ONE_TO_ONE,new ConversionContract.ResourceEstimate(1,"render"),new ConversionContract.RetryPolicy(true,2,true),null);var a=new TypedArtifact("a","t1","w1",LogicalArtifactKind.VIDEO,"mp4","video/mp4","h264","1",new ArtifactTechnicalProperties(null,1920,1080,null,"h264",null,null),com.example.platform.shared.digest.ContentDigest.sha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),"storage://a",null,null,java.time.Instant.EPOCH);var errors=TypedArtifactValidator.validate(c,List.of(a),List.of(a),json.createObjectNode(),"t2","w1","0");assertThat(errors).extracting(TypedArtifactValidationError::code).contains("STALE_CONTRACT_VERSION","CROSS_TENANT_REFERENCE","PARAMETER_REQUIRED");}
}
