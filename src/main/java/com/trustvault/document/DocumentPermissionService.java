
package com.trustvault.document;

import com.trustvault.user.AppUser;
import com.trustvault.audit.AuditLogService;
import com.trustvault.user.AppUserRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentPermissionService {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final AppUserRepository userRepository;
    
    private final AuditLogService auditLogService;

    public DocumentPermissionService(
            DocumentRepository documentRepository,
            DocumentPermissionRepository permissionRepository,
            AppUserRepository userRepository,
            AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public DocumentPermission grantPermission(
            Long documentId,
            String targetEmail,
            PermissionType permissionType,
            String ownerEmail) {

        if (targetEmail == null || targetEmail.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Recipient email is required.");
        }

        if (permissionType == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Permission type is required.");
        }

        AppUser owner = userRepository
            .findByEmailIgnoreCase(ownerEmail)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found."));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Document not found."));

        if (document.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found.");
        }

        AppUser documentOwner = document.getOwner();

        if (documentOwner == null
                || documentOwner.getId() == null
                || !documentOwner.getId().equals(owner.getId())) {
        	auditLogService.recordPermissionChangeFailure(
        	        document,
        	        owner,
        	        null,
        	        targetEmail.trim(),
        	        null,
        	        permissionType.name(),
        	        "PERMISSION_GRANT",
        	        "DENIED"
        	);
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found.");
        }

        AppUser recipient = userRepository
            .findByEmailIgnoreCase(targetEmail.trim())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Recipient user not found."));

        if (recipient.getId().equals(owner.getId())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "You already own this document.");
        }

        DocumentPermission permission = permissionRepository
            .findByDocument_IdAndUser_Id(
                documentId, recipient.getId())
            .orElse(null);

        boolean isNewGrant = permission == null;

        String previousPermissionType = isNewGrant
                ? null
                : permission.getPermissionType().name();

        if (isNewGrant) {
            permission = new DocumentPermission(
                document, recipient, permissionType, owner);
        } else {
            permission.setPermissionType(permissionType);
        }

        DocumentPermission saved =
                permissionRepository.saveAndFlush(permission);

        auditLogService.recordPermissionChange(
        	    document,
        	    owner,
        	    recipient.getId(),
        	    recipient.getEmail(),
        	    previousPermissionType,
        	    permissionType.name(),
        	    isNewGrant ? "PERMISSION_GRANT" : "PERMISSION_UPDATE",
        	    "SUCCESS"
        	);

        return saved;
    }

    @Transactional
    public void revokePermission(
            Long documentId,
            Long recipientId,
            String ownerEmail) {

        AppUser owner = userRepository
            .findByEmailIgnoreCase(ownerEmail)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found."));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Document not found."));

        if (document.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found.");
        }

        AppUser documentOwner = document.getOwner();

        if (documentOwner == null
                || documentOwner.getId() == null
                || !documentOwner.getId().equals(owner.getId())) {
        	auditLogService.recordPermissionChangeFailure(
        	        document,
        	        owner,
        	        recipientId,
        	        null,
        	        null,
        	        null,
        	        "PERMISSION_REVOKE",
        	        "DENIED"
        	);

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found.");
        }

        DocumentPermission permission = permissionRepository
            .findByDocument_IdAndUser_Id(documentId, recipientId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Permission not found."));
        
        AppUser recipient = permission.getUser();

        String revokedPermissionType = permission.getPermissionType().name();

        permissionRepository.delete(permission);
        permissionRepository.flush();

        auditLogService.recordPermissionChange(
        	    document,
        	    owner,
        	    recipient.getId(),
        	    recipient.getEmail(),
        	    revokedPermissionType,
        	    null,
        	    "PERMISSION_REVOKE",
        	    "SUCCESS"
        	);
    }
    
    
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDocumentPermissions(
            Long documentId, String ownerEmail) {

        AppUser requester = userRepository.findByEmailIgnoreCase(ownerEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        Document document;

        // ADMIN may view permissions on any document.
        if (requester.getRole() == com.trustvault.user.UserRole.ADMIN) {
            document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Document not found"));
        } else {
            document = documentRepository
                    .findByIdAndOwner_Id(documentId, requester.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Document not found"));
        }

        if (document.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Document not found");
        }

        return permissionRepository.findByDocument_Id(documentId)
                .stream()
                .map(permission -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("recipientId", permission.getUser().getId());
                    result.put("email", permission.getUser().getEmail());
                    result.put("fullName", permission.getUser().getFullName());
                    result.put("permissionType",
                            permission.getPermissionType().name());
                    result.put("grantedAt", permission.getGrantedAt().toString());
                    return result;
                })
                .toList();
    }
}
