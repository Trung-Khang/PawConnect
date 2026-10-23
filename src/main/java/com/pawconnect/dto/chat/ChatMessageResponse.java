package com.pawconnect.dto.chat;

import java.time.Instant;
import com.pawconnect.entity.ChatMessageType;

public record ChatMessageResponse(Long id, Long conversationId, Long senderId, String senderName, String senderRole,
                                  String content, Instant sentAt, boolean read, ChatMessageType messageType,
                                  String contextUrl, String contextTitle, String contextSummary, String contextImageUrl) {
}
