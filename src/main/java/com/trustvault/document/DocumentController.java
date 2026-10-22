package com.trustvault.document;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
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
            @RequestParam(
            	    name = "category",
            	    defaultValue = "Uncategorized"
            	) String category,
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
        
        //Validate Category
        if (category != null && category.trim().length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Category must not exceed 100 characters."
            );
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
                owner,
                category
            );

            document = documentRepository.saveAndFlush(document);
            
            auditLogService.recordUpload(document, owner);

            return Map.of(
                "id", document.getId(),
                "title", document.getTitle(),
                "filename", document.getOriginalFilename(),
                "category", document.getCategory(),
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
    		        HttpStatus.NOT_FOUND, "User not found."));

    		List<Document> documents;

    		if (user.getRole() == UserRole.ADMIN) {
    		    documents = documentRepository
    		        .findByOwner_RoleAndDeletedFalseOrderByCreatedAtDesc(UserRole.ADMIN);
    		} else if (user.getRole() == UserRole.VIEWER) {
    		    documents = documentRepository.findSharedDocuments(user.getId());
    		} else {
    		    documents = documentRepository.findOwnedOrSharedDocuments(user.getId());
    		}

        return documents
            .stream()
            .map(document -> Map.<String, Object>of(
                "id", document.getId(),
                "title", document.getTitle(),
                "filename", document.getOriginalFilename(),
                "category", document.getCategory(),
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
    
    if (document.isDeleted()) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Document not found.");
    }

    boolean isOwner = document.getOwner().getId().equals(user.getId());

    if (!isOwner) {
    	DocumentPermission permission = permissionRepository
    	        .findByDocument_IdAndUser_Id(id, user.getId())
    	        .orElse(null);

    	if (permission == null) {
    	    auditLogService.recordDownloadFailure(document, user, "DENIED");
    	    throw new ResponseStatusException(
    	            HttpStatus.NOT_FOUND, "Document not found.");
    	}

    	if (permission.getPermissionType() != PermissionType.VIEW
    	        && permission.getPermissionType() != PermissionType.EDIT) {
    	    auditLogService.recordDownloadFailure(document, user, "DENIED");
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


@DeleteMapping("/{id}")
@Transactional
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public ResponseEntity<Void> deleteDocument(
        @PathVariable Long id,
        Authentication authentication) {

    AppUser user = userRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user not found."));

    Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found."));

    if (document.isDeleted()) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Document not found.");
    }

    boolean isOwner = document.getOwner().getId()
            .equals(user.getId());

    if (!isOwner) {
        auditLogService.recordDeletionFailure(
                document, user, "DENIED");

        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Document not found.");
    }

    document.setDeleted(true);
    documentRepository.save(document);

    storageService.deleteQuietly(document.getStorageKey());

    auditLogService.recordDeletion(document, user);

    return ResponseEntity.noContent().build();
}


@PutMapping("/{id}")
@Transactional
public Map<String, Object> updateDocument(
        @PathVariable Long id,
        @RequestBody Map<String, String> changes,
        Authentication authentication) {

    AppUser user = userRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "User not found."));

    Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found."));

    if (document.isDeleted()) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Document not found.");
    }

    // The owner may edit. Other users require explicit EDIT permission.
    boolean isOwner = document.getOwner().getId().equals(user.getId());

    if (!isOwner) {
        DocumentPermission permission = permissionRepository
                .findByDocument_IdAndUser_Id(id, user.getId())
                .orElse(null);

        if (permission == null
                || permission.getPermissionType() != PermissionType.EDIT) {

            auditLogService.recordDocumentUpdateFailure(
                    document, user, "DENIED");

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found.");
        }
    }

    if (!changes.containsKey("title")
            && !changes.containsKey("category")) {
    	
    	auditLogService.recordDocumentUpdateFailure(
    	        document, user, "INVALID_INPUT");
    	
    	
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provide a title or category to update.");
    }

    String updatedTitle = changes.get("title");

    if (updatedTitle == null) {
        updatedTitle = document.getTitle();
    } else {
        updatedTitle = updatedTitle.trim();

        if (updatedTitle.isBlank() || updatedTitle.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Title must be 1–200 characters.");
        }
    }

    String updatedCategory = changes.get("category");

    if (updatedCategory == null) {
        updatedCategory = document.getCategory();
    } else {
        updatedCategory = updatedCategory.trim();

        if (updatedCategory.isBlank()) {
            updatedCategory = "Uncategorized";
        }

        if (updatedCategory.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Category must not exceed 100 characters.");
        }
    }

    document.updateMetadata(updatedTitle, updatedCategory);
    documentRepository.saveAndFlush(document);

    // Same transaction as the metadata update.
    auditLogService.recordDocumentUpdate(document, user);

    return Map.of(
            "id", document.getId(),
            "title", document.getTitle(),
            "category", document.getCategory(),
            "filename", document.getOriginalFilename(),
            "ownerEmail", document.getOwner().getEmail(),
            "message", "Document metadata updated successfully."
    );
}


