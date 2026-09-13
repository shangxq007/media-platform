package com.example.platform.artifact.app;
import com.example.platform.artifact.domain.ArtifactMediaType;
import java.util.Optional;
/** Accepted output identity and content resolution through Artifact then Storage owners. */
public interface ArtifactOutputRead {
 Optional<ArtifactOutputReference> find(ArtifactScope scope);
 Content read(ArtifactOutputReference reference);
 record Content(byte[] bytes,ArtifactMediaType mediaType) {
  public Content {bytes=bytes.clone();java.util.Objects.requireNonNull(mediaType);}
  @Override public byte[] bytes(){return bytes.clone();}
 }
}
