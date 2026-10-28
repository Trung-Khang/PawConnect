package com.pawconnect.service.shop;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Order;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.OrderRepository;
import com.pawconnect.repository.OrderCancellationRequestRepository;
import com.pawconnect.repository.OrderStatusEventRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.repository.PuppyListingRepository;
import com.pawconnect.security.CustomUserDetails;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class OrderServiceAuthorizationTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PuppyListingRepository puppyListingRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private CartService cartService;
    @Mock private OrderStatusEventRepository statusEventRepository;
    @Mock private OrderCancellationRequestRepository cancellationRequestRepository;
    @InjectMocks private OrderServiceImpl service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void branchManagerSeesOnlyOwnBranchOrders() {
        authenticateManager(10L);

        service.getManagedOrders();

        verify(orderRepository).findByBranchIdOrderByCreatedAtDesc(10L);
        verify(orderRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void branchManagerCannotUpdateAnotherBranchOrder() {
        authenticateManager(10L);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(orderForBranch(11L)));

        assertThrows(AccessDeniedException.class, () -> service.updateOrderStatus(42L, "SHIPPING", null));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void branchManagerCanUpdateOwnBranchOrder() {
        authenticateManager(10L);
        Order order = orderForBranch(10L);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));

        assertDoesNotThrow(() -> service.updateOrderStatus(42L, "SHIPPING", null));
        verify(orderRepository).save(order);
    }

    private void authenticateManager(Long branchId) {
        CustomUserDetails principal = new CustomUserDetails(1L, branchId, "manager@example.com", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Order orderForBranch(Long branchId) {
        Branch branch = new Branch();
        branch.setId(branchId);
        Order order = new Order();
        order.setBranch(branch);
        order.setStatus("PAID");
        order.setPaymentStatus("PAID");
        return order;
    }
}
