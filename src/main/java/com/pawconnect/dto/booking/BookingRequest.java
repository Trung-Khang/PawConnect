package com.pawconnect.dto.booking;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookingRequest {
    private Long branchId;
    private Long serviceTypeId;
    private LocalDateTime bookingTime;
}
