package com.example.platform.artifact.app;
import java.util.List;
/** Bounded internal reverse-reference query for the existing administrative/deletion-check boundary. */
public interface ArtifactOutputReferenceIndex {
 List<ArtifactOutputReference> findByStorageUri(String storageUri,String projectId,int limit);
}
