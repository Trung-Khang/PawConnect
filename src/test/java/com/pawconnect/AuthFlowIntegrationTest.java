package com.pawconnect;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.repository.AuthOtpRepository;
import com.pawconnect.service.auth.OtpService;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthOtpRepository authOtpRepository;

    @Test
    void customerCanRegisterLoginRefreshAndReadOwnProfile() throws Exception {
        String registration = """
                {"username":"customer.a","fullName":"Nguyen Van A","email":"customer@example.com","password":"Password123!","confirmPassword":"Password123!","phone":"+84901234567"}
                """;
        MvcResult registrationResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailSent").value(false))
                .andReturn();

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"customer.a\",\"password\":\"Password123!\"}"))
                .andExpect(status().isForbidden());

        var otp = authOtpRepository.findAll().stream()
                .filter(value -> value.getEmail().equals("customer@example.com"))
                .findFirst().orElseThrow();
        otp.setCodeHash(OtpService.sha256("123456"));
        authOtpRepository.saveAndFlush(otp);
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity\":\"customer.a\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"customer.a\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("customer.a"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn();
        JsonNode registered = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = registered.path("accessToken").asText();
        String refreshToken = registered.path("refreshToken").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@example.com"));

        mockMvc.perform(post("/api/auth/refresh-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    void verifiedUserCanResetPasswordOnlyOnce() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reset.user\",\"fullName\":\"Reset User\",\"email\":\"reset@example.com\",\"password\":\"Password123!\",\"confirmPassword\":\"Password123!\",\"phone\":\"0912345678\"}"))
                .andExpect(status().isCreated());
        var verification = authOtpRepository.findAll().stream()
                .filter(value -> value.getEmail().equals("reset@example.com"))
                .findFirst().orElseThrow();
        verification.setCodeHash(OtpService.sha256("111111"));
        authOtpRepository.saveAndFlush(verification);
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity\":\"reset.user\",\"code\":\"111111\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity\":\"reset.user\"}"))
                .andExpect(status().isOk());
        var resetOtp = authOtpRepository.findAll().stream()
                .filter(value -> value.getEmail().equals("reset@example.com") && value.getPurpose().name().equals("RESET_PASSWORD"))
                .findFirst().orElseThrow();
        resetOtp.setCodeHash(OtpService.sha256("222222"));
        authOtpRepository.saveAndFlush(resetOtp);

        MvcResult verificationResult = mockMvc.perform(post("/api/v1/auth/verify-reset-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity\":\"reset.user\",\"code\":\"222222\"}"))
                .andExpect(status().isOk()).andReturn();
        String resetToken = objectMapper.readTree(verificationResult.getResponse().getContentAsString()).path("resetToken").asText();
        String resetRequest = "{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"NewPassword123!\",\"confirmPassword\":\"NewPassword123!\"}";
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetRequest))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetRequest))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"reset.user\",\"password\":\"NewPassword123!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCallerCannotReadProtectedProfile() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRefreshTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifiedUserCanUpdateOwnProfileWithNormalizedVietnamesePhone() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"profile.user\",\"fullName\":\"Profile User\",\"email\":\"profile@example.com\",\"password\":\"Password123!\",\"confirmPassword\":\"Password123!\",\"phone\":\"0912345690\"}"))
                .andExpect(status().isCreated());
        var otp = authOtpRepository.findAll().stream()
                .filter(value -> value.getEmail().equals("profile@example.com"))
                .findFirst().orElseThrow();
        otp.setCodeHash(OtpService.sha256("333333"));
        authOtpRepository.saveAndFlush(otp);
        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity\":\"profile.user\",\"code\":\"333333\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"profile.user\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).path("accessToken").asText();

        mockMvc.perform(put("/api/auth/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nguyen Van Profile\",\"phone\":\"+84 (935) 345-678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyen Van Profile"))
                .andExpect(jsonPath("$.phone").value("0935345678"));

        mockMvc.perform(put("/api/auth/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nguyen Van Profile\",\"phone\":\"0212345678\"}"))
                .andExpect(status().isBadRequest());
    }
}
