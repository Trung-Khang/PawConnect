package com.pawconnect.controller;

import com.pawconnect.dto.booking.BookingRequest;
import com.pawconnect.dto.booking.BookingResponse;
import com.pawconnect.service.shop.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class ServiceBookingController {

    private final BookingService bookingService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public BookingResponse createBooking(@RequestBody @Valid BookingRequest request) {
        return bookingService.createBooking(request);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/my")
    public List<BookingResponse> getMyBookings() {
        return bookingService.getMyBookings();
    }

    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'ADMIN')")
    @GetMapping("/manage")
    public List<BookingResponse> getManagedBookings() {
        return bookingService.getManagedBookings();
    }

    @GetMapping("/busy-slots")
    public List<BookingResponse> getBusySlots(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.getBusySlots(branchId, date);
    }

    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'ADMIN')")
    @PutMapping("/{id}/status")
    public BookingResponse updateBookingStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String reason) {
        String finalReason = body != null && body.containsKey("reason") ? body.get("reason") : reason;
        return bookingService.updateBookingStatus(id, status, finalReason);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/{id}/cancel")
    public BookingResponse cancelMyBooking(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return bookingService.cancelMyBooking(id, reason);
    }
}
