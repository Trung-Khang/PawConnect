package com.pawconnect.dto.chat;

import java.time.Instant;

public record ConversationResponse(Long id, Long customerId, Long adminId, Long adoptionPostId, Instant createdAt) {
}
