package com.pawconnect.dto.chat;

import com.pawconnect.entity.ConversationStatus;
import com.pawconnect.entity.ConversationType;
import java.time.Instant;

public record ConversationResponse(Long id, ConversationType type, ConversationStatus status,
                                   Long customerId, String customerName, String customerAvatarUrl,
                                   Long assignedStaffId, String assignedStaffName,
                                   Long branchId, Long adoptionPostId, Long productId, Long puppyListingId,
                                   Instant assignedAt, Instant lastMessageAt, Instant createdAt, long unreadCount,
                                   String lastMessagePreview) { }
