package com.pawconnect.dto.booking;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookingResponse {
    private Long id;
    private Long userId;
    private Long branchId;
    private Long serviceTypeId;
    private String serviceTypeName;
    private LocalDateTime bookingTime;
    private String status;
}
