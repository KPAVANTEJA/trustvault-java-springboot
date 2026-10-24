package com.trustvault.audit;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long documentId,
        String documentTitle,
        Long userId,
        String userEmail,
        String userName,
        String action,
        String outcome,
        LocalDateTime eventTime,
        Long targetUserId,
        String targetUserEmail,
        String previousPermissionType,
        String newPermissionType) {
}