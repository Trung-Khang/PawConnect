package com.pawconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "auth_otps")
public class AuthOtp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 254) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private OtpPurpose purpose;
    @Column(nullable = false, length = 64) private String codeHash;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant consumedAt;
    private Instant invalidatedAt;
    @Column(nullable = false) private int attemptCount;
    @Column(nullable = false) private Instant lastSentAt = Instant.now();

    public AuthOtp() { }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getEmail() { return email; }
    public OtpPurpose getPurpose() { return purpose; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getLastSentAt() { return lastSentAt; }
    public void setUser(User user) { this.user = user; }
    public void setEmail(String email) { this.email = email; }
    public void setPurpose(OtpPurpose purpose) { this.purpose = purpose; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public void setInvalidatedAt(Instant invalidatedAt) { this.invalidatedAt = invalidatedAt; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void setLastSentAt(Instant lastSentAt) { this.lastSentAt = lastSentAt; }
}
