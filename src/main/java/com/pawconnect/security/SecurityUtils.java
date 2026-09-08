package com.pawconnect.security;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

public class SecurityUtils {
    public static Long getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String mockUserId = request.getHeader("X-Mock-User-Id");
            if (mockUserId != null && !mockUserId.isEmpty()) {
                return Long.parseLong(mockUserId);
            }
        }
        return 1L; // Fallback default
    }
}
