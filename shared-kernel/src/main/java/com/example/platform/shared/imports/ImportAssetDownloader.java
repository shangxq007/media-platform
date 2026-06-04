package com.example.platform.shared.imports;

/**
 * Downloads an asset from a URL during project import.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Validate the URL for SSRF safety before downloading</li>
 *   <li>Stream the download without buffering the entire file in memory</li>
 *   <li>Compute sizeBytes and checksum during download</li>
 *   <li>Respect timeout and max file size limits</li>
 *   <li>Write to a temporary file that the caller can clean up</li>
 * </ul>
 */
public interface ImportAssetDownloader {

    /**
     * Download an asset from the given URL.
     *
     * @param downloadUrl the URL to download from
     * @return downloaded asset with temp file, size, and checksum
     * @throws AssetDownloadException if download fails for any reason
     */
    DownloadedAsset download(String downloadUrl);

    /**
     * Clean up a previously downloaded asset's temporary file.
     */
    void cleanup(DownloadedAsset asset);
}
