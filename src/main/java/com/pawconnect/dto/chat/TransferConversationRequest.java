package com.pawconnect.dto.chat;

import jakarta.validation.constraints.NotNull;

public record TransferConversationRequest(@NotNull Long staffId) {
}
