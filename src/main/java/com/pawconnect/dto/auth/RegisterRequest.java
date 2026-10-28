package com.pawconnect.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Vui lòng nhập tên đăng nhập.") @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "Tên đăng nhập cần 3–50 ký tự, chỉ dùng chữ không dấu, số, dấu chấm (.), gạch dưới (_) hoặc gạch ngang (-); không có dấu cách.") String username,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password,
        @NotBlank @Size(max = 72) String confirmPassword,
        @NotBlank @Size(max = 30) String phone) {
}
