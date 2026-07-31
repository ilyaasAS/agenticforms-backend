package com.agenticform.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.agenticform.dto.ProxiedImage;
import com.agenticform.dto.ResolveImageResponse;

@Service
public class ImageResolveService {

    private static final Logger log = LoggerFactory.getLogger(ImageResolveService.class);
    private static final int CONNECT_TIMEOUT_MS = 4_000;
    private static final int READ_TIMEOUT_MS = 6_000;
    private static final int MAX_BYTES = 1_000_000;
    private static final int MAX_IMAGE_BYTES = 5_000_000;
    private static final int MAX_REDIRECTS = 4;

    private static final Pattern DIRECT_EXT = Pattern.compile(
            "\\.(avif|bmp|gif|jpe?g|png|svg|webp)(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern OG_IMAGE = Pattern.compile(
            "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']"
                    + "|<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TWITTER_IMAGE = Pattern.compile(
            "<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']"
                    + "|<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']twitter:image[\"']",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LINK_IMAGE_SRC = Pattern.compile(
            "<link[^>]+rel=[\"']image_src[\"'][^>]+href=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMG_SRC = Pattern.compile(
            "<img[^>]+src=[\"'](https?://[^\"']+\\.(?:png|jpe?g|webp|gif|svg)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);

    public ResolveImageResponse resolve(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return ResolveImageResponse.failure("URL manquante.");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception ex) {
            return ResolveImageResponse.failure("URL invalide.");
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            return ResolveImageResponse.failure("Seuls les liens http(s) sont acceptés.");
        }

        if (isBlockedHost(uri.getHost())) {
            return ResolveImageResponse.failure("Hôte non autorisé.");
        }

        String path = Optional.ofNullable(uri.getPath()).orElse("");
        if (DIRECT_EXT.matcher(path).find()) {
            return ResolveImageResponse.success(uri.toString(), "direct");
        }

        try {
            String html = fetchHtml(uri.toURL());
            if (html == null || html.isBlank()) {
                return ResolveImageResponse.failure("Impossible de récupérer la page.");
            }

            String og = firstGroup(OG_IMAGE.matcher(html));
            if (og == null) {
                og = firstGroup(TWITTER_IMAGE.matcher(html));
            }
            if (og == null) {
                og = firstGroup(LINK_IMAGE_SRC.matcher(html));
            }
            if (og == null) {
                og = firstGroup(IMG_SRC.matcher(html));
            }

            if (og == null || og.isBlank()) {
                return ResolveImageResponse.failure("Aucune image trouvée dans la page.");
            }

            String absolute = toAbsoluteUrl(uri, og.trim());
            if (absolute == null) {
                return ResolveImageResponse.failure("URL d’image extraite invalide.");
            }
            return ResolveImageResponse.success(absolute, "og");
        } catch (Exception ex) {
            log.debug("resolve image failed for {}: {}", rawUrl, ex.toString());
            return ResolveImageResponse.failure("Échec de l’extraction d’image.");
        }
    }

    /**
     * Proxy d’image serveur (contourne hotlink / CORS navigateur).
     */
    public Optional<ProxiedImage> proxyImage(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception ex) {
            return Optional.empty();
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            return Optional.empty();
        }
        if (isBlockedHost(uri.getHost())) {
            return Optional.empty();
        }

        try {
            return fetchImageBytes(uri.toURL());
        } catch (Exception ex) {
            log.debug("proxy image failed for {}: {}", rawUrl, ex.toString());
            return Optional.empty();
        }
    }

