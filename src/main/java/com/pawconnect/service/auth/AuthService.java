package com.pawconnect.service.auth;

import com.pawconnect.dto.auth.AuthMessageResponse;
import com.pawconnect.dto.auth.AuthResponse;
import com.pawconnect.dto.auth.ForgotPasswordRequest;
import com.pawconnect.dto.auth.LoginRequest;
import com.pawconnect.dto.auth.ProfileUpdateRequest;
import com.pawconnect.dto.auth.RegisterRequest;
import com.pawconnect.dto.auth.ResetPasswordRequest;
import com.pawconnect.dto.auth.ResetVerificationResponse;
import com.pawconnect.dto.auth.UserResponse;
import com.pawconnect.entity.OtpPurpose;
import com.pawconnect.entity.PasswordResetSession;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.exception.AuthException;
import com.pawconnect.exception.ResourceConflictException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.PasswordResetSessionRepository;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.security.JwtService;
import com.pawconnect.util.UserDisplayName;
import io.jsonwebtoken.JwtException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String GENERIC_FORGOT_MESSAGE = "Nếu tài khoản tồn tại trong hệ thống, mã OTP đã được gửi.";
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final PasswordResetSessionRepository resetSessions;
    private final PasswordPolicyValidator passwordPolicy;
    private final Duration resetTokenTtl;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder, JwtService jwtService,
                       OtpService otpService, PasswordResetSessionRepository resetSessions,
                       PasswordPolicyValidator passwordPolicy,
                       @Value("${app.auth.reset-token-ttl:PT10M}") Duration resetTokenTtl) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.otpService = otpService;
        this.resetSessions = resetSessions;
        this.passwordPolicy = passwordPolicy;
        this.resetTokenTtl = resetTokenTtl;
    }

    @Transactional
    public AuthMessageResponse register(RegisterRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        validateRegistration(request, username, email, phone);
        User existing = users.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null) {
            if (existing.isEmailVerified()) throw new ResourceConflictException("Email đã được sử dụng.");
            return dispatch(otpService.issue(existing, OtpPurpose.VERIFY_EMAIL, true));
        }
        if (users.existsByUsernameIgnoreCase(username)) throw new ResourceConflictException("Tên đăng nhập đã được sử dụng.");
        if (users.existsByPhone(phone)) throw new ResourceConflictException("Số điện thoại đã được sử dụng.");
        Role customer = roles.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role has not been initialized"));
        User user = new User(username, request.fullName().trim(), email, passwordEncoder.encode(request.password()), phone, customer);
        users.saveAndFlush(user);
        return dispatch(otpService.issue(user, OtpPurpose.VERIFY_EMAIL, false));
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthMessageResponse verifyEmail(String identity, String code) {
        User user = findByIdentity(identity);
        if (user.isEmailVerified()) throw new AuthException(HttpStatus.BAD_REQUEST, "Tài khoản này đã được xác minh email.");
        user = otpService.verify(user, requireOtp(code), OtpPurpose.VERIFY_EMAIL);
        user.setEmailVerified(true);
        return new AuthMessageResponse("Xác minh tài khoản thành công. Bạn có thể đăng nhập.", true, 0);
    }

    @Transactional
    public AuthMessageResponse resendVerification(String identity) {
        User user = findByIdentity(identity);
        if (user.isEmailVerified()) throw new AuthException(HttpStatus.BAD_REQUEST, "Tài khoản này đã được xác minh email.");
        return dispatch(otpService.issue(user, OtpPurpose.VERIFY_EMAIL, true));
    }

    public AuthResponse login(LoginRequest request) {
        String identity = request.usernameOrEmail() != null && !request.usernameOrEmail().isBlank()
                ? request.usernameOrEmail() : request.email();
        if (identity == null || identity.isBlank() || request.password() == null || request.password().isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tên đăng nhập/email và mật khẩu.");
        }
        User user = findByIdentityOrNull(identity);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) throw new BadCredentialsException("Invalid credentials");
        if (!user.isActive()) throw new AuthException(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa.");
        if (user.isLocked()) throw new AuthException(HttpStatus.FORBIDDEN, "Tài khoản đang bị khóa.");
        if (!user.isEmailVerified()) throw new AuthException(HttpStatus.FORBIDDEN, "Tài khoản chưa xác minh email. Vui lòng xác minh hoặc gửi lại OTP.");
        return tokensFor(user);
    }

    public AuthResponse refresh(String refreshToken) {
        try {
            if (!jwtService.isRefreshToken(refreshToken)) throw new BadCredentialsException("Invalid refresh token");
            User user = findByEmail(jwtService.getEmail(refreshToken));
            if (!user.isActive() || user.isLocked()) throw new BadCredentialsException("Invalid refresh token");
            return tokensFor(user);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid refresh token");
        }
    }

    public UserResponse getCurrentUser(String email) { return toResponse(findByEmail(email)); }

    @Transactional
    public UserResponse updateCurrentProfile(String email, ProfileUpdateRequest request) {
        User user = findByEmail(email);
        String fullName = request.fullName() == null ? "" : request.fullName().trim();
        String phone = normalizePhone(request.phone());
        if (fullName.isBlank()) throw new AuthException(HttpStatus.BAD_REQUEST, "Họ và tên không được để trống.");
        if (fullName.length() > 120) throw new AuthException(HttpStatus.BAD_REQUEST, "Họ và tên không được vượt quá 120 ký tự.");
        if (phone == null || !phone.matches("(03|05|07|08|09)\\d{8}")) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 03, 05, 07, 08 hoặc 09.");
        }
        if (!phone.equals(user.getPhone()) && users.existsByPhone(phone)) {
            throw new ResourceConflictException("Số điện thoại đã được sử dụng.");
        }
        user.updateProfile(fullName, phone);
        return toResponse(user);
    }

    @Transactional
    public AuthMessageResponse forgotPassword(ForgotPasswordRequest request) {
        User user = findByIdentityOrNull(request.identity());
        if (user != null && user.isActive() && !user.isLocked() && user.isEmailVerified()) {
            try { otpService.issue(user, OtpPurpose.RESET_PASSWORD, true); } catch (AuthException ignored) { }
        }
        return new AuthMessageResponse(GENERIC_FORGOT_MESSAGE, true, 0);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public ResetVerificationResponse verifyResetOtp(String identity, String code) {
        User user = findByIdentityOrNull(identity);
        if (user == null) throw invalidOtp();
        user = otpService.verify(user, requireOtp(code), OtpPurpose.RESET_PASSWORD);
        if (!user.isActive() || user.isLocked()) throw new AuthException(HttpStatus.FORBIDDEN, "Không thể đặt lại mật khẩu cho tài khoản này.");
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        resetSessions.consumeActiveByUserId(user.getId(), Instant.now());
        PasswordResetSession session = new PasswordResetSession();
        session.setUser(user); session.setTokenHash(OtpService.sha256(token)); session.setExpiresAt(Instant.now().plus(resetTokenTtl));
        resetSessions.save(session);
        return new ResetVerificationResponse("Mã OTP hợp lệ. Vui lòng đặt mật khẩu mới.", token);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthMessageResponse resetPassword(ResetPasswordRequest request) {
        PasswordResetSession session = resetSessions.findActiveForUpdate(OtpService.sha256(request.resetToken()))
                .orElseThrow(this::invalidResetToken);
        if (!session.getExpiresAt().isAfter(Instant.now())) throw invalidResetToken();
        User user = session.getUser();
        passwordPolicy.validate(request.newPassword(), request.confirmPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) throw new AuthException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại.");
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        session.setConsumedAt(Instant.now());
        return new AuthMessageResponse("Đặt lại mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới.", true, 0);
    }

    private AuthMessageResponse dispatch(OtpService.OtpDispatch result) {
        return result.emailSent() ? new AuthMessageResponse("Mã xác minh đã được gửi. Vui lòng kiểm tra Hộp thư đến và Spam.", true, 0)
                : new AuthMessageResponse("Chưa thể gửi email. Vui lòng thử lại sau.", false, result.retryAfterSeconds());
    }
    private AuthResponse tokensFor(User user) { return new AuthResponse(jwtService.generateAccessToken(user), jwtService.generateRefreshToken(user), "Bearer", toResponse(user)); }
    private UserResponse toResponse(User user) {
        Long branchId = user.getBranch() != null ? user.getBranch().getId() : null;
        String branchName = user.getBranch() != null ? user.getBranch().getName() : null;
        return new UserResponse(user.getId(), user.getUsername(), UserDisplayName.of(user), user.getEmail(), user.getPhone(), user.getAvatarUrl(), user.getRole().getName().name(), user.getCreatedAt(), branchId, branchName);
    }
    private User findByEmail(String email) { return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResourceNotFoundException("Account was not found")); }
    private User findByIdentity(String identity) { User user = findByIdentityOrNull(identity); if (user == null) throw new AuthException(HttpStatus.BAD_REQUEST, "Thông tin xác minh không hợp lệ."); return user; }
    private User findByIdentityOrNull(String identity) {
        if (identity == null || identity.isBlank()) return null;
        String value = identity.trim();
        return value.contains("@") ? users.findByEmailIgnoreCase(normalizeEmail(value)).orElse(null) : users.findByUsernameIgnoreCase(value).orElse(null);
    }
    private void validateRegistration(RegisterRequest request, String username, String email, String phone) {
        if (request == null || request.fullName() == null || request.fullName().isBlank() || email.isBlank() || phone == null) throw new AuthException(HttpStatus.BAD_REQUEST, "Vui lòng nhập đầy đủ các trường bắt buộc.");
        if (!username.matches("[A-Za-z0-9._-]{3,50}")) throw new AuthException(HttpStatus.BAD_REQUEST, "Tên đăng nhập không hợp lệ.");
        if (request.fullName().trim().length() > 120) throw new AuthException(HttpStatus.BAD_REQUEST, "Họ tên không được vượt quá 120 ký tự.");
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new AuthException(HttpStatus.BAD_REQUEST, "Email không hợp lệ.");
        if (!phone.matches("(03|05|07|08|09)\\d{8}")) throw new AuthException(HttpStatus.BAD_REQUEST, "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 03, 05, 07, 08 hoặc 09.");
        passwordPolicy.validate(request.password(), request.confirmPassword());
    }
    private String normalizeEmail(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
    private String normalizePhone(String phone) { if (phone == null) return null; String value = phone.trim().replaceAll("[\\s().-]", ""); return value.startsWith("+84") ? "0" + value.substring(3) : value; }
    private String requireOtp(String code) { if (code == null || !code.matches("\\d{6}")) throw invalidOtp(); return code; }
    private AuthException invalidOtp() { return new AuthException(HttpStatus.BAD_REQUEST, "Mã OTP không hợp lệ hoặc đã hết hạn."); }
    private AuthException invalidResetToken() { return new AuthException(HttpStatus.BAD_REQUEST, "Phiên đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."); }
}
