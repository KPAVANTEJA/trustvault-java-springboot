package com.trustvault.document;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores private document bytes either on local disk (local Oracle development)
 * or in a private Supabase Storage bucket (the hosted production profile).
 *
 * All access-control decisions remain in DocumentController. The Supabase key
 * used here is server-side only and must never be sent to browser code.
 */
@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

    private final Path storageDirectory;
    private final boolean supabaseStorage;
    private final String supabaseUrl;
    private final String supabaseKey;
    private final String bucket;
    private final HttpClient httpClient;

    public DocumentStorageService(
            @Value("${trustvault.storage.directory:./trustvault-private-files}") String directory,
            @Value("${trustvault.storage.provider:local}") String provider,
            @Value("${trustvault.storage.supabase.url:}") String configuredSupabaseUrl,
            @Value("${trustvault.storage.supabase.key:}") String configuredSupabaseKey,
            @Value("${trustvault.storage.supabase.bucket:docurithm-private}") String configuredBucket)
            throws IOException {

        this.supabaseStorage = "supabase".equalsIgnoreCase(provider.trim());
        this.supabaseUrl = trimTrailingSlash(configuredSupabaseUrl == null ? "" : configuredSupabaseUrl.trim());
        this.supabaseKey = configuredSupabaseKey == null ? "" : configuredSupabaseKey.trim();
        this.bucket = configuredBucket == null ? "" : configuredBucket.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (supabaseStorage) {
            if (this.supabaseUrl.isBlank() || !this.supabaseUrl.startsWith("https://")) {
                throw new IllegalStateException("SUPABASE_URL must be an HTTPS project URL when Supabase Storage is enabled.");
            }
            if (this.supabaseKey.isBlank()) {
                throw new IllegalStateException("SUPABASE_SECRET_KEY is required when Supabase Storage is enabled.");
            }
            if (!this.supabaseKey.startsWith("sb_secret_") && !isLegacyServiceRoleKey(this.supabaseKey)) {
                throw new IllegalStateException("SUPABASE_SECRET_KEY must be a server-side Supabase secret key or a legacy service_role JWT.");
            }
            if (!this.bucket.matches("[A-Za-z0-9_-]{1,100}")) {
                throw new IllegalStateException("SUPABASE_STORAGE_BUCKET has an invalid name.");
            }
            this.storageDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "docurithm-private-files")
                    .toAbsolutePath().normalize();
        } else {
            this.storageDirectory = Paths.get(directory)
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(storageDirectory);
        }
    }

    public boolean isSupabaseStorage() {
        return supabaseStorage;
    }

    public String store(MultipartFile file) throws IOException {
        String storageKey = UUID.randomUUID().toString();

        if (supabaseStorage) {
            byte[] contents = file.getBytes();
            HttpRequest request = storageRequest(storageKey)
                    .header("Content-Type", safeContentType(file.getContentType()))
                    .header("x-upsert", "false")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(contents))
                    .build();
            HttpResponse<String> response = sendForText(request);
            requireSuccess(response.statusCode(), "upload");
            return storageKey;
        }

        Path destination = resolve(storageKey);
        try (var input = file.getInputStream()) {
            Files.copy(input, destination);
        }
        return storageKey;
    }

    /** Read bytes only after the controller has checked the user's permissions. */
    public byte[] read(String storageKey) throws IOException {
        validateStorageKey(storageKey);

        if (supabaseStorage) {
            HttpRequest request = storageRequest(storageKey)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = sendForBytes(request);
            if (response.statusCode() == 404) {
                throw new FileNotFoundException("Stored document was not found.");
            }
            requireSuccess(response.statusCode(), "download");
            return response.body();
        }

        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new FileNotFoundException("Stored document was not found.");
        }
        return Files.readAllBytes(path);
    }

    /** Local path helper retained for the local-storage path and existing tests. */
    public Path resolve(String storageKey) {
        validateStorageKey(storageKey);
        if (supabaseStorage) {
            throw new IllegalStateException("A cloud-stored document does not have a local filesystem path.");
        }

        Path resolved = storageDirectory.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Invalid storage key.");
        }
        return resolved;
    }

    public void deleteQuietly(String storageKey) {
        try {
            validateStorageKey(storageKey);
            if (supabaseStorage) {
                HttpRequest request = storageRequest(storageKey)
                        .DELETE()
                        .build();
                HttpResponse<String> response = sendForText(request);
                if (response.statusCode() != 404 && !isSuccess(response.statusCode())) {
                    log.warn("Supabase Storage delete failed with HTTP status {}.", response.statusCode());
                }
            } else {
                Files.deleteIfExists(resolve(storageKey));
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            // Deletion is best-effort during rollback/cleanup; never log credentials or URLs.
            log.warn("Could not remove stored document object.");
        }
    }

    private HttpRequest.Builder storageRequest(String storageKey) {
        validateStorageKey(storageKey);
        URI uri = URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + storageKey);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(40))
                .header("apikey", supabaseKey);

        // Current Supabase secret keys (sb_secret_...) are API keys, not JWTs,
        // and must not be sent as Bearer tokens. Legacy service_role keys are
        // JWTs and are supported through Authorization for backwards compatibility.
        if (isLegacyServiceRoleKey(supabaseKey)) {
            builder.header("Authorization", "Bearer " + supabaseKey);
        }
        return builder;
    }

    private static boolean isLegacyServiceRoleKey(String key) {
        if (key == null || !key.startsWith("eyJ")) {
            return false;
        }
        String[] parts = key.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return Pattern.compile("\"role\"\\s*:\\s*\"service_role\"").matcher(payload).find();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private HttpResponse<String> sendForText(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Supabase Storage request was interrupted.", ex);
        }
    }

    private HttpResponse<byte[]> sendForBytes(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Supabase Storage request was interrupted.", ex);
        }
    }

    private static void requireSuccess(int statusCode, String operation) throws IOException {
        if (!isSuccess(statusCode)) {
            // Do not include provider response bodies, which may contain internal details.
            throw new IOException("Supabase Storage " + operation + " failed with HTTP status " + statusCode + ".");
        }
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static String safeContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static void validateStorageKey(String storageKey) {
        UUID.fromString(storageKey);
    }
}
