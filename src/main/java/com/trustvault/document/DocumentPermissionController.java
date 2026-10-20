
package com.trustvault.document;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents/{documentId}/permissions")
public class DocumentPermissionController {

    private final DocumentPermissionService permissionService;
    
    

    public DocumentPermissionController(
            DocumentPermissionService permissionService) {
        this.permissionService = permissionService;
        
    }

    public record GrantPermissionRequest(
            String email,
            PermissionType permissionType) {
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public Map<String, Object> grantPermission(
            @PathVariable Long documentId,
            @RequestBody GrantPermissionRequest request,
            Authentication authentication) {

        if (request == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Request body is required.");
        }

        DocumentPermission permission = permissionService.grantPermission(
            documentId,
            request.email(),
            request.permissionType(),
            authentication.getName()
        );

        return Map.of(
            "documentId", permission.getDocument().getId(),
            "userEmail", permission.getUser().getEmail(),
            "permissionType", permission.getPermissionType().name(),
            "grantedBy", permission.getGrantedBy().getEmail(),
            "grantedAt", permission.getGrantedAt().toString()
        );
    }

    @DeleteMapping("/{recipientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public Map<String, String> revokePermission(
            @PathVariable Long documentId,
            @PathVariable Long recipientId,
            Authentication authentication) {

        permissionService.revokePermission(
            documentId, recipientId, authentication.getName());

        return Map.of("message", "Permission revoked successfully.");
    }
}
