package com.pawconnect.service.shop;

import com.pawconnect.dto.booking.BookingRequest;
import com.pawconnect.dto.booking.BookingResponse;
import java.util.List;

public interface BookingService {
    BookingResponse createBooking(BookingRequest request);
    List<BookingResponse> getMyBookings();
    BookingResponse updateBookingStatus(Long bookingId, String status);
}
