package com.messenger.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for a real object store. It mints a stable, unique asset
 * URL of the form {@code {base-url}/api/media/{id}} and retains the raw bytes so
 * {@code GET /api/media/{id}} can stream them back for the frontend. Swapping in
 * S3 later only requires a new {@link MediaStorageService} bean.
 */
@Service
public class MockMediaStorageService implements MediaStorageService {

    private final String baseUrl;
    private final Map<String, byte[]> store = new ConcurrentHashMap<>();
    private final Map<String, String> contentTypes = new ConcurrentHashMap<>();

    public MockMediaStorageService(@Value("${app.media.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store an empty file");
        }
        String id = UUID.randomUUID().toString();
        try {
            store.put(id, file.getBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read uploaded file", ex);
        }
        contentTypes.put(id, file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        return baseUrl + "/api/media/" + id;
    }

    @Override
    public void delete(String assetUrl) {
        if (assetUrl == null) {
            return;
        }
        int slash = assetUrl.lastIndexOf('/');
        if (slash >= 0) {
            String id = assetUrl.substring(slash + 1);
            store.remove(id);
            contentTypes.remove(id);
        }
    }

    /** @return the stored bytes for an asset id, or {@code null} if unknown. */
    public byte[] bytes(String id) {
        return store.get(id);
    }

    /** @return the content type recorded for an asset id. */
    public String contentType(String id) {
        return contentTypes.getOrDefault(id, "application/octet-stream");
    }
}
