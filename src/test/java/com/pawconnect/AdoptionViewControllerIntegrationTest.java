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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"adoption-list-view\"")));

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
}
