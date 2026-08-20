package com.agenticform.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.agenticform.dto.MediaUploadResponse;
import com.agenticform.exception.InvalidFormFieldException;

import jakarta.annotation.PostConstruct;

@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);
    private static final long MAX_BYTES = 20_000_000L;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml",
            "image/avif",
            "image/bmp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final Path storageRoot;

    public MediaStorageService(
            @Value("${app.media.storage-dir:./data/media}") String storageDir
    ) {
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureRoot() throws IOException {
        Files.createDirectories(storageRoot);
        log.info("Media storage root: {}", storageRoot);
    }

    public MediaUploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFormFieldException("Fichier manquant.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidFormFieldException(
                    "Fichier trop volumineux (max 20 Mo). Compressez-le ou choisissez un autre fichier."
            );
        }

        String contentType = Optional.ofNullable(file.getContentType())
                .orElse("")
                .split(";")[0]
                .trim()
                .toLowerCase(Locale.ROOT);
        // Navigateur parfois sans Content-Type : déduire de l’extension.
        if (contentType.isBlank() || "application/octet-stream".equals(contentType)) {
            contentType = contentTypeFromFilename(file.getOriginalFilename());
        }
        if (!ALLOWED_TYPES.contains(contentType) && !"image/jpg".equals(contentType)) {
            throw new InvalidFormFieldException(
                    "Type de fichier non supporté. Utilisez JPEG, PNG, GIF, WebP, AVIF, SVG, BMP, PDF ou Word."
            );
        }

        String ext = extensionFor(contentType, file.getOriginalFilename());
        String id = UUID.randomUUID().toString().replace("-", "");
        String filename = id + ext;
        Path target = storageRoot.resolve(filename).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new InvalidFormFieldException("Chemin de stockage invalide.");
        }

        try {
            Files.createDirectories(storageRoot);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.error("Échec écriture média dans {}: {}", storageRoot, ex.toString());
            throw new InvalidFormFieldException(
                    "Impossible d’enregistrer le fichier sur le serveur. Réessayez ou contactez l’administrateur."
            );
        }

        String url = "/v1/media/files/" + filename;
        return new MediaUploadResponse(url, contentType, file.getSize());
    }

    public Optional<StoredMedia> load(String filename) throws IOException {
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/")
                || filename.contains("\\")) {
            return Optional.empty();
        }
        Path path = storageRoot.resolve(filename).normalize();
        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        String contentType = probeContentType(filename);
        byte[] bytes = Files.readAllBytes(path);
        return Optional.of(new StoredMedia(bytes, contentType));
    }

    private static String contentTypeFromFilename(String originalName) {
        if (originalName == null) return "";
        String lower = originalName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".avif")) return "image/avif";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "";
    }

    private static String extensionFor(String contentType, String originalName) {
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            case "image/avif" -> ".avif";
            case "image/bmp" -> ".bmp";
            case "application/pdf" -> ".pdf";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            default -> {
                if (originalName != null) {
                    int dot = originalName.lastIndexOf('.');
                    if (dot >= 0 && dot < originalName.length() - 1) {
                        yield originalName.substring(dot).toLowerCase(Locale.ROOT);
                    }
                }
                yield ".bin";
            }
        };
    }

    private static String probeContentType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".avif")) return "image/avif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF_VALUE;
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    public record StoredMedia(byte[] bytes, String contentType) {
    }
}
