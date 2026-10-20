package com.trustvault.document;

import com.trustvault.user.AppUser;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "TV_DOCUMENTS",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_TV_DOCUMENTS_STORAGE_KEY",
        columnNames = "STORAGE_KEY"
    )
)
public class Document {

    @Id
    @SequenceGenerator(
        name = "tv_documents_seq_gen",
        sequenceName = "TV_DOCUMENTS_SEQ",
        allocationSize = 1
    )
    @GeneratedValue(
        strategy = GenerationType.SEQUENCE,
        generator = "tv_documents_seq_gen"
    )
    private Long id;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Column(name = "ORIGINAL_FILENAME", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "STORAGE_KEY", nullable = false, length = 36)
    private String storageKey;

    @Column(name = "CONTENT_TYPE", nullable = false, length = 150)
    private String contentType;

    @Column(name = "SIZE_BYTES", nullable = false)
    private Long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "OWNER_ID", nullable = false)
    private AppUser owner;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "IS_DELETED", nullable = false)
    private boolean deleted = false;

    protected Document() {
        // Required by JPA.
    }

    public Document(
            String title,
            String originalFilename,
            String storageKey,
            String contentType,
            Long sizeBytes,
            AppUser owner) {
        this.title = title;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.owner = owner;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public AppUser getOwner() {
        return owner;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}