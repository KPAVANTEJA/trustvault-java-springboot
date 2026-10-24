package com.trustvault.user;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import com.trustvault.audit.AuditIntegrityService;
import com.trustvault.audit.AuditLog;
import com.trustvault.document.Document;
import com.trustvault.document.DocumentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "TRUSTVAULT_AUDIT_HMAC_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class UserManagementAndAuditTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditIntegrityService auditIntegrityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupChainHead() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TV_AUDIT_CHAIN_HEAD WHERE ID = 1",
                Integer.class
        );

        if (count == null || count == 0) {
            jdbcTemplate.update("""
                INSERT INTO TV_AUDIT_CHAIN_HEAD (
                    ID, LAST_EVENT_ID, LAST_HASH, CHECKPOINT_HMAC, UPDATED_AT, CHAIN_START_AFTER_ID, HISTORICAL_BASELINE_HMAC
                ) VALUES (
                    1, NULL, 'GENESIS', 'UNINITIALIZED', CURRENT_TIMESTAMP, NULL, NULL
                )
                """);
        }

        AppUser admin = userRepository.saveAndFlush(
                new AppUser("audit.admin@example.com", "Admin Tester", "Pass12345678!", UserRole.ADMIN));
        
        ensureRoleUser(
                "audit.owner@example.com", "Owner Tester", UserRole.OWNER);

        ensureRoleUser(
                "audit.viewer@example.com", "Viewer Tester", UserRole.VIEWER);
        
        Document doc = documentRepository.saveAndFlush(
                new Document("Audit Doc", "audit.txt", "storage-key", "text/plain", 100L, admin, "Uncategorized"));

        auditIntegrityService.append(new AuditLog(doc, admin, "UPLOAD", "SUCCESS"));
    }

    @Test
    @WithMockUser(username = "audit.admin@example.com", roles = "ADMIN")
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = "audit.owner@example.com", roles = "OWNER")
    void ownerCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "audit.viewer@example.com", roles = "VIEWER")
    void viewerCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "audit.admin@example.com", roles = "ADMIN")
    void adminCanCreateUser() throws Exception {
        String payload = """
            {
                "fullName": "Test Admin Created",
                "email": "created.user@example.com",
                "password": "StrongPassword123!",
                "role": "VIEWER"
            }
            """;

        mockMvc.perform(post("/api/admin/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("created.user@example.com")))
                .andExpect(jsonPath("$.role", is("VIEWER")))
                .andExpect(jsonPath("$.enabled", is(true)));
    }

    @Test
    @WithMockUser(username = "audit.admin@example.com", roles = "ADMIN")
    void createUserWithShortPasswordFails() throws Exception {
        String payload = """
            {
                "fullName": "Short Pass User",
                "email": "short.pass@example.com",
                "password": "short",
                "role": "VIEWER"
            }
            """;

        mockMvc.perform(post("/api/admin/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "audit.admin@example.com", roles = "ADMIN")
    void adminCanToggleUserEnabled() throws Exception {
        AppUser user = userRepository.save(new AppUser(
                "toggle.user@example.com", "Toggle User", "HashPass12345!", UserRole.VIEWER));

        mockMvc.perform(patch("/api/admin/users/" + user.getId() + "/enabled")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));

        mockMvc.perform(patch("/api/admin/users/" + user.getId() + "/enabled")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)));
    }

    @Test
    @WithMockUser(username = "audit.admin@example.com", roles = "ADMIN")
    void adminCanChangeUserRole() throws Exception {
        AppUser user = userRepository.save(new AppUser(
                "rolechange.user@example.com", "Role User", "HashPass12345!", UserRole.VIEWER));

        mockMvc.perform(patch("/api/admin/users/" + user.getId() + "/role")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"OWNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("OWNER")));
    }

    @Test
    @WithMockUser(username = "audit.admin@example.com", roles = "ADMIN")
    void adminCanVerifyAuditIntegrity() throws Exception {
        mockMvc.perform(get("/api/admin/audit/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OK")));
    }

    @Test
    @WithMockUser(username = "audit.owner@example.com", roles = "OWNER")
    void ownerCannotVerifyAuditIntegrity() throws Exception {
        mockMvc.perform(get("/api/admin/audit/integrity"))
                .andExpect(status().isForbidden());
    }
    
    private AppUser ensureRoleUser(
            String email, String fullName, UserRole role) {

        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> new AppUser(
                        email, fullName, "Pass12345678!", role));

        user.setRole(role);
        user.setEnabled(true);

        return userRepository.saveAndFlush(user);
    }
    

@Test
@WithMockUser(username = "audit.admin@example.com", roles = "ADMIN")
void adminCanReadPermissionAuditMetadata() throws Exception {
    AppUser admin = userRepository
            .findByEmailIgnoreCase("audit.admin@example.com")
            .orElseThrow();

    AppUser recipient = userRepository.saveAndFlush(
            new AppUser(
                    "permission.recipient.api@example.com",
                    "Permission Recipient",
                    "TestPassword123!",
                    UserRole.VIEWER));

    String suffix = UUID.randomUUID().toString();

    Document document = documentRepository.saveAndFlush(
            new Document(
                    "Permission Audit Test " + suffix,
                    "permission-audit.txt",
                    suffix,
                    "text/plain",
                    1L,
                    admin,
                    "Tests"));

    AuditLog event = new AuditLog(
            document,
            admin,
            "PERMISSION_GRANT",
            "SUCCESS");

    event.setTargetUserId(recipient.getId());
    event.setTargetUserEmail(recipient.getEmail());
    event.setPreviousPermissionType(null);
    event.setNewPermissionType("VIEW");

    auditIntegrityService.append(event);

    mockMvc.perform(get("/api/admin/audit?size=100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath(
                    "$.content[?(@.targetUserEmail == 'permission.recipient.api@example.com')].targetUserId",
                    hasItem(recipient.getId().intValue())))
            .andExpect(jsonPath(
                    "$.content[?(@.targetUserEmail == 'permission.recipient.api@example.com')].newPermissionType",
                    hasItem("VIEW")));
}


@Test
@WithMockUser(username = "audit.viewer@example.com", roles = "VIEWER")
void viewerCannotReadAuditHistory() throws Exception {
    AppUser viewer = userRepository
            .findByEmailIgnoreCase("audit.viewer@example.com")
            .orElseGet(() -> new AppUser(
                    "audit.viewer@example.com",
                    "Audit Viewer",
                    "TestPassword123!",
                    UserRole.VIEWER));

    viewer.setRole(UserRole.VIEWER);
    viewer.setEnabled(true);
    userRepository.saveAndFlush(viewer);

    mockMvc.perform(get("/api/admin/audit"))
            .andExpect(status().isForbidden());
}


}
