
package com.trustvault.document;

import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentPermissionService {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final AppUserRepository userRepository;

    public DocumentPermissionService(
            DocumentRepository documentRepository,
            DocumentPermissionRepository permissionRepository,
            AppUserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
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

        Document document = documentRepository
            .findByIdAndOwner_Id(documentId, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Document not found."));

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

        if (permission == null) {
            permission = new DocumentPermission(
                document, recipient, permissionType, owner);
        } else {
            permission.setPermissionType(permissionType);
        }

        return permissionRepository.save(permission);
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

        documentRepository
            .findByIdAndOwner_Id(documentId, owner.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Document not found."));

        DocumentPermission permission = permissionRepository
            .findByDocument_IdAndUser_Id(documentId, recipientId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Permission not found."));

        permissionRepository.delete(permission);
    }
}
