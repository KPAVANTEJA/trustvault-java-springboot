
package com.trustvault.audit;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final AuditIntegrityService auditIntegrityService;

    public AuditLogController(AuditLogService auditLogService,
            AuditIntegrityService auditIntegrityService) {
        this.auditLogService = auditLogService;
        this.auditIntegrityService = auditIntegrityService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditLogResponse> getAuditHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Page must be non-negative and size must be between 1 and 100"
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        return auditLogService.getRecentAuditLogs(pageable);
    }

    @GetMapping("/integrity")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> verifyIntegrity() {
        try {
            auditIntegrityService.verifyIntegrity();
            return Map.of(
                "status", "OK",
                "message", "Audit chain integrity verified successfully."
            );
        } catch (IllegalStateException ex) {
            return Map.of(
                "status", "FAILED",
                "message", ex.getMessage()
            );
        }
    }
}
