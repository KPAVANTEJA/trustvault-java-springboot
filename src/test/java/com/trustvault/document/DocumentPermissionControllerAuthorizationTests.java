package com.trustvault.document;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "TRUSTVAULT_AUDIT_HMAC_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class DocumentPermissionControllerAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentPermissionService permissionService;

    @Test
    @WithMockUser(username = "viewer@example.com", roles = "VIEWER")
    void viewerCannotGrantPermission() throws Exception {
        mockMvc.perform(post("/api/documents/100/permissions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "recipient@example.com",
                        "permissionType": "VIEW"
                    }
                    """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(permissionService);
    }

    @Test
    @WithMockUser(username = "viewer@example.com", roles = "VIEWER")
    void viewerCannotRevokePermission() throws Exception {
        mockMvc.perform(delete("/api/documents/100/permissions/3")
                .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(permissionService);
    }

    @Test
    @WithMockUser(username = "viewer@example.com", roles = "VIEWER")
    void viewerCannotListDocumentPermissions() throws Exception {
        mockMvc.perform(get("/api/documents/100/permissions"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(permissionService);
    }
}