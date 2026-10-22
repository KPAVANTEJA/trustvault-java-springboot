
package com.trustvault.document;

import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

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
    
    @MockitoBean
    private DocumentStorageService storageService;

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
    

@Test
@WithMockUser(username = "viewer@example.com", roles = "VIEWER")
void viewerWithViewPermissionCanDownloadDocument() throws Exception {
    AppUser viewer = mock(AppUser.class);
    AppUser owner = mock(AppUser.class);
    Document document = mock(Document.class);
    DocumentPermission permission = mock(DocumentPermission.class);

    Path tempFile = Files.createTempFile("trustvault-test-", ".txt");

    try {
        Files.writeString(tempFile, "test document content");

        when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
            .thenReturn(Optional.of(viewer));
        when(viewer.getId()).thenReturn(2L);
        when(owner.getId()).thenReturn(1L);

        when(documentRepository.findById(100L))
            .thenReturn(Optional.of(document));
        when(document.isDeleted()).thenReturn(false);
        when(document.getOwner()).thenReturn(owner);
        when(document.getStorageKey()).thenReturn("test-storage-key");
        when(document.getContentType()).thenReturn("text/plain");

        when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
            .thenReturn(Optional.of(permission));
        when(permission.getPermissionType()).thenReturn(PermissionType.VIEW);

        when(storageService.resolve("test-storage-key"))
            .thenReturn(tempFile);

        mockMvc.perform(get("/api/documents/100/download"))
            .andExpect(status().isOk());

        verify(auditLogService).recordDownload(document, viewer);
    } finally {
        Files.deleteIfExists(tempFile);
    }
}


@Test
@WithMockUser(
    username = "viewer@example.com",
    roles = "VIEWER"
)
void viewerCannotEditAnotherUsersDocument() throws Exception {

    AppUser viewer = mock(AppUser.class);
    when(viewer.getId()).thenReturn(2L);

    AppUser owner = mock(AppUser.class);
    when(owner.getId()).thenReturn(1L);

    Document document = mock(Document.class);
    when(document.getId()).thenReturn(100L);
    when(document.getOwner()).thenReturn(owner);
    when(document.isDeleted()).thenReturn(false);

    when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
        .thenReturn(Optional.of(viewer));

    when(documentRepository.findById(100L))
        .thenReturn(Optional.of(document));

    when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
        .thenReturn(Optional.empty());

    mockMvc.perform(put("/api/documents/100")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "title": "Unauthorized change"
                }
                """))
        .andExpect(status().isNotFound());

    verify(auditLogService).recordDocumentUpdateFailure(
        document, viewer, "DENIED"
    );

    verify(documentRepository, never())
        .saveAndFlush(any(Document.class));
}

}
