package com.pawconnect.dto.branch;

import lombok.Data;

@Data
public class BranchResponse {
    private Long id;
    private String name;
    private String address;
    private String phone;
    private Double latitude;
    private Double longitude;
}
