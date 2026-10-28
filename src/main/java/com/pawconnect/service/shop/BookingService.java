package com.pawconnect.service.shop;

import com.pawconnect.dto.booking.BookingRequest;
import com.pawconnect.dto.booking.BookingResponse;

import java.time.LocalDate;
import java.util.List;

public interface BookingService {
    BookingResponse createBooking(BookingRequest request);
    List<BookingResponse> getMyBookings();
    List<BookingResponse> getManagedBookings();
    BookingResponse updateBookingStatus(Long bookingId, String status);
    BookingResponse updateBookingStatus(Long bookingId, String status, String reason);
    BookingResponse cancelMyBooking(Long bookingId, String reason);
    List<BookingResponse> getBusySlots(Long branchId, LocalDate date);
    int autoUpdateNoShowBookings();
}
