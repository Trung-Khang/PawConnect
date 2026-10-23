package com.pawconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** Null only for legacy seed users; newly registered accounts must provide one. */
    @Column(unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(unique = true, length = 30)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean emailVerified = true;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(length = 1_000)
    private String avatarUrl;

    @Column(length = 255)
    private String avatarPublicId;

    /** Seed V3 key used by TV1 and TV2 to resolve a shared user ID. */
    @Column(unique = true, length = 100)
    private String seedKey;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /** Only BRANCH_MANAGER users are assigned to a branch. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String fullName, String email, String password, String phone, Role role) {
        this.fullName = Objects.requireNonNull(fullName, "Full name must not be null");
        this.email = Objects.requireNonNull(email, "Email must not be null");
        this.password = Objects.requireNonNull(password, "Password must not be null");
        this.phone = phone;
        this.role = Objects.requireNonNull(role, "Role must not be null");
    }

    public User(String username, String fullName, String email, String password, String phone, Role role) {
        this(fullName, email, password, phone, role);
        this.username = Objects.requireNonNull(username, "Username must not be null");
        this.emailVerified = false;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getPhone() { return phone; }
    public String getAvatarUrl() { return avatarUrl; }
    public Role getRole() { return role; }
    public String getAvatarPublicId() { return avatarPublicId; }
    public String getSeedKey() { return seedKey; }
    public Branch getBranch() { return branch; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isActive() { return active; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean isLocked() { return locked; }

    public void updateSeedProfile(String seedKey, Role role, Branch branch) {
        this.seedKey = seedKey;
        this.role = Objects.requireNonNull(role, "Role must not be null");
        this.branch = branch;
    }

    public void updateAvatar(String avatarUrl, String avatarPublicId) {
        this.avatarUrl = avatarUrl;
        this.avatarPublicId = avatarPublicId;
    }

    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public void setActive(boolean active) { this.active = active; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setPassword(String password) { this.password = Objects.requireNonNull(password, "Password must not be null"); }
    public void updateProfile(String fullName, String phone) {
        this.fullName = Objects.requireNonNull(fullName, "Full name must not be null");
        this.phone = Objects.requireNonNull(phone, "Phone must not be null");
    }
}
