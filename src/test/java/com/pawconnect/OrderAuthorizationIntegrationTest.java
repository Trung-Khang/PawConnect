package com.pawconnect;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrderAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void anonymousCannotManageOrders() throws Exception {
        mockMvc.perform(get("/api/orders/manage"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/orders/{id}/status", 1).param("status", "SHIPPED"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotManageOrders() throws Exception {
        mockMvc.perform(get("/api/orders/manage").with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/orders/{id}/status", 1).param("status", "SHIPPED")
                        .with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }
}
