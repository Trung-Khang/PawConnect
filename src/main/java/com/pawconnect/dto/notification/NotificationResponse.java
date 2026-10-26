package com.pawconnect.dto.notification;

import com.pawconnect.entity.Notification;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        String title,
        String content,
        String type,
        String link,
        Long referenceId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getType(),
                n.getLink(),
                n.getReferenceId(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
