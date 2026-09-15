package com.pawconnect.dto.booking;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class BookingRequest {
    @NotNull(message = "Branch ID is required")
    private Long branchId;
    
    @NotNull(message = "Service Type ID is required")
    private Long serviceTypeId;
    
    @NotNull(message = "Booking Time is required")
    private LocalDateTime bookingTime;
}
