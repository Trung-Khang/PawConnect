package com.pawconnect.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityUtilsTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingPrincipalCannotBecomeSeedUser() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class, SecurityUtils::getCurrentUserId);
    }
}
