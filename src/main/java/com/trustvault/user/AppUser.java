package com.trustvault.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(
    name = "TV_USERS",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_TV_USERS_EMAIL",
        columnNames = "EMAIL"
    )
)
public class AppUser {

	@Id
	@SequenceGenerator(
	    name = "tv_users_seq_gen",
	    sequenceName = "TV_USERS_SEQ",
	    allocationSize = 1
	)
	@GeneratedValue(
	    strategy = GenerationType.SEQUENCE,
	    generator = "tv_users_seq_gen"
	)
	private Long id;

    @Column(name = "EMAIL", nullable = false, length = 254)
    private String email;

    @Column(name = "FULL_NAME", nullable = false, length = 120)
    private String fullName;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE_NAME", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled = true;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AppUser() {
        // Required by JPA.
    }

    public AppUser(
            String email,
            String fullName,
            String passwordHash,
            UserRole role) {
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
    }

    @PrePersist
    void beforeInsert() {
        email = email.trim().toLowerCase(Locale.ROOT);
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}