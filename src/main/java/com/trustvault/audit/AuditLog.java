
package com.trustvault.audit;

import java.time.LocalDateTime;

import com.trustvault.document.Document;
import com.trustvault.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "TV_AUDIT_LOGS")
public class AuditLog {

    @Id
    @GeneratedValue(
        strategy = GenerationType.SEQUENCE,
        generator = "tv_audit_log_generator"
    )
    @SequenceGenerator(
        name = "tv_audit_log_generator",
        sequenceName = "TV_AUDIT_LOG_SEQ",
        allocationSize = 1
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DOCUMENT_ID", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private AppUser user;

    @Column(name = "ACTION", nullable = false, length = 50)
    private String action;

    @Column(name = "OUTCOME", nullable = false, length = 32)
    private String outcome;

    @Column(name = "EVENT_TIME", nullable = false, updatable = false)
    private LocalDateTime eventTime;
    
    @Column(name = "PREVIOUS_HASH", length = 64)
    private String previousHash;

    @Column(name = "EVENT_HASH", length = 64)
    private String eventHash;
    
 // Snapshot of the user affected by a permission change.
 // No foreign key: historical evidence must survive user deletion.
 @Column(name = "TARGET_USER_ID")
 private Long targetUserId;

 @Column(name = "TARGET_USER_EMAIL", length = 320)
 private String targetUserEmail;

 @Column(name = "PREVIOUS_PERMISSION_TYPE", length = 20)
 private String previousPermissionType;

 @Column(name = "NEW_PERMISSION_TYPE", length = 20)
 private String newPermissionType;

    protected AuditLog() {
    }

    public AuditLog(
            Document document,
            AppUser user,
            String action,
            String outcome) {
        this.document = document;
        this.user = user;
        this.action = action;
        this.outcome = outcome;
    }

    @PrePersist
    protected void onCreate() {
        if (eventTime == null) {
            eventTime = LocalDateTime.now();
        }
    }
    
    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getEventHash() {
        return eventHash;
    }

    public void setEventHash(String eventHash) {
        this.eventHash = eventHash;
    }
    
    
    public Long getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public AppUser getUser() {
        return user;
    }

    public String getAction() {
        return action;
    }

    public String getOutcome() {
        return outcome;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }
    
    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getTargetUserEmail() {
        return targetUserEmail;
    }

    public void setTargetUserEmail(String targetUserEmail) {
        this.targetUserEmail = targetUserEmail;
    }

    public String getPreviousPermissionType() {
        return previousPermissionType;
    }

    public void setPreviousPermissionType(String previousPermissionType) {
        this.previousPermissionType = previousPermissionType;
    }

    public String getNewPermissionType() {
        return newPermissionType;
    }

    public void setNewPermissionType(String newPermissionType) {
        this.newPermissionType = newPermissionType;
    }
}
