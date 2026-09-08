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
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_conversation_time", columnList = "conversation_id,sent_at"))
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 4_000)
    private String content;

    @Column(nullable = false, updatable = false, name = "sent_at")
    private Instant sentAt;

    @Column(nullable = false)
    private boolean read;

    protected ChatMessage() {
    }

    public ChatMessage(Conversation conversation, User sender, String content) {
        this.conversation = Objects.requireNonNull(conversation, "Conversation must not be null");
        this.sender = Objects.requireNonNull(sender, "Sender must not be null");
        this.content = Objects.requireNonNull(content, "Content must not be null");
    }

    @PrePersist
    void setSentAt() {
        if (sentAt == null) sentAt = Instant.now();
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getSender() { return sender; }
    public String getContent() { return content; }
    public Instant getSentAt() { return sentAt; }
    public boolean isRead() { return read; }
    public void markRead() { this.read = true; }
}
