
package com.trustvault.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trustvault.document.Document;
import com.trustvault.user.AppUser;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void recordDownload(Document document, AppUser user) {
        AuditLog event = new AuditLog(
            document,
            user,
            "DOCUMENT_DOWNLOAD",
            "SUCCESS"
        );

        auditLogRepository.save(event);
    }
}
