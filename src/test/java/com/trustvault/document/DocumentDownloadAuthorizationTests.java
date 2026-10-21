
package com.trustvault.document;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import com.trustvault.audit.AuditLogService;
import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentDownloadAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserRepository userRepository;

    @MockitoBean
    private DocumentRepository documentRepository;

    @MockitoBean
    private DocumentPermissionRepository permissionRepository;

    @MockitoBean
    private AuditLogService auditLogService;

    @Test
    @WithMockUser(username = "viewer@example.com", roles = "VIEWER")
    void viewerCannotDownloadAnotherUsersPrivateDocument() throws Exception {
        AppUser viewer = mock(AppUser.class);
        AppUser owner = mock(AppUser.class);
        Document document = mock(Document.class);

        when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
            .thenReturn(Optional.of(viewer));

        when(viewer.getId()).thenReturn(2L);
        when(owner.getId()).thenReturn(1L);

        when(documentRepository.findById(100L))
            .thenReturn(Optional.of(document));

        when(document.isDeleted()).thenReturn(false);
        when(document.getOwner()).thenReturn(owner);

        when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documents/100/download"))
            .andExpect(status().isNotFound());

        verify(auditLogService)
            .recordDownloadFailure(document, viewer, "DENIED");
    }
}
