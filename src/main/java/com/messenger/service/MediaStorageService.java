package com.messenger.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction over binary media storage. The production implementation would
 * stream to S3/GCS/Azure Blob; {@link MockMediaStorageService} keeps everything
 * in-memory for local development and demos while preserving the same contract.
 */
public interface MediaStorageService {

    /**
     * Store the uploaded file and return its publicly reachable asset URL.
     *
     * @param file the multipart upload
     * @return a URL (or stable key) that can be persisted on the message
     */
    String store(MultipartFile file);

    /** Remove a previously stored asset, if present. No-op when missing. */
    void delete(String assetUrl);
}
