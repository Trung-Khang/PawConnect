package com.pawconnect.service.auth;

import com.pawconnect.exception.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {
    private static final String SPECIAL_CHARACTERS = "@#$%^&+=!";

    public void validate(String password, String confirmation) {
        if (password == null || password.length() < 8) reject("Mật khẩu phải có ít nhất 8 ký tự.");
        if (password.chars().noneMatch(Character::isUpperCase)) reject("Mật khẩu phải có ít nhất 1 chữ hoa.");
        if (password.chars().noneMatch(Character::isLowerCase)) reject("Mật khẩu phải có ít nhất 1 chữ thường.");
        if (password.chars().noneMatch(Character::isDigit)) reject("Mật khẩu phải có ít nhất 1 chữ số.");
        if (password.chars().noneMatch(value -> SPECIAL_CHARACTERS.indexOf(value) >= 0)) {
            reject("Mật khẩu phải có ít nhất 1 ký tự đặc biệt (@#$%^&+=!).");
        }
        if (!password.equals(confirmation)) reject("Mật khẩu xác nhận không khớp.");
    }

    private void reject(String message) { throw new AuthException(HttpStatus.BAD_REQUEST, message); }
}
