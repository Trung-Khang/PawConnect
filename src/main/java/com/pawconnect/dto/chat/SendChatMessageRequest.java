package com.pawconnect.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
        @NotNull Long conversationId,
        @NotBlank @Size(max = 4_000) String content) {
}
