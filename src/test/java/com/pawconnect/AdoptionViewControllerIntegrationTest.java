package com.pawconnect;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.seed.users.enabled=false")
@AutoConfigureMockMvc
class AdoptionViewControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicAdoptionPagesRenderTheSharedTemplate() throws Exception {
        mockMvc.perform(get("/adoptions"))
                .andExpect(status().isOk())
                .andExpect(view().name("community/adoptions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"adoption-list-view\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"headerSearchBtn\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"cartBtn\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("src=\"/js/header.js?v=")));

        mockMvc.perform(get("/adoptions/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("community/adoptions"));

        mockMvc.perform(get("/adoptions/my-applications"))
                .andExpect(status().isOk())
                .andExpect(view().name("community/adoptions"));

        mockMvc.perform(get("/adoptions/manage"))
                .andExpect(status().isOk())
                .andExpect(view().name("community/adoptions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"dogBranchId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"delete-dog-image\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"delete-post-image\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"open-adoption-chat\"")));
    }

    @Test
    void adminUsersPageRendersTheManagementTemplate() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"admin-users-table-body\"")));
    }

    @Test
    void adminOrdersPageRendersTheManagementTemplate() throws Exception {
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/orders"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"managed-orders\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"order-action-dialog\"")));
    }

    @Test
    void profilePageRendersTheSharedHeaderAndProfileForm() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile/index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"profile-form\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"avatar-file\"")));
    }
}
