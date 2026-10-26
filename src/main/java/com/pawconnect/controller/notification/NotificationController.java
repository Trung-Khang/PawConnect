package com.pawconnect.controller.notification;

import com.pawconnect.dto.notification.NotificationResponse;
import com.pawconnect.service.notification.NotificationService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> getMyNotifications(@AuthenticationPrincipal UserDetails principal) {
        return notificationService.getMyNotifications(principal.getUsername());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal UserDetails principal) {
        long count = notificationService.getUnreadCount(principal.getUsername());
        return Map.of("count", count, "unreadCount", count);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        notificationService.markAsRead(id, principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal UserDetails principal) {
        notificationService.markAllAsRead(principal.getUsername());
        return ResponseEntity.noContent().build();
    }
}
