package com.example.platform.artifact.app;
import com.example.platform.artifact.domain.ArtifactMediaType;
import java.util.Optional;
/** Accepted output identity and content resolution through Artifact then Storage owners. */
public interface ArtifactOutputRead {
 Optional<ArtifactOutputReference> find(ArtifactScope scope);
 Content read(ArtifactOutputReference reference);
 record Content(byte[] bytes,ArtifactMediaType mediaType,String contentType,String fileName) {
  public Content {bytes=bytes.clone();java.util.Objects.requireNonNull(mediaType);
   var format=com.example.platform.artifact.domain.ArtifactOutputFormat.require(contentType);
   if(format.mediaType()!=mediaType || !format.fileName().equals(fileName))throw new IllegalArgumentException("output representation metadata mismatch");}
  @Override public byte[] bytes(){return bytes.clone();}
 }
}
