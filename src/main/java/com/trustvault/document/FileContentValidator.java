package com.trustvault.document;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FileContentValidator {

    private static final byte[] PDF_SIGNATURE =
            "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47,
        0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final byte[] JPEG_SIGNATURE = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };

    public void validate(
            MultipartFile file,
            String expectedType) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a non-empty file.");
        }

        final byte[] content;

        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to read uploaded file contents.");
        }

        boolean valid;

        switch (expectedType) {
            case "application/pdf" ->
                    valid = startsWith(content, PDF_SIGNATURE);

            case "image/png" ->
                    valid = startsWith(content, PNG_SIGNATURE);

            case "image/jpeg" ->
                    valid = startsWith(content, JPEG_SIGNATURE);

            case "text/plain" ->
                    valid = isValidText(content);

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported content type.");
        }

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File contents do not match the expected file type.");
        }
    }

    private boolean startsWith(
            byte[] content,
            byte[] signature) {

        if (content.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidText(byte[] content) {
        final String text;

        try {
            text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException ex) {
            return false;
        }

        // Reject binary control characters, while allowing ordinary
        // text formatting such as newlines, tabs and carriage returns.
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);

            if (Character.isISOControl(codePoint)
                    && codePoint != '\n'
                    && codePoint != '\r'
                    && codePoint != '\t'
                    && codePoint != '\f') {
                return false;
            }

            offset += Character.charCount(codePoint);
        }

        return true;
    }
}