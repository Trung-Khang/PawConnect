package com.pawconnect.dto.order;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class OrderRequest {
    @NotNull(message = "Branch ID is required")
    private Long branchId;
}
