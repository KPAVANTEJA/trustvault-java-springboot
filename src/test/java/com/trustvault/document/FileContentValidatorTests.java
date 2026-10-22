package com.trustvault.document;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class FileContentValidatorTests {

    private final FileContentValidator validator =
            new FileContentValidator();

    @Test
    void acceptsPdfSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "%PDF-1.7\nTest".getBytes(StandardCharsets.UTF_8));

        assertDoesNotThrow(
                () -> validator.validate(file, "application/pdf"));
    }

    @Test
    void rejectsTextDisguisedAsPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "This is not a PDF".getBytes(StandardCharsets.UTF_8));

        assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(file, "application/pdf"));
    }

    @Test
    void acceptsPngSignature() {
        byte[] pngHeader = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
        };

        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", pngHeader);

        assertDoesNotThrow(
                () -> validator.validate(file, "image/png"));
    }

    @Test
    void acceptsJpegSignature() {
        byte[] jpegHeader = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
        };

        MockMultipartFile file = new MockMultipartFile(
                "file", "image.jpg", "image/jpeg", jpegHeader);

        assertDoesNotThrow(
                () -> validator.validate(file, "image/jpeg"));
    }

    @Test
    void acceptsUtf8Text() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "Hello TrustVault\nSecond line\tTab"
                        .getBytes(StandardCharsets.UTF_8));

        assertDoesNotThrow(
                () -> validator.validate(file, "text/plain"));
    }

    @Test
    void rejectsBinaryContentAsText() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                new byte[] { 0x41, 0x00, 0x42 });

        assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(file, "text/plain"));
    }

    @Test
    void rejectsUnsupportedExpectedType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.exe",
                "application/octet-stream",
                new byte[] { 0x01, 0x02 });

        assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(file, "application/octet-stream"));
    }
}