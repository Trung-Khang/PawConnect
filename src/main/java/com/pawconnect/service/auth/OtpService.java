package com.pawconnect.service.auth;

import com.pawconnect.entity.AuthOtp;
import com.pawconnect.entity.OtpPurpose;
import com.pawconnect.entity.User;
import com.pawconnect.exception.AuthException;
import com.pawconnect.repository.AuthOtpRepository;
import com.pawconnect.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private final UserRepository userRepository;
    private final AuthOtpRepository otpRepository;
    private final OtpMailService mailService;
    private final Duration ttl;
    private final Duration resendCooldown;

    public OtpService(UserRepository userRepository, AuthOtpRepository otpRepository, OtpMailService mailService,
                      @Value("${app.auth.otp-ttl:PT5M}") Duration ttl,
                      @Value("${app.auth.otp-resend-cooldown:PT60S}") Duration resendCooldown) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.mailService = mailService;
        this.ttl = ttl;
        this.resendCooldown = resendCooldown;
    }

    @Transactional(noRollbackFor = AuthException.class)
    public OtpDispatch issue(User inputUser, OtpPurpose purpose, boolean enforceCooldown) {
        User user = userRepository.findLockedById(inputUser.getId()).orElseThrow();
        Instant now = Instant.now();
        List<AuthOtp> active = otpRepository.findActiveForUpdate(user.getId(), purpose);
        for (AuthOtp otp : active) {
            long seconds = Math.max(0, resendCooldown.minus(Duration.between(otp.getLastSentAt(), now)).toSeconds());
            if (enforceCooldown && seconds > 0) {
                throw new AuthException(HttpStatus.TOO_MANY_REQUESTS,
                        "Vui lòng chờ " + seconds + " giây trước khi gửi lại mã OTP.");
            }
        }
        active.forEach(otp -> otp.setInvalidatedAt(now));

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        AuthOtp otp = new AuthOtp();
        otp.setUser(user);
        otp.setEmail(user.getEmail());
        otp.setPurpose(purpose);
        otp.setCodeHash(sha256(code));
        otp.setExpiresAt(now.plus(ttl));
        otp.setLastSentAt(now);
        otpRepository.save(otp);
        boolean sent = mailService.send(user.getEmail(), user.getFullName(), purpose, code);
        if (!sent) otp.setLastSentAt(now.minus(resendCooldown));
        return new OtpDispatch(sent, 0);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public User verify(User inputUser, String code, OtpPurpose purpose) {
        User user = userRepository.findLockedById(inputUser.getId()).orElseThrow();
        AuthOtp otp = otpRepository.findFirstActiveForUpdate(user.getId(), purpose)
                .orElseThrow(() -> invalidOtp());
        Instant now = Instant.now();
        if (!otp.getExpiresAt().isAfter(now) || otp.getAttemptCount() >= MAX_ATTEMPTS) {
            otp.setInvalidatedAt(now);
            throw invalidOtp();
        }
        if (!MessageDigest.isEqual(otp.getCodeHash().getBytes(StandardCharsets.UTF_8), sha256(code).getBytes(StandardCharsets.UTF_8))) {
            int attempts = otp.getAttemptCount() + 1;
            otp.setAttemptCount(attempts);
            if (attempts >= MAX_ATTEMPTS) otp.setInvalidatedAt(now);
            throw invalidOtp();
        }
        otp.setConsumedAt(now);
        return user;
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private AuthException invalidOtp() { return new AuthException(HttpStatus.BAD_REQUEST, "Mã OTP không hợp lệ hoặc đã hết hạn."); }
    public record OtpDispatch(boolean emailSent, long retryAfterSeconds) { }
}
