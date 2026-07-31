package com.agenticform.controller;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.ProxiedImage;
import com.agenticform.dto.ResolveImageResponse;
import com.agenticform.service.ImageResolveService;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final ImageResolveService imageResolveService;

    public MediaController(ImageResolveService imageResolveService) {
        this.imageResolveService = imageResolveService;
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
        // SVG : toujours image/svg+xml (navigateur + nosniff).
        if (contentType.toLowerCase().contains("svg")) {
            contentType = "image/svg+xml";
        } else if ("application/octet-stream".equals(contentType)) {
            contentType = "image/jpeg";
        }
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setCacheControl("private, max-age=3600");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
        return new ResponseEntity<>(image.bytes(), headers, HttpStatus.OK);
    }
}