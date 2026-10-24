package com.trustvault.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentContentHashService {

    public String sha256(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return calculateHash(input);
        }
    }

    public String sha256(Path filePath) throws IOException {
        try (InputStream input = Files.newInputStream(filePath)) {
            return calculateHash(input);
        }
    }

    public String sha256(byte[] content) throws IOException {
        try (InputStream input = new java.io.ByteArrayInputStream(content)) {
            return calculateHash(input);
        }
    }

    private String calculateHash(InputStream input) throws IOException {
        final MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable.", exception);
        }

        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }

        return HexFormat.of().formatHex(digest.digest());
    }
}