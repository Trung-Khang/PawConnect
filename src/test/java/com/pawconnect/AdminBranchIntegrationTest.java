package com.pawconnect;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Order;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminBranchIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BranchRepository branches;
    @Autowired private OrderRepository orders;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateUpdateAndDeleteUnusedBranch() throws Exception {
        String code = "BR_TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String created = mockMvc.perform(post("/api/admin/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Chi nhánh thử\",\"address\":\"Địa chỉ A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).path("id").asLong();

        mockMvc.perform(patch("/api/admin/branches/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chi nhánh mới\",\"address\":\"Địa chỉ B\",\"latitude\":10.75,\"longitude\":106.66}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value("Chi nhánh mới"));

        mockMvc.perform(delete("/api/admin/branches/{id}", id)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(branches.existsById(id));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cannotDeleteBranchWithOrdersOrCreateInvalidBranch() throws Exception {
        Branch branch = branches.save(Branch.builder().code("BR_ORDER_" + UUID.randomUUID().toString().substring(0, 8))
                .name("Đã có đơn").address("Địa chỉ").build());
        orders.save(Order.builder().branch(branch).userId(1L).status("PENDING").totalAmount(BigDecimal.ZERO).build());

        mockMvc.perform(delete("/api/admin/branches/{id}", branch.getId()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/branches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad code\",\"name\":\"A\",\"address\":\"B\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/branches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BR_INVALID_01\",\"name\":\"A\",\"address\":\"B\",\"latitude\":91}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "BRANCH_MANAGER")
    void managerCannotManageBranches() throws Exception {
        mockMvc.perform(post("/api/admin/branches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BR_NEW_01\",\"name\":\"A\",\"address\":\"B\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/branches/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"address\":\"B\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/branches/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotManageBranches() throws Exception {
        mockMvc.perform(post("/api/admin/branches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BR_NEW_02\",\"name\":\"A\",\"address\":\"B\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/branches/1")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotManageBranches() throws Exception {
        mockMvc.perform(delete("/api/admin/branches/1")).andExpect(status().isUnauthorized());
    }
}