@PostMapping("/{id}/content")
@Transactional
public Map<String, Object> replaceDocumentContent(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file,
        Authentication authentication) {

    AppUser user = userRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "User not found."));

    Document document = documentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found."));

    if (document.isDeleted()) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Document not found.");
    }

    boolean isOwner = document.getOwner().getId().equals(user.getId());

    if (!isOwner) {
        DocumentPermission permission = permissionRepository
                .findByDocument_IdAndUser_Id(id, user.getId())
                .orElse(null);

        if (permission == null
                || permission.getPermissionType() != PermissionType.EDIT) {

            auditLogService.recordDocumentUpdateFailure(
                    document, user, "DENIED");

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found.");
        }
    }

    // Apply the same upload validation rules.
    if (file == null || file.isEmpty()) {
    	
    	auditLogService.recordDocumentUpdateFailure(
    	        document, user, "INVALID_INPUT");
    	
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Choose a non-empty file.");
    }

    if (file.getSize() > MAX_FILE_SIZE) {
    	
    	auditLogService.recordDocumentUpdateFailure(
    	        document, user, "INVALID_INPUT");
    	
        throw new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Maximum file size is 10 MB.");
    }

    String originalFilename = file.getOriginalFilename();

    if (originalFilename == null || originalFilename.isBlank()) {
    	
    	auditLogService.recordDocumentUpdateFailure(
    	        document, user, "INVALID_INPUT");
    	
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Filename is required.");
    }

    originalFilename = originalFilename.replace('\\', '/');
    originalFilename = originalFilename.substring(
            originalFilename.lastIndexOf('/') + 1);

    if (originalFilename.isBlank() || originalFilename.length() > 255) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid filename.");
    }

    String extension = originalFilename.contains(".")
            ? originalFilename.substring(
                    originalFilename.lastIndexOf('.') + 1)
                    .toLowerCase(java.util.Locale.ROOT)
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
    	
    	auditLogService.recordDocumentUpdateFailure(
    	        document, user, "INVALID_INPUT");
    	
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

    String oldStorageKey = document.getStorageKey();
    String newStorageKey = null;

    try {
        // Store the new file without deleting the old one yet.
        newStorageKey = storageService.store(file);

        document.replaceStoredFile(
                originalFilename,
                newStorageKey,
                expectedType,
                file.getSize());

        documentRepository.saveAndFlush(document);

        // Uses the existing audited metadata-update service method.
        auditLogService.recordDocumentUpdate(document, user);

        String replacementKey = newStorageKey;

        // Delete the old file only after the transaction commits.
        // If the database transaction rolls back, delete the new file.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_COMMITTED) {
                            storageService.deleteQuietly(oldStorageKey);
                        } else {
                            storageService.deleteQuietly(replacementKey);
                        }
                    }
                });

        return Map.of(
                "id", document.getId(),
                "title", document.getTitle(),
                "filename", document.getOriginalFilename(),
                "category", document.getCategory(),
                "sizeBytes", document.getSizeBytes(),
                "message", "Document content replaced successfully."
        );

    } catch (IOException ex) {
        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to store replacement document.");

    } catch (RuntimeException ex) {
        if (newStorageKey != null) {
            storageService.deleteQuietly(newStorageKey);
        }

        throw ex;
    }
}


@GetMapping("/search")
@Transactional(readOnly = true)
public List<Map<String, Object>> searchDocuments(
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "") String owner,
        @RequestParam(defaultValue = "") String category,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate,
        Authentication authentication) {

    AppUser user = userRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "User not found"));

    if (fromDate != null && toDate != null
            && fromDate.isAfter(toDate)) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Start date must not be after end date.");
    }

    // First restrict results to documents this user may access.
    List<Document> documents;

    if (user.getRole() == UserRole.ADMIN) {
        documents = documentRepository
                .findByOwner_RoleAndDeletedFalseOrderByCreatedAtDesc(
                        UserRole.ADMIN);
    } else if (user.getRole() == UserRole.VIEWER) {
        documents = documentRepository
                .findSharedDocuments(user.getId());
    } else {
        documents = documentRepository
                .findOwnedOrSharedDocuments(user.getId());
    }

    String nameFilter = keyword.trim();
    String ownerFilter = owner.trim();
    String categoryFilter = category.trim();

    return documents.stream()
            .filter(d ->
                    matchesFilter(d.getTitle(), nameFilter)
                    || matchesFilter(
                            d.getOriginalFilename(), nameFilter))
            .filter(d ->
                    matchesFilter(d.getOwner().getFullName(), ownerFilter)
                    || matchesFilter(d.getOwner().getEmail(), ownerFilter))
            .filter(d ->
                    matchesFilter(d.getCategory(), categoryFilter))
            .filter(d -> fromDate == null
                    || !d.getCreatedAt().toLocalDate()
                            .isBefore(fromDate))
            .filter(d -> toDate == null
                    || !d.getCreatedAt().toLocalDate()
                            .isAfter(toDate))
            .map(d -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", d.getId());
                result.put("title", d.getTitle());
                result.put("filename", d.getOriginalFilename());
                result.put("category", d.getCategory());
                result.put("ownerName", d.getOwner().getFullName());
                result.put("ownerEmail", d.getOwner().getEmail());
                result.put("sizeBytes", d.getSizeBytes());
                result.put("createdAt", d.getCreatedAt());
                return result;
            })
            .toList();
}

private boolean matchesFilter(String value, String filter) {
    return filter == null
            || filter.isBlank()
            || (value != null
                && value.toLowerCase(Locale.ROOT)
                        .contains(filter.trim().toLowerCase(Locale.ROOT)));
}



}