package com.pawconnect.dto.admin;

import java.math.BigDecimal;

/** Branch information exposed through the system administration dashboard with operational metrics. */
public record AdminBranchResponse(
        Long id,
        String code,
        String name,
        String address,
        String phone,
        Double latitude,
        Double longitude,
        Integer totalStock,
        Integer productStock,
        Integer productTypesCount,
        Integer puppyStock,
        Integer puppyListingsCount,
        Integer adoptionTotal,
        Integer adoptionAvailable,
        Integer soldOrdersCount,
        Integer totalItemsSold,
        BigDecimal totalRevenue) {

    public AdminBranchResponse(Long id, String code, String name, String address, String phone, Double latitude, Double longitude) {
        this(id, code, name, address, phone, latitude, longitude, 0, 0, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO);
    }
}
