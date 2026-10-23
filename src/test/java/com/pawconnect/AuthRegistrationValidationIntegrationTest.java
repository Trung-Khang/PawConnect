package com.pawconnect;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawconnect.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRegistrationValidationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;

    @Test
    void normalizesVietnamesePhoneAndEmailThenRejectsDuplicateIdentityFields() throws Exception {
        register("unique.user", "  UNIQUE@Example.COM  ", "+84 (912) 223-456").andExpect(status().isCreated());
        var saved = users.findByEmailIgnoreCase("unique@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("0912223456", saved.getPhone());
        org.junit.jupiter.api.Assertions.assertEquals("unique@example.com", saved.getEmail());

        register("unique.user", "different@example.com", "0912345679").andExpect(status().isConflict());
        // An unverified account can request a replacement verification OTP, but no second user is created.
        register("another.user", "unique@example.com", "0912345679").andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertTrue(users.findByUsernameIgnoreCase("another.user").isEmpty());
        register("phone.user", "phone@example.com", "0912223456").andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidUsernameEmailPhoneAndPasswordPolicy() throws Exception {
        register("bad name", "valid@example.com", "0912345680").andExpect(status().isBadRequest());
        register("valid.name", "not-an-email", "0912345681").andExpect(status().isBadRequest());
        register("valid.name", "valid2@example.com", "0212345681").andExpect(status().isBadRequest());
        request("valid.name", "valid3@example.com", "0912345682", "password123!", "password123!").andExpect(status().isBadRequest());
        request("valid.name", "valid4@example.com", "0912345683", "Password123!", "Password124!").andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions register(String username, String email, String phone) throws Exception {
        return request(username, email, phone, "Password123!", "Password123!");
    }

    private org.springframework.test.web.servlet.ResultActions request(String username, String email, String phone,
                                                                        String password, String confirmPassword) throws Exception {
        String body = "{\"username\":\"%s\",\"fullName\":\"Test Customer\",\"email\":\"%s\",\"phone\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}"
                .formatted(username, email, phone, password, confirmPassword);
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
