package com.pawconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "conversations", indexes = @Index(name = "idx_conversation_customer", columnList = "customer_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_conversation_adoption_participants",
                columnNames = {"customer_id", "admin_id", "adoption_post_id"}))
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    /** Foreign identifier owned by the Adoption module (TV2); no cross-module JPA mapping is introduced here. */
    @Column(name = "adoption_post_id", nullable = false)
    private Long adoptionPostId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    public Conversation(User customer, User admin, Long adoptionPostId) {
        this.customer = Objects.requireNonNull(customer, "Customer must not be null");
        this.admin = Objects.requireNonNull(admin, "Administrator must not be null");
        this.adoptionPostId = Objects.requireNonNull(adoptionPostId, "Adoption post id must not be null");
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getCustomer() { return customer; }
    public User getAdmin() { return admin; }
    public Long getAdoptionPostId() { return adoptionPostId; }
    public Instant getCreatedAt() { return createdAt; }
}
