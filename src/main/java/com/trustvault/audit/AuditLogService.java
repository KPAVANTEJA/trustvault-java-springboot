
package com.trustvault.audit;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.trustvault.document.Document;
import com.trustvault.user.AppUser;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


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
    
    
    @Transactional
    public void recordUpload(Document document, AppUser user) {
        AuditLog event = new AuditLog(
            document,
            user,
            "DOCUMENT_UPLOAD",
            "SUCCESS"
        );

        auditLogRepository.saveAndFlush(event);
    }
    
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getRecentAuditLogs(Pageable pageable) {
        return auditLogRepository.findRecentAuditLogs(pageable);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDownloadFailure(
            Document document,
            AppUser user,
            String outcome) {

        AuditLog event = new AuditLog(
                document,
                user,
                "DOCUMENT_DOWNLOAD",
                outcome
        );

        auditLogRepository.saveAndFlush(event);
    }
    
    @Transactional
    public void recordDeletion(Document document, AppUser user) {
        AuditLog event = new AuditLog(
                document,
                user,
                "DOCUMENT_DELETE",
                "SUCCESS"
        );

        auditLogRepository.save(event);
    }
}
