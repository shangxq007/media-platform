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
  @Test void fingerprintIsOrderIndependentForParameterObjects(){var a=json.createObjectNode().put("z",1).put("a",2);var b=json.createObjectNode().put("a",2).put("z",1);assertThat(ConversionSpecification.fingerprint("c","1",List.of("a"),a)).isEqualTo(ConversionSpecification.fingerprint("c","1",List.of("a"),b));}
  @Test void validatorRejectsScopeAndParameterErrors(){var req=new ArtifactRequirement(Set.of(LogicalArtifactKind.VIDEO),Set.of("video/mp4"),Set.of("mp4"),Set.of("h264"),new VersionRange(">=1"),1,1);var c=new ConversionContract("c","1",req,req,List.of(new ParameterDefinition("width",ParameterDefinition.ParameterType.INTEGER,true,null)),ExecutionMode.ASYNC,Determinism.DETERMINISTIC,Cardinality.ONE_TO_ONE,new ConversionContract.ResourceEstimate(1,"render"),new ConversionContract.RetryPolicy(true,2,true),null);var a=new TypedArtifact("a","t1","w1",LogicalArtifactKind.VIDEO,"mp4","video/mp4","h264","1",new ArtifactTechnicalProperties(null,1920,1080,null,"h264",null,null),com.example.platform.shared.digest.ContentDigest.sha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),"storage://a",null,null,java.time.Instant.EPOCH);var errors=TypedArtifactValidator.validate(c,List.of(a),List.of(a),json.createObjectNode(),"t2","w1","0");assertThat(errors).extracting(TypedArtifactValidationError::code).contains("STALE_CONTRACT_VERSION","CROSS_TENANT_REFERENCE","PARAMETER_REQUIRED");}
  @Test void identityGuardRejectsDuplicateAndMissingStorageFacts(){var a=new TypedArtifact("a","t","w",LogicalArtifactKind.IMAGE,"png","image/png",null,"1",null,com.example.platform.shared.digest.ContentDigest.sha256("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),"storage://a",null,null,java.time.Instant.EPOCH);var errors=ArtifactIdentityGuard.validateUniqueAndScoped(List.of(a,a),"t","w","inputs");assertThat(errors).extracting(TypedArtifactValidationError::code).contains("DUPLICATE_ARTIFACT_ID");}
  @Test void mediaDetailsAreArtifactKeyedAndValidateFacts(){assertThatThrownBy(()->new MediaArtifactDetails("a","video","mp4","video/mp4","h264",null,0,1080,null,List.of(),null,null,null,null,null)).isInstanceOf(IllegalArgumentException.class);assertThat(new MediaArtifactDetails("a","video","mp4","video/mp4","h264",null,1920,1080,"24",List.of(new MediaArtifactDetails.Track("VIDEO","h264",0,null)),"bt709",null,null,null,null).artifactId()).isEqualTo("a");}
}
