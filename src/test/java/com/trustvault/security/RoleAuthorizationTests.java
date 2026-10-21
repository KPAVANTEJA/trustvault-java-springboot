package com.trustvault.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/access-check"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/access-check"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerCanAccessOwnerEndpoint() throws Exception {
        mockMvc.perform(get("/api/owner/access-check"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/access-check"))
            .andExpect(status().isForbidden());
    }
    

    @Test
    void unauthenticatedUserCannotAccessDocuments() throws Exception {
        mockMvc.perform(get("/api/documents"))
            .andExpect(status().is3xxRedirection());
    }

}