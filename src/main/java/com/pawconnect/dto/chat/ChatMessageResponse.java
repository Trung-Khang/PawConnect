package com.pawconnect.dto.chat;

import java.time.Instant;

public record ChatMessageResponse(Long id, Long conversationId, Long senderId, String content, Instant sentAt, boolean read) {
}
