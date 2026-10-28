package com.pawconnect.service.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.ServiceBooking;
import com.pawconnect.entity.ServiceType;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.ServiceBookingRepository;
import com.pawconnect.repository.ServiceTypeRepository;
import com.pawconnect.repository.UserRepository;
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
class BookingBranchAuthorizationTest {
    @Mock private ServiceBookingRepository bookings;
    @Mock private BranchRepository branches;
    @Mock private ServiceTypeRepository serviceTypes;
    @Mock private UserRepository userRepository;
    @Mock private com.pawconnect.service.notification.NotificationService notificationService;
    @InjectMocks private BookingServiceImpl service;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerCannotUpdateAnotherBranchBooking() {
        authenticate(10L);
        ServiceBooking booking = bookingForBranch(11L);
        when(bookings.findById(42L)).thenReturn(Optional.of(booking));

        assertThrows(AccessDeniedException.class, () -> service.updateBookingStatus(42L, "CONFIRMED"));
        verify(bookings, never()).save(booking);
    }

    @Test
    void managerCanUpdateOwnBranchBooking() {
        authenticate(10L);
        ServiceBooking booking = bookingForBranch(10L);
        when(bookings.findById(42L)).thenReturn(Optional.of(booking));

        assertEquals("CONFIRMED", service.updateBookingStatus(42L, "CONFIRMED").getStatus());
        verify(bookings).save(booking);
    }

    @Test
    void adminCanUpdateAnyBranchBooking() {
        CustomUserDetails principal = new CustomUserDetails(1L, null, "admin@example.com", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        ServiceBooking booking = bookingForBranch(11L);
        when(bookings.findById(42L)).thenReturn(Optional.of(booking));

        assertEquals("CONFIRMED", service.updateBookingStatus(42L, "CONFIRMED").getStatus());
        verify(bookings).save(booking);
    }

    private void authenticate(Long branchId) {
        CustomUserDetails principal = new CustomUserDetails(1L, branchId, "manager@example.com", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ServiceBooking bookingForBranch(Long branchId) {
        Branch branch = new Branch();
        branch.setId(branchId);
        ServiceType serviceType = new ServiceType();
        serviceType.setId(3L);
        serviceType.setName("Grooming");
        ServiceBooking booking = new ServiceBooking();
        booking.setId(42L);
        booking.setBranch(branch);
        booking.setServiceType(serviceType);
        booking.setStatus("PENDING");
        return booking;
    }
}
