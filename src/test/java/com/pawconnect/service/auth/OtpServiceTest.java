package com.pawconnect.service.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.pawconnect.entity.AuthOtp;
import com.pawconnect.entity.OtpPurpose;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.exception.AuthException;
import com.pawconnect.repository.AuthOtpRepository;
import com.pawconnect.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OtpServiceTest {
    private final UserRepository users = Mockito.mock(UserRepository.class);
    private final AuthOtpRepository otps = Mockito.mock(AuthOtpRepository.class);
    private final OtpMailService mail = Mockito.mock(OtpMailService.class);
    private final User user = new User("tester", "Test User", "test@example.com", "encoded", "0912345678", new Role(RoleName.CUSTOMER));
    private OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(users, otps, mail, Duration.ofMinutes(5), Duration.ofSeconds(60));
        when(users.findLockedById(isNull())).thenReturn(Optional.of(user));
    }

    @Test
    void issuesOnlyHashedSixDigitOtp() {
        when(otps.findActiveForUpdate(null, OtpPurpose.VERIFY_EMAIL)).thenReturn(List.of());
        when(mail.send(any(), any(), any(), any())).thenReturn(true);

        service.issue(user, OtpPurpose.VERIFY_EMAIL, false);

        ArgumentCaptor<AuthOtp> captured = ArgumentCaptor.forClass(AuthOtp.class);
        Mockito.verify(otps).save(captured.capture());
        assertNotNull(captured.getValue().getCodeHash());
        assertEquals(64, captured.getValue().getCodeHash().length());
        assertEquals(OtpPurpose.VERIFY_EMAIL, captured.getValue().getPurpose());
    }

    @Test
    void enforcesCooldownAndInvalidatesOldOtpWhenNewCodeIsIssued() {
        AuthOtp fresh = otp("123456", Instant.now().plusSeconds(300));
        when(otps.findActiveForUpdate(null, OtpPurpose.VERIFY_EMAIL)).thenReturn(List.of(fresh));
        assertThrows(AuthException.class, () -> service.issue(user, OtpPurpose.VERIFY_EMAIL, true));

        fresh.setLastSentAt(Instant.now().minusSeconds(61));
        when(mail.send(any(), any(), any(), any())).thenReturn(true);
        assertDoesNotThrow(() -> service.issue(user, OtpPurpose.VERIFY_EMAIL, true));
        assertNotNull(fresh.getInvalidatedAt());
    }

    @Test
    void consumesCorrectCodeAndInvalidatesAfterFiveWrongAttempts() {
        AuthOtp correct = otp("123456", Instant.now().plusSeconds(300));
        when(otps.findFirstActiveForUpdate(null, OtpPurpose.VERIFY_EMAIL)).thenReturn(Optional.of(correct));
        assertDoesNotThrow(() -> service.verify(user, "123456", OtpPurpose.VERIFY_EMAIL));
        assertNotNull(correct.getConsumedAt());

        AuthOtp wrong = otp("123456", Instant.now().plusSeconds(300));
        when(otps.findFirstActiveForUpdate(null, OtpPurpose.RESET_PASSWORD)).thenReturn(Optional.of(wrong));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(AuthException.class, () -> service.verify(user, "000000", OtpPurpose.RESET_PASSWORD));
        }
        assertEquals(5, wrong.getAttemptCount());
        assertNotNull(wrong.getInvalidatedAt());
    }

    @Test
    void rejectsExpiredOtp() {
        AuthOtp expired = otp("123456", Instant.now().minusSeconds(1));
        when(otps.findFirstActiveForUpdate(null, OtpPurpose.VERIFY_EMAIL)).thenReturn(Optional.of(expired));
        assertThrows(AuthException.class, () -> service.verify(user, "123456", OtpPurpose.VERIFY_EMAIL));
        assertNotNull(expired.getInvalidatedAt());
    }

    private AuthOtp otp(String code, Instant expiresAt) {
        AuthOtp value = new AuthOtp();
        value.setUser(user);
        value.setEmail(user.getEmail());
        value.setPurpose(OtpPurpose.VERIFY_EMAIL);
        value.setCodeHash(OtpService.sha256(code));
        value.setExpiresAt(expiresAt);
        value.setLastSentAt(Instant.now());
        return value;
    }
}
