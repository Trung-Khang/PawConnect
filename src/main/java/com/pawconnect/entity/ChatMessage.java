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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // Keep the Java/JSON field name "read", but avoid MySQL's reserved READ identifier.
    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType = ChatMessageType.TEXT;

    @Column(name = "context_url", length = 1000) private String contextUrl;
    @Column(name = "context_title", length = 255) private String contextTitle;
    @Column(name = "context_summary", length = 1000) private String contextSummary;
    @Column(name = "context_image_url", length = 1000) private String contextImageUrl;

    protected ChatMessage() {
    }

    public ChatMessage(Conversation conversation, User sender, String content) {
        this.conversation = Objects.requireNonNull(conversation, "Conversation must not be null");
        this.sender = Objects.requireNonNull(sender, "Sender must not be null");
        this.content = Objects.requireNonNull(content, "Content must not be null");
    }

    public static ChatMessage context(Conversation conversation, User sender, String content, String url,
                                      String title, String summary, String imageUrl) {
        ChatMessage message = new ChatMessage(conversation, sender, content);
        message.messageType = ChatMessageType.CONTEXT;
        message.contextUrl = url; message.contextTitle = title; message.contextSummary = summary; message.contextImageUrl = imageUrl;
        return message;
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
    public ChatMessageType getMessageType() { return messageType; }
    public String getContextUrl() { return contextUrl; }
    public String getContextTitle() { return contextTitle; }
    public String getContextSummary() { return contextSummary; }
    public String getContextImageUrl() { return contextImageUrl; }
    public void markRead() { this.read = true; }
}
