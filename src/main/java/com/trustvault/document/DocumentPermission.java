
package com.trustvault.document;

import com.trustvault.user.AppUser;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "TV_DOCUMENT_PERMISSIONS",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_TV_DOC_PERMISSION",
        columnNames = {"DOCUMENT_ID", "USER_ID"}
    )
)
public class DocumentPermission {

    @Id
    @SequenceGenerator(
        name = "tv_doc_permissions_seq_gen",
        sequenceName = "TV_DOC_PERMISSIONS_SEQ",
        allocationSize = 1
    )
    @GeneratedValue(
        strategy = GenerationType.SEQUENCE,
        generator = "tv_doc_permissions_seq_gen"
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DOCUMENT_ID", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "PERMISSION_TYPE", nullable = false, length = 10)
    private PermissionType permissionType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "GRANTED_BY", nullable = false)
    private AppUser grantedBy;

    @Column(name = "GRANTED_AT", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    protected DocumentPermission() {
    }

    public DocumentPermission(
            Document document,
            AppUser user,
            PermissionType permissionType,
            AppUser grantedBy) {
        this.document = document;
        this.user = user;
        this.permissionType = permissionType;
        this.grantedBy = grantedBy;
    }

    @PrePersist
    void beforeInsert() {
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
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

    public PermissionType getPermissionType() {
        return permissionType;
    }

    public AppUser getGrantedBy() {
        return grantedBy;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setPermissionType(PermissionType permissionType) {
        this.permissionType = permissionType;
    }
}
