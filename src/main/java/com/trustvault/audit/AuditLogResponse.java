
package com.trustvault.audit;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long documentId,
        Long userId,
        String action,
        String outcome,
        LocalDateTime eventTime) {
}
