package com.pawconnect.dto.chat;

import jakarta.validation.constraints.NotNull;

public record CreateSupportConversationRequest(
        @NotNull Long branchId,
        Long productId,
        Long puppyListingId) {
}
