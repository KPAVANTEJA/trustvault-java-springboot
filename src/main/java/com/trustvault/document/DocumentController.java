package com.trustvault.document;

import java.io.IOException;

import java.util.List;
import java.util.Map;

import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.trustvault.audit.AuditLogService;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final AppUserRepository userRepository;
    private final DocumentStorageService storageService;
    
    private final DocumentPermissionRepository permissionRepository;
    private final AuditLogService auditLogService;
    
    

    public DocumentController(
            DocumentRepository documentRepository,
            AppUserRepository userRepository,
            DocumentStorageService storageService,
            DocumentPermissionRepository permissionRepository,
            AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.permissionRepository = permissionRepository;
        this.auditLogService = auditLogService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Transactional
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            Authentication authentication) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Choose a non-empty file.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE, "Maximum file size is 10 MB.");
        }

        String cleanTitle = title == null ? "" : title.trim();

        if (cleanTitle.isBlank() || cleanTitle.length() > 200) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Title must be 1–200 characters.");
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Filename is required.");
        }

        originalFilename = originalFilename
            .replace('\\', '/');

        originalFilename = originalFilename.substring(
            originalFilename.lastIndexOf('/') + 1);

        if (originalFilename.isBlank() || originalFilename.length() > 255) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid filename.");
        }

        String extension = originalFilename.contains(".")
            ? originalFilename.substring(
                originalFilename.lastIndexOf('.') + 1).toLowerCase()
            : "";

        Map<String, String> allowedTypes = Map.of(
            "pdf", "application/pdf",
            "txt", "text/plain",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg"
        );

        String expectedType = allowedTypes.get(extension);

        if (expectedType == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Supported formats: PDF, TXT, PNG, JPG, JPEG.");
        }

        String suppliedType = file.getContentType();

        if (suppliedType != null
                && !suppliedType.equalsIgnoreCase(expectedType)
                && !suppliedType.equalsIgnoreCase("application/octet-stream")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File extension and content type do not match.");
        }

        AppUser owner = userRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Authenticated user not found."));

        String storageKey = null;

        try {
            storageKey = storageService.store(file);

            Document document = new Document(
                cleanTitle,
                originalFilename,
                storageKey,
                expectedType,
                file.getSize(),
                owner
            );

            document = documentRepository.saveAndFlush(document);

            return Map.of(
                "id", document.getId(),
                "title", document.getTitle(),
                "filename", document.getOriginalFilename(),
                "sizeBytes", document.getSizeBytes(),
                "ownerEmail", owner.getEmail(),
                "createdAt", document.getCreatedAt().toString()
            );

        } catch (IOException ex) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to store document.");
        } catch (RuntimeException ex) {
            if (storageKey != null) {
                storageService.deleteQuietly(storageKey);
            }
            throw ex;
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOwnDocuments(
            Authentication authentication) {

        AppUser user = userRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Authenticated user not found."));

        return documentRepository
            .findByOwner_IdOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(document -> Map.<String, Object>of(
                "id", document.getId(),
                "title", document.getTitle(),
                "filename", document.getOriginalFilename(),
                "sizeBytes", document.getSizeBytes(),
                "createdAt", document.getCreatedAt().toString()
            ))
            .toList();
    }
    

@GetMapping("/{id}/download")
@Transactional
public ResponseEntity<Resource> downloadDocument(
        @PathVariable Long id,
        Authentication authentication) {

    AppUser user = userRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user not found."));

    Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found."));

    boolean isOwner = document.getOwner().getId().equals(user.getId());

    if (!isOwner) {
        DocumentPermission permission = permissionRepository
                .findByDocument_IdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Document not found."));

        if (permission.getPermissionType() != PermissionType.VIEW
                && permission.getPermissionType() != PermissionType.EDIT) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found.");
        }
    }

    Path filePath = storageService.resolve(document.getStorageKey());

    if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Document file not found.");
    }

    Resource resource = new FileSystemResource(filePath);

    String contentType = document.getContentType() == null
            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
            : document.getContentType();

    auditLogService.recordDownload(document, user);
    
    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"document-" + document.getId() + "\"")
            .body(resource);
}

}