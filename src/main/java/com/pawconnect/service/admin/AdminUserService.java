package com.pawconnect.service.admin;

import com.pawconnect.dto.admin.AdminUserResponse;
import com.pawconnect.dto.admin.AdminUserUpdateRequest;
import com.pawconnect.entity.User;
import com.pawconnect.exception.BusinessException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.AdoptionApplicationRepository;
import com.pawconnect.repository.AdoptionPostRepository;
import com.pawconnect.repository.AuthOtpRepository;
import com.pawconnect.repository.CartRepository;
import com.pawconnect.repository.ChatMessageRepository;
import com.pawconnect.repository.ConversationRepository;
import com.pawconnect.repository.NotificationRepository;
import com.pawconnect.repository.OrderRepository;
import com.pawconnect.repository.PasswordResetSessionRepository;
import com.pawconnect.repository.ServiceBookingRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.security.SecurityUtils;
import com.pawconnect.util.UserDisplayName;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuthOtpRepository authOtpRepository;
    private final PasswordResetSessionRepository passwordResetSessionRepository;
    private final NotificationRepository notificationRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final ServiceBookingRepository serviceBookingRepository;
    private final AdoptionApplicationRepository adoptionApplicationRepository;
    private final AdoptionPostRepository adoptionPostRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public AdminUserService(UserRepository userRepository,
                            AuthOtpRepository authOtpRepository,
                            PasswordResetSessionRepository passwordResetSessionRepository,
                            NotificationRepository notificationRepository,
                            CartRepository cartRepository,
                            OrderRepository orderRepository,
                            ServiceBookingRepository serviceBookingRepository,
                            AdoptionApplicationRepository adoptionApplicationRepository,
                            AdoptionPostRepository adoptionPostRepository,
                            ConversationRepository conversationRepository,
                            ChatMessageRepository chatMessageRepository) {
        this.userRepository = userRepository;
        this.authOtpRepository = authOtpRepository;
        this.passwordResetSessionRepository = passwordResetSessionRepository;
        this.notificationRepository = notificationRepository;
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.serviceBookingRepository = serviceBookingRepository;
        this.adoptionApplicationRepository = adoptionApplicationRepository;
        this.adoptionPostRepository = adoptionPostRepository;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /** Returns account information safe for administration; password hashes are never mapped to the response. */
    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminUserResponse updateUser(Long userId, AdminUserUpdateRequest request) {
        User user = requireUser(userId);
        String fullName = request.fullName().trim();
        String phone = normalizePhone(request.phone());
        if (fullName.isBlank()) throw new BusinessException("Họ và tên không được để trống.");
        if (!phone.matches("(03|05|07|08|09)\\d{8}")) {
            throw new BusinessException("Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 03, 05, 07, 08 hoặc 09.");
        }
        if (!phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new BusinessException("Số điện thoại đã được sử dụng.");
        }
        if (user.getId().equals(SecurityUtils.getCurrentUserId()) && (!request.active() || request.locked())) {
            throw new BusinessException("Không thể vô hiệu hóa hoặc khóa tài khoản quản trị đang đăng nhập.");
        }
        user.updateProfile(fullName, phone);
        user.setActive(request.active());
        user.setLocked(request.locked());
        return toResponse(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = requireUser(userId);
        if (user.getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException("Không thể xóa tài khoản quản trị đang đăng nhập.");
        }
        if (hasBusinessData(userId)) {
            throw new BusinessException("Không thể xóa tài khoản vì dữ liệu nghiệp vụ liên quan vẫn còn. Hãy khóa tài khoản thay vì xóa.");
        }

        // Clean up auxiliary account artifacts (cart, otps, reset sessions, notifications)
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cartRepository.delete(cart);
            cartRepository.flush();
        });
        authOtpRepository.deleteByUserId(userId);
        passwordResetSessionRepository.deleteByUserId(userId);
        notificationRepository.deleteByRecipientId(userId);

        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Không thể xóa tài khoản vì dữ liệu nghiệp vụ liên quan vẫn còn. Hãy khóa tài khoản thay vì xóa.");
        }
    }

    private boolean hasBusinessData(Long userId) {
        return orderRepository.existsByUserId(userId)
                || serviceBookingRepository.existsByUserId(userId)
                || adoptionApplicationRepository.existsByApplicantId(userId)
                || adoptionPostRepository.existsByCreatedById(userId)
                || conversationRepository.existsByCustomerId(userId)
                || conversationRepository.existsByAssignedStaffId(userId)
                || chatMessageRepository.existsBySenderId(userId);
    }

    private User requireUser(Long userId) {
        return userRepository.findLockedById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.trim().replaceAll("[\\s().-]", "").replaceFirst("^\\+84", "0");
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), UserDisplayName.of(user), user.getEmail(), user.getPhone(), user.getAvatarUrl(),
                user.getRole().getName().name(), user.isActive(), user.isLocked(), user.isEmailVerified(), user.getCreatedAt());
    }
}
