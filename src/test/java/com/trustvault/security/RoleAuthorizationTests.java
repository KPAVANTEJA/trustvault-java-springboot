
package com.trustvault.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @BeforeEach
    void seedAuthenticatedUsers() {
        seedUser("admin@example.com", UserRole.ADMIN);
        seedUser("owner@example.com", UserRole.OWNER);
        seedUser("viewer@example.com", UserRole.VIEWER);
    }

    private void seedUser(String email, UserRole role) {
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> new AppUser(
                        email, "Test User", "test-password-hash", role));

        user.setRole(role);
        user.setEnabled(true);
        userRepository.saveAndFlush(user);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCanAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/access-check"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "viewer@example.com", roles = "VIEWER")
    void viewerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/access-check"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
    void ownerCanAccessOwnerEndpoint() throws Exception {
        mockMvc.perform(get("/api/owner/access-check"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "owner@example.com", roles = "OWNER")
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
