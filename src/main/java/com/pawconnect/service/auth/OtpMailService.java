package com.pawconnect.service.auth;

import com.pawconnect.entity.OtpPurpose;
import jakarta.mail.internet.MimeMessage;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OtpMailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OtpMailService.class);
    private final JavaMailSender mailSender;
    private final String username;
    private final String password;
    private final String from;
    private final String fromName;

    public OtpMailService(JavaMailSender mailSender,
                          @Value("${spring.mail.username:}") String username,
                          @Value("${spring.mail.password:}") String password,
                          @Value("${app.mail.from:}") String from,
                          @Value("${app.mail.from-name:PawConnect}") String fromName) {
        this.mailSender = mailSender;
        this.username = username;
        this.password = password;
        this.from = from;
        this.fromName = fromName;
    }

    public boolean send(String recipient, String fullName, OtpPurpose purpose, String code) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password) || !StringUtils.hasText(from)) {
            LOGGER.warn("OTP delivery skipped because SMTP is not configured.");
            return false;
        }
        String action = purpose == OtpPurpose.VERIFY_EMAIL ? "xác minh email" : "đặt lại mật khẩu";
        String subject = "[PawConnect] Mã OTP " + action;
        String body = """
                PawConnect

                Xin chào %s,

                Mã OTP để %s của bạn là: %s

                Mã có hiệu lực trong 5 phút và chỉ dùng một lần.
                Không chia sẻ mã này với bất kỳ ai. Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email.
                """.formatted(fullName, action, code);
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(from, fromName);
                helper.setTo(recipient);
                helper.setSubject(subject);
                helper.setText(body, false);
                mailSender.send(message);
                return true;
            } catch (Exception exception) {
                if (attempt == 1 && isTimeout(exception)) continue;
                LOGGER.warn("OTP delivery failed: {}", exception.getClass().getSimpleName());
                return false;
            }
        }
        return false;
    }

    private boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) return true;
        }
        return false;
    }
}
