package com.pawconnect.controller.auth;

import com.pawconnect.dto.auth.AuthMessageResponse;
import com.pawconnect.dto.auth.AuthResponse;
import com.pawconnect.dto.auth.ForgotPasswordRequest;
import com.pawconnect.dto.auth.LoginRequest;
import com.pawconnect.dto.auth.OtpVerificationRequest;
import com.pawconnect.dto.auth.ProfileUpdateRequest;
import com.pawconnect.dto.auth.RefreshTokenRequest;
import com.pawconnect.dto.auth.RegisterRequest;
import com.pawconnect.dto.auth.ResetPasswordRequest;
import com.pawconnect.dto.auth.ResetVerificationResponse;
import com.pawconnect.dto.auth.UserResponse;
import com.pawconnect.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    public ResponseEntity<AuthMessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
    @PostMapping("/verify-email")
    public AuthMessageResponse verifyEmail(@Valid @RequestBody OtpVerificationRequest request) { return authService.verifyEmail(request.identity(), request.code()); }
    @PostMapping("/resend-verification")
    public AuthMessageResponse resendVerification(@Valid @RequestBody ForgotPasswordRequest request) { return authService.resendVerification(request.identity()); }
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }
    @PostMapping("/refresh-token")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) { return authService.refresh(request.refreshToken()); }
    @PostMapping("/forgot-password")
    public AuthMessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) { return authService.forgotPassword(request); }
    @PostMapping("/verify-reset-otp")
    public ResetVerificationResponse verifyResetOtp(@Valid @RequestBody OtpVerificationRequest request) { return authService.verifyResetOtp(request.identity(), request.code()); }
    @PostMapping("/reset-password")
    public AuthMessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) { return authService.resetPassword(request); }
    @PostMapping("/logout")
    public AuthMessageResponse logout() { return new AuthMessageResponse("Đăng xuất thành công.", true, 0); }
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserDetails user) { return authService.getCurrentUser(user.getUsername()); }
    @PutMapping("/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal UserDetails user,
                                      @Valid @RequestBody ProfileUpdateRequest request) {
        return authService.updateCurrentProfile(user.getUsername(), request);
    }
}
