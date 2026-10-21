
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
    
    private final AuditIntegrityService auditIntegrityService;

    public AuditLogService(AuditLogRepository auditLogRepository,
    		AuditIntegrityService auditIntegrityService) {
        this.auditLogRepository = auditLogRepository;
        this.auditIntegrityService = auditIntegrityService;
    }

    @Transactional
    public void recordDownload(Document document, AppUser user) {
        AuditLog event = new AuditLog(
            document,
            user,
            "DOCUMENT_DOWNLOAD",
            "SUCCESS"
        );

        auditIntegrityService.append(event);
    }
    
    
    @Transactional
    public void recordUpload(Document document, AppUser user) {
        AuditLog event = new AuditLog(
            document,
            user,
            "DOCUMENT_UPLOAD",
            "SUCCESS"
        );

        auditIntegrityService.append(event);
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

        auditIntegrityService.append(event);
    }
    
    @Transactional
    public void recordDeletion(Document document, AppUser user) {
        AuditLog event = new AuditLog(
                document,
                user,
                "DOCUMENT_DELETE",
                "SUCCESS"
        );

        auditIntegrityService.append(event);
    }
    
    @Transactional
    public void recordDocumentUpdate(Document document, AppUser user) {
        AuditLog event = new AuditLog(
            document,
            user,
            "DOCUMENT_UPDATE",
            "SUCCESS"
        );

        auditIntegrityService.append(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDocumentUpdateFailure(
            Document document,
            AppUser user,
            String outcome) {

        AuditLog event = new AuditLog(
            document,
            user,
            "DOCUMENT_UPDATE",
            outcome
        );

        auditIntegrityService.append(event);
    }
}
