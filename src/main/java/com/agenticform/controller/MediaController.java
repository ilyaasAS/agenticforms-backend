package com.agenticform.controller;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.agenticform.dto.MediaUploadResponse;
import com.agenticform.dto.ProxiedImage;
import com.agenticform.dto.ResolveImageResponse;
import com.agenticform.service.ImageResolveService;
import com.agenticform.service.MediaStorageService;
import com.agenticform.service.MediaStorageService.StoredMedia;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final ImageResolveService imageResolveService;
    private final MediaStorageService mediaStorageService;

    public MediaController(
            ImageResolveService imageResolveService,
            MediaStorageService mediaStorageService
    ) {
        this.imageResolveService = imageResolveService;
        this.mediaStorageService = mediaStorageService;
    }

    @GetMapping("/resolve-image")
    public ResponseEntity<ResolveImageResponse> resolveImage(
            @RequestParam("url") String url
    ) {
        ResolveImageResponse response = imageResolveService.resolve(url);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String url) {
        Optional<ProxiedImage> proxied = imageResolveService.proxyImage(url);
        if (proxied.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        ProxiedImage image = proxied.get();
        HttpHeaders headers = new HttpHeaders();
        String contentType = image.contentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        if (contentType.toLowerCase().contains("svg")) {
            contentType = "image/svg+xml";
        } else if ("application/octet-stream".equals(contentType)) {
            contentType = "image/jpeg";
        }
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setCacheControl("public, max-age=3600");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
        return new ResponseEntity<>(image.bytes(), headers, HttpStatus.OK);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaStorageService.store(file));
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<byte[]> serveFile(@PathVariable("filename") String filename)
            throws IOException {
        Optional<StoredMedia> stored = mediaStorageService.load(filename);
        if (stored.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StoredMedia media = stored.get();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(media.contentType()));
        headers.setCacheControl("public, max-age=86400");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
        return new ResponseEntity<>(media.bytes(), headers, HttpStatus.OK);
    }
}
