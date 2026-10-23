package com.pawconnect.service.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pawconnect.exception.AuthException;
import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {
    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void acceptsEveryRequiredPasswordCharacteristic() {
        assertDoesNotThrow(() -> validator.validate("Password123!", "Password123!"));
    }

    @Test
    void rejectsEachMissingPasswordCharacteristicAndMismatch() {
        assertThrows(AuthException.class, () -> validator.validate("Pass1!", "Pass1!"));
        assertThrows(AuthException.class, () -> validator.validate("password123!", "password123!"));
        assertThrows(AuthException.class, () -> validator.validate("PASSWORD123!", "PASSWORD123!"));
        assertThrows(AuthException.class, () -> validator.validate("Passwordabc!", "Passwordabc!"));
        assertThrows(AuthException.class, () -> validator.validate("Password123", "Password123"));
        assertThrows(AuthException.class, () -> validator.validate("Password123!", "Password124!"));
    }
}
