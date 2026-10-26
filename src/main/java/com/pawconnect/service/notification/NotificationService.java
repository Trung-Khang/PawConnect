package com.pawconnect.service.notification;

import com.pawconnect.dto.notification.NotificationResponse;
import com.pawconnect.entity.Notification;
import com.pawconnect.entity.User;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.NotificationRepository;
import com.pawconnect.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(String actorEmail) {
        User user = findUser(actorEmail);
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String actorEmail) {
        User user = findUser(actorEmail);
        return notificationRepository.countByRecipientIdAndReadFalse(user.getId());
    }

    public void markAsRead(Long id, String actorEmail) {
        User user = findUser(actorEmail);
        notificationRepository.findByIdAndRecipientId(id, user.getId())
                .ifPresent(n -> n.setRead(true));
    }

    public void markAllAsRead(String actorEmail) {
        User user = findUser(actorEmail);
        notificationRepository.markAllAsReadByRecipientId(user.getId());
    }

    public Notification createNotification(User recipient, String title, String content, String type, String link, Long referenceId) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .title(title)
                .content(content)
                .type(type)
                .link(link)
                .referenceId(referenceId)
                .read(false)
                .build();
        return notificationRepository.save(notification);
    }

    private User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));
    }
}
