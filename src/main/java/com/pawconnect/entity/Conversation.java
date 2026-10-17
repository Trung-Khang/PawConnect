package com.pawconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conversation_customer", columnList = "customer_id"),
        @Index(name = "idx_conversation_branch_status", columnList = "branch_id,status")
}, uniqueConstraints = @UniqueConstraint(name = "uk_conversation_customer",
        columnNames = "customer_id"))
public class Conversation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private User assignedStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationStatus status;

    @Column(name = "adoption_post_id")
    private Long adoptionPostId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "puppy_listing_id")
    private Long puppyListingId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() { }

    private Conversation(User customer, Branch branch, ConversationType type, Long adoptionPostId,
                         Long productId, Long puppyListingId) {
        this.customer = Objects.requireNonNull(customer, "Customer must not be null");
        this.branch = Objects.requireNonNull(branch, "Branch must not be null");
        this.type = Objects.requireNonNull(type, "Conversation type must not be null");
        this.status = ConversationStatus.OPEN;
        this.adoptionPostId = adoptionPostId;
        this.productId = productId;
        this.puppyListingId = puppyListingId;
    }

    public static Conversation support(User customer, Branch branch, Long productId, Long puppyListingId) {
        return new Conversation(customer, branch, ConversationType.SUPPORT, null, productId, puppyListingId);
    }

    public static Conversation adoption(User customer, Branch branch, Long adoptionPostId) {
        return new Conversation(customer, branch, ConversationType.ADOPTION,
                Objects.requireNonNull(adoptionPostId, "Adoption post id must not be null"), null, null);
    }

    @PrePersist
    void initializeTimestamps() {
        if (createdAt == null) createdAt = Instant.now();
        if (lastMessageAt == null) lastMessageAt = createdAt;
        if (type == null) type = ConversationType.ADOPTION;
        if (status == null) status = ConversationStatus.OPEN;
    }

    public void claim(User staff, Instant now) {
        this.assignedStaff = Objects.requireNonNull(staff, "Staff must not be null");
        this.assignedAt = Objects.requireNonNull(now, "Claim time must not be null");
        this.status = ConversationStatus.CLAIMED;
    }

    public void release() { this.assignedStaff = null; this.assignedAt = null; this.status = ConversationStatus.OPEN; }
    public void close() { this.status = ConversationStatus.CLOSED; }
    public void touch(Instant now) { this.lastMessageAt = now; }

    public Long getId() { return id; }
    public User getCustomer() { return customer; }
    public User getAssignedStaff() { return assignedStaff; }
    public Branch getBranch() { return branch; }
    public ConversationType getType() { return type; }
    public ConversationStatus getStatus() { return status; }
    public Long getAdoptionPostId() { return adoptionPostId; }
    public Long getProductId() { return productId; }
    public Long getPuppyListingId() { return puppyListingId; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }
}
