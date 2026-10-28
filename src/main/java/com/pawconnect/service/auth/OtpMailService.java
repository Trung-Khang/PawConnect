package com.pawconnect.service.auth;

import com.pawconnect.entity.OtpPurpose;
import jakarta.mail.internet.MimeMessage;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private final String resendApiKey;
    private final String resendFrom;
    private final HttpClient httpClient;

    public record DeliveryResult(boolean sent, boolean deliveredToInbox, String demoCode) {}

    public OtpMailService(JavaMailSender mailSender,
                          @Value("${spring.mail.username:}") String username,
                          @Value("${spring.mail.password:}") String password,
                          @Value("${app.mail.from:}") String from,
                          @Value("${app.mail.from-name:PawConnect}") String fromName,
                          @Value("${app.resend.api-key:${RESEND_API_KEY:}}") String resendApiKey,
                          @Value("${app.resend.from:${RESEND_FROM:PawConnect <onboarding@resend.dev>}}") String resendFrom) {
        this.mailSender = mailSender;
        this.username = username;
        this.password = password;
        this.from = from;
        this.fromName = fromName;
        this.resendApiKey = resendApiKey;
        this.resendFrom = resendFrom;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean send(String recipient, String fullName, OtpPurpose purpose, String code) {
        return sendWithResult(recipient, fullName, purpose, code).sent();
    }

    public DeliveryResult sendWithResult(String recipient, String fullName, OtpPurpose purpose, String code) {
        String action = purpose == OtpPurpose.VERIFY_EMAIL ? "xác minh email" : "đặt lại mật khẩu";
        String subject = "[PawConnect] Mã OTP " + action;
        String body = """
                PawConnect

                Xin chào %s,

                Mã OTP để %s của bạn là: %s

                Mã có hiệu lực trong 5 phút và chỉ dùng một lần.
                Không chia sẻ mã này với bất kỳ ai. Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email.
                """.formatted(fullName, action, code);

        // 1. Gửi qua Resend REST API (Cổng HTTPS 443 - không bao giờ bị Cloud/Railway chặn)
        if (StringUtils.hasText(resendApiKey)) {
            try {
                String payload = String.format(
                        "{\"from\":\"%s\",\"to\":[\"%s\"],\"subject\":\"%s\",\"text\":\"%s\"}",
                        escapeJson(resendFrom),
                        escapeJson(recipient),
                        escapeJson(subject),
                        escapeJson(body)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.resend.com/emails"))
                        .header("Authorization", "Bearer " + resendApiKey.trim())
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    LOGGER.info("OTP email successfully sent via Resend to {}", recipient);
                    return new DeliveryResult(true, true, null);
                }

                LOGGER.warn("Resend API response status {}: {}", response.statusCode(), response.body());

                // Trường hợp tài khoản Resend chưa verify domain riêng chỉ gửi được tới email chủ tài khoản
                if (response.statusCode() == 403 && response.body().contains("testing emails")) {
                    LOGGER.warn("[RESEND TEST MODE] Recipient {} is outside Resend verified sandbox. OTP: {}", recipient, code);
                    return new DeliveryResult(true, false, code);
                }
            } catch (Exception ex) {
                LOGGER.error("Failed to send email via Resend API: {}", ex.getMessage());
            }
        }

        // 2. Fallback sang Spring Mail SMTP nếu có cấu hình
        if (StringUtils.hasText(username) && StringUtils.hasText(password) && StringUtils.hasText(from)) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                    helper.setFrom(from, fromName);
                    helper.setTo(recipient);
                    helper.setSubject(subject);
                    helper.setText(body, false);
                    mailSender.send(message);
                    LOGGER.info("OTP email successfully sent via SMTP to {}", recipient);
                    return new DeliveryResult(true, true, null);
                } catch (Exception exception) {
                    if (attempt == 1 && isTimeout(exception)) continue;
                    LOGGER.warn("OTP delivery via SMTP failed: {}", exception.getMessage());
                }
            }
        }

        if (!StringUtils.hasText(resendApiKey) && (!StringUtils.hasText(username) || !StringUtils.hasText(password) || !StringUtils.hasText(from))) {
            LOGGER.warn("OTP delivery skipped because neither Resend nor SMTP is configured. OTP code for {}: {}", recipient, code);
        }

        return new DeliveryResult(false, false, null);
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\r", "\\r")
                   .replace("\n", "\\n")
                   .replace("\t", "\\t");
    }

    private boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) return true;
        }
        return false;
    }
}
