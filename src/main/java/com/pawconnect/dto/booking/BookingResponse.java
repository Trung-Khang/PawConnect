package com.pawconnect.dto.booking;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BookingResponse {
    private Long id;
    private Long userId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private Long branchId;
    private String branchName;
    private Long serviceTypeId;
    private String serviceTypeName;
    private BigDecimal servicePrice;
    private Integer durationMinutes;
    private LocalDateTime bookingTime;
    private String status;
    private String reason;
    private LocalDateTime createdAt;
}
