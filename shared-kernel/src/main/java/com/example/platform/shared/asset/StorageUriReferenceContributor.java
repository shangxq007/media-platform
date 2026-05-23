package com.example.platform.shared.asset;

import java.util.List;

/**
 * Optional plug-in for delete-check / tombstone guards (delivery, render jobs, etc.).
 */
public interface StorageUriReferenceContributor {

    String contributorId();

    List<StorageUriReferenceHit> findReferences(String storageUri, String projectId);
}