    private Optional<ProxiedImage> fetchImageBytes(URL start) throws IOException {
        URL current = start;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "AgenticFormsImageProxy/1.0");
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");

            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isBlank()) {
                    return Optional.empty();
                }
                URI next = URI.create(current.toString()).resolve(location);
                if (isBlockedHost(next.getHost())) {
                    return Optional.empty();
                }
                current = next.toURL();
                continue;
            }

            if (status >= 400) {
                connection.disconnect();
                return Optional.empty();
            }

            String contentType = Optional.ofNullable(connection.getContentType())
                    .orElse("application/octet-stream")
                    .split(";")[0]
                    .trim()
                    .toLowerCase(Locale.ROOT);

            byte[] buffer = connection.getInputStream().readNBytes(MAX_IMAGE_BYTES + 1);
            connection.disconnect();
            if (buffer.length == 0 || buffer.length > MAX_IMAGE_BYTES) {
                return Optional.empty();
            }

            boolean svg = looksLikeSvg(buffer) || contentType.contains("svg");
            boolean raster = looksLikeImageMagic(buffer);
            boolean imageContentType = contentType.startsWith("image/");
            boolean permissiveSvgOriginType = contentType.equals("text/plain")
                    || contentType.equals("text/xml")
                    || contentType.equals("application/xml")
                    || contentType.equals("application/octet-stream");

            // SVG : souvent servi en text/plain / text/xml / application/xml (GitHub Raw, Wikimedia).
            // looksLikeImageMagic ne détecte que PNG/JPEG/GIF/WEBP → SVG était rejeté ici.
            if (!imageContentType && !raster) {
                if (svg && permissiveSvgOriginType) {
                    // OK — SVG détecté malgré un Content-Type non-image
                } else if (pathSuggestsSvg(current) && looksLikeSvg(buffer)) {
                    svg = true;
                } else {
                    return Optional.empty();
                }
            }

            if (svg) {
                contentType = "image/svg+xml";
            } else if (!imageContentType) {
                contentType = guessImageContentType(buffer);
            }
            return Optional.of(new ProxiedImage(buffer, contentType));
        }
        return Optional.empty();
    }

    private static boolean pathSuggestsSvg(URL url) {
        String path = Optional.ofNullable(url.getPath()).orElse("").toLowerCase(Locale.ROOT);
        return path.endsWith(".svg");
    }

    /**
     * Détecte un SVG (texte XML) : commence par {@code <svg} ou {@code <?xml} contenant {@code <svg}.
     */
    private static boolean looksLikeSvg(byte[] buffer) {
        if (buffer == null || buffer.length < 4) {
            return false;
        }
        String head = new String(buffer, 0, Math.min(buffer.length, 512), StandardCharsets.UTF_8)
                .stripLeading()
                .toLowerCase(Locale.ROOT);
        if (head.startsWith("\ufeff")) {
            head = head.substring(1).stripLeading();
        }
        return head.startsWith("<svg")
                || (head.startsWith("<?xml") && head.contains("<svg"))
                || head.contains("<svg");
    }

    private static boolean looksLikeImageMagic(byte[] bytes) {
        if (bytes.length < 4) {
            return false;
        }
        // PNG
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return true;
        }
        // JPEG
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return true;
        }
        // GIF
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return true;
        }
        // WEBP (RIFF....WEBP)
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return true;
        }
        return false;
    }

    private static String guessImageContentType(byte[] bytes) {
        if (looksLikeSvg(bytes)) {
            return "image/svg+xml";
        }
        if (bytes.length >= 4 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) {
            return "image/png";
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (bytes.length >= 3 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length >= 12 && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B') {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private static String firstGroup(Matcher matcher) {
        if (!matcher.find()) {
            return null;
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String toAbsoluteUrl(URI page, String candidate) {
        try {
            URI resolved = page.resolve(candidate);
            if (!"http".equalsIgnoreCase(resolved.getScheme())
                    && !"https".equalsIgnoreCase(resolved.getScheme())) {
                return null;
            }
            if (isBlockedHost(resolved.getHost())) {
                return null;
            }
            return resolved.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String fetchHtml(URL start) throws IOException {
        URL current = start;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "AgenticFormsImageResolver/1.0");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");

            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isBlank()) {
                    return null;
                }
                URI next = URI.create(current.toString()).resolve(location);
                if (isBlockedHost(next.getHost())) {
                    return null;
                }
                current = next.toURL();
                continue;
            }

            if (status >= 400) {
                connection.disconnect();
                return null;
            }

            String contentType = Optional.ofNullable(connection.getContentType()).orElse("").toLowerCase(Locale.ROOT);
            if (contentType.startsWith("image/")) {
                String imageUrl = current.toString();
                connection.disconnect();
                // Caller already had a webpage URL; if server returns image, keep it.
                return "<meta property=\"og:image\" content=\"" + imageUrl + "\" />";
            }

            byte[] buffer = connection.getInputStream().readNBytes(MAX_BYTES);
            connection.disconnect();
            return new String(buffer, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static boolean isBlockedHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost") || normalized.endsWith(".local")) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(normalized);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress();
        } catch (Exception ex) {
            return true;
        }
    }
}
