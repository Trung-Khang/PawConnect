package com.pawconnect.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawconnect.entity.Cart;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.exception.BusinessException;
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
import com.pawconnect.security.CustomUserDetails;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminUserServiceTest {

    private UserRepository userRepository;
    private AuthOtpRepository authOtpRepository;
    private PasswordResetSessionRepository passwordResetSessionRepository;
    private NotificationRepository notificationRepository;
    private CartRepository cartRepository;
    private OrderRepository orderRepository;
    private ServiceBookingRepository serviceBookingRepository;
    private AdoptionApplicationRepository adoptionApplicationRepository;
    private AdoptionPostRepository adoptionPostRepository;
    private ConversationRepository conversationRepository;
    private ChatMessageRepository chatMessageRepository;
    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authOtpRepository = mock(AuthOtpRepository.class);
        passwordResetSessionRepository = mock(PasswordResetSessionRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        cartRepository = mock(CartRepository.class);
        orderRepository = mock(OrderRepository.class);
        serviceBookingRepository = mock(ServiceBookingRepository.class);
        adoptionApplicationRepository = mock(AdoptionApplicationRepository.class);
        adoptionPostRepository = mock(AdoptionPostRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        chatMessageRepository = mock(ChatMessageRepository.class);

        adminUserService = new AdminUserService(
                userRepository,
                authOtpRepository,
                passwordResetSessionRepository,
                notificationRepository,
                cartRepository,
                orderRepository,
                serviceBookingRepository,
                adoptionApplicationRepository,
                adoptionPostRepository,
                conversationRepository,
                chatMessageRepository
        );

        CustomUserDetails adminDetails = new CustomUserDetails(
                1L, null, "admin", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        Authentication auth = new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createTestUser(Long id) {
        Role role = new Role(RoleName.CUSTOMER);
        User user = new User("tester", "Test User", "test@pawconnect.local", "hash", "0912345678", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void deleteUser_whenDeletingSelf_throwsBusinessException() {
        Long adminId = 1L;
        User adminUser = createTestUser(adminId);
        when(userRepository.findLockedById(adminId)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> adminUserService.deleteUser(adminId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể xóa tài khoản quản trị đang đăng nhập");

        verify(userRepository, never()).delete(adminUser);
    }

    @Test
    void deleteUser_whenHasOrders_throwsBusinessException() {
        Long userId = 99L;
        User user = createTestUser(userId);
        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(orderRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.deleteUser(userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể xóa tài khoản vì dữ liệu nghiệp vụ liên quan vẫn còn");

        verify(userRepository, never()).delete(user);
    }

    @Test
    void deleteUser_whenHasServiceBookings_throwsBusinessException() {
        Long userId = 99L;
        User user = createTestUser(userId);
        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(serviceBookingRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.deleteUser(userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể xóa tài khoản vì dữ liệu nghiệp vụ liên quan vẫn còn");

        verify(userRepository, never()).delete(user);
    }

    @Test
    void deleteUser_whenHasOnlyAuxiliaryData_deletesSuccessfully() {
        Long userId = 99L;
        User user = createTestUser(userId);
        Cart cart = Cart.builder().id(10L).userId(userId).build();

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        adminUserService.deleteUser(userId);

        verify(cartRepository).delete(cart);
        verify(authOtpRepository).deleteByUserId(userId);
        verify(passwordResetSessionRepository).deleteByUserId(userId);
        verify(notificationRepository).deleteByRecipientId(userId);
        verify(userRepository).delete(user);
    }
}

