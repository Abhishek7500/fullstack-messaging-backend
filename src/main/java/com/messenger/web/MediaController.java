package com.messenger.web;

import com.messenger.dto.ChatEvents.MediaUploadResponse;
import com.messenger.service.MediaStorageService;
import com.messenger.service.MockMediaStorageService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Mock media upload + retrieval. Files are stored in-memory by
 * {@link MockMediaStorageService} and streamed back from {@code GET /api/media/{id}}
 * so the frontend can render them during development.
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaStorageService mediaStorageService;
    private final MockMediaStorageService mockMediaStorageService;

    public MediaController(
            MediaStorageService mediaStorageService,
            MockMediaStorageService mockMediaStorageService
    ) {
        this.mediaStorageService = mediaStorageService;
        this.mockMediaStorageService = mockMediaStorageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MediaUploadResponse upload(@RequestParam("file") MultipartFile file) {
        String url = mediaStorageService.store(file);
        return new MediaUploadResponse(url, file.getContentType(), file.getSize());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        byte[] bytes = mockMediaStorageService.bytes(id);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .contentType(MediaType.parseMediaType(mockMediaStorageService.contentType(id)))
                .contentLength(bytes.length)
                .body(resource);
    }
}
