
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

    @Column(name = "OUTCOME", nullable = false, length = 20)
    private String outcome;

    @Column(name = "EVENT_TIME", nullable = false, updatable = false)
    private LocalDateTime eventTime;

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
}
