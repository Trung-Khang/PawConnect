package com.pawconnect.controller;

import com.pawconnect.dto.booking.BookingRequest;
import com.pawconnect.dto.booking.BookingResponse;
import com.pawconnect.service.shop.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

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

    @PreAuthorize("hasRole('BRANCH_MANAGER')")
    @PutMapping("/{id}/status")
    public BookingResponse updateBookingStatus(@PathVariable Long id, @RequestParam String status) {
        return bookingService.updateBookingStatus(id, status);
    }
}
