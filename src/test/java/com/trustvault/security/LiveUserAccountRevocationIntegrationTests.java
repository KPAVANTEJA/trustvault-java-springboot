package com.trustvault.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "TRUSTVAULT_AUDIT_HMAC_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class LiveUserAccountRevocationIntegrationTests {

    private static final String PASSWORD = "StrongTestPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupAuditChainHead() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TV_AUDIT_CHAIN_HEAD WHERE ID = 1",
                Integer.class);

        if (count == null || count == 0) {
            jdbcTemplate.update("""
                INSERT INTO TV_AUDIT_CHAIN_HEAD (
                    ID, LAST_EVENT_ID, LAST_HASH, CHECKPOINT_HMAC,
                    UPDATED_AT, CHAIN_START_AFTER_ID, HISTORICAL_BASELINE_HMAC
                ) VALUES (
                    1, NULL, 'GENESIS', 'UNINITIALIZED',
                    CURRENT_TIMESTAMP, NULL, NULL
                )
                """);
        }
    }

    @Test
    void disablingUserInvalidatesTheirExistingLoginSession()
            throws Exception {

        AppUser admin = createUser(
                "revocation.admin@example.com", UserRole.ADMIN);
        AppUser viewer = createUser(
                "revocation.viewer@example.com", UserRole.VIEWER);

        MockHttpSession viewerSession = login(viewer.getEmail());

        mockMvc.perform(get("/api/documents").session(viewerSession))
                .andExpect(status().isOk());

        MockHttpSession adminSession = login(admin.getEmail());

        mockMvc.perform(patch(
                    "/api/admin/users/" + viewer.getId() + "/enabled")
                .session(adminSession)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        // The same viewer session must now be rejected.
        mockMvc.perform(get("/api/documents").session(viewerSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingUserRoleInvalidatesTheirExistingLoginSession()
            throws Exception {

        AppUser admin = createUser(
                "role-change.admin@example.com", UserRole.ADMIN);
        AppUser viewer = createUser(
                "role-change.viewer@example.com", UserRole.VIEWER);

        MockHttpSession viewerSession = login(viewer.getEmail());

        mockMvc.perform(get("/api/documents").session(viewerSession))
                .andExpect(status().isOk());

        MockHttpSession adminSession = login(admin.getEmail());

        mockMvc.perform(patch(
                    "/api/admin/users/" + viewer.getId() + "/role")
                .session(adminSession)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"OWNER\"}"))
                .andExpect(status().isOk());

        // The old ROLE_VIEWER session must now be rejected.
        mockMvc.perform(get("/api/documents").session(viewerSession))
                .andExpect(status().isUnauthorized());
    }

    private AppUser createUser(String email, UserRole role) {
        AppUser user = new AppUser(
                email,
                "Revocation Test User",
                passwordEncoder.encode(PASSWORD),
                role);

        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                    .with(csrf())
                    .param("username", email)
                    .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) result.getRequest().getSession(false);

        assertNotNull(session, "Successful login should create a session");
        return session;
    }
}