package com.example.platform.delivery.app;
import com.example.platform.artifact.app.*;
import java.io.InputStream;
import java.util.Optional;
import org.springframework.stereotype.Service;
@Service
public class DeliverySourceResolver {
 private final ArtifactOutputRead artifacts;
 public DeliverySourceResolver(ArtifactOutputRead artifacts){this.artifacts=artifacts;}
 public record SourceFile(String fileName,String contentType,long length,InputStream stream) implements AutoCloseable {
    public void close() throws java.io.IOException {stream.close();}
 }
 public Optional<ArtifactOutputReference> find(ArtifactScope scope){return artifacts.find(scope);}
 public Optional<SourceFile> open(ArtifactOutputReference reference){
    try{
        var content=artifacts.read(reference);byte[] bytes=content.bytes();
        String mime=switch(content.mediaType()){case VIDEO->"video/mp4";case AUDIO->"audio/wav";case IMAGE->"image/png";default->"application/octet-stream";};
        return Optional.of(new SourceFile("output.mp4",mime,bytes.length,new java.io.ByteArrayInputStream(bytes)));
    }catch(IllegalArgumentException|IllegalStateException unavailable){return Optional.empty();}
 }
}
