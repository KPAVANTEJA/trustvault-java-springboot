package com.trustvault.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentStorageService {

    private final Path storageDirectory;

    public DocumentStorageService(
            @Value("${trustvault.storage.directory:./trustvault-private-files}")
            String directory) throws IOException {

        this.storageDirectory = Paths.get(directory)
            .toAbsolutePath()
            .normalize();

        Files.createDirectories(storageDirectory);
    }

    public String store(MultipartFile file) throws IOException {
        String storageKey = UUID.randomUUID().toString();
        Path destination = resolve(storageKey);

        try (var input = file.getInputStream()) {
            Files.copy(input, destination);
        }

        return storageKey;
    }

    public Path resolve(String storageKey) {
        UUID.fromString(storageKey); // Reject invalid storage identifiers.

        Path resolved = storageDirectory.resolve(storageKey).normalize();

        if (!resolved.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Invalid storage key.");
        }

        return resolved;
    }

    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException | IllegalArgumentException ignored) {
            // Cleanup failure must not expose filesystem details.
        }
    }
}