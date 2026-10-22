
package com.trustvault.document;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.trustvault.audit.AuditLogService;
import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

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


@Test
@WithMockUser(
    username = "owner2@example.com",
    roles = "OWNER"
)
void ownerCannotDeleteAnotherUsersDocument() throws Exception {

    AppUser requester = mock(AppUser.class);
    when(requester.getId()).thenReturn(2L);

    when(userRepository.findByEmailIgnoreCase("owner2@example.com"))
        .thenReturn(Optional.of(requester));


    AppUser actualOwner = mock(AppUser.class);
    when(actualOwner.getId()).thenReturn(1L);

    Document document = mock(Document.class);
    when(document.getOwner()).thenReturn(actualOwner);
    when(document.isDeleted()).thenReturn(false);

    when(userRepository.findByEmailIgnoreCase("owner2@example.com"))
            .thenReturn(Optional.of(requester));

    when(documentRepository.findById(100L))
            .thenReturn(Optional.of(document));

    mockMvc.perform(delete("/api/documents/100")
            .with(csrf()))
        .andExpect(status().isNotFound());
    
    verify(auditLogService).recordDeletionFailure(
            document, requester, "DENIED");

    // Confirm no file is deleted and no deletion is audited as successful.
    verify(storageService, never()).deleteQuietly(anyString());
    
    

    verify(auditLogService, never())
        .recordDeletion(any(Document.class), any(AppUser.class));
}

@Test
@WithMockUser(
    username = "viewer@example.com",
    roles = "VIEWER"
)
void viewerCannotReplaceAnotherUsersDocumentContent()
        throws Exception {

    AppUser viewer = mock(AppUser.class);
    when(viewer.getId()).thenReturn(2L);

    AppUser owner = mock(AppUser.class);
    when(owner.getId()).thenReturn(1L);

    Document document = mock(Document.class);
    
    when(document.getOwner()).thenReturn(owner);
    when(document.isDeleted()).thenReturn(false);

    when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
        .thenReturn(Optional.of(viewer));

    when(documentRepository.findById(100L))
        .thenReturn(Optional.of(document));

    when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
        .thenReturn(Optional.empty());

    MockMultipartFile replacement = new MockMultipartFile(
        "file",
        "replacement.txt",
        "text/plain",
        "unauthorized replacement".getBytes(
            java.nio.charset.StandardCharsets.UTF_8)
    );

    mockMvc.perform(
            multipart("/api/documents/100/content")
                .file(replacement)
                .with(csrf())
        )
        .andExpect(status().isNotFound());

    verify(auditLogService).recordDocumentUpdateFailure(
        document, viewer, "DENIED"
    );

    verify(storageService, never()).store(any(MultipartFile.class));
}

@Test
@WithMockUser(
    username = "viewer@example.com",
    roles = "VIEWER"
)
void viewerWithViewPermissionCannotEditDocument() throws Exception {

    AppUser viewer = mock(AppUser.class);
    when(viewer.getId()).thenReturn(2L);

    AppUser owner = mock(AppUser.class);
    when(owner.getId()).thenReturn(1L);

    Document document = mock(Document.class);
    when(document.getId()).thenReturn(100L);
    when(document.getOwner()).thenReturn(owner);
    when(document.isDeleted()).thenReturn(false);

    DocumentPermission permission = mock(DocumentPermission.class);
    when(permission.getPermissionType()).thenReturn(PermissionType.VIEW);

    when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
        .thenReturn(Optional.of(viewer));

    when(documentRepository.findById(100L))
        .thenReturn(Optional.of(document));

    when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
        .thenReturn(Optional.of(permission));

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

@Test
@WithMockUser(
    username = "editor@example.com",
    roles = "VIEWER"
)
void userWithEditPermissionCanUpdateDocumentMetadata()
        throws Exception {

    AppUser editor = mock(AppUser.class);
    when(editor.getId()).thenReturn(2L);

    AppUser owner = mock(AppUser.class);
    when(owner.getId()).thenReturn(1L);
    when(owner.getEmail()).thenReturn("owner@example.com");

    Document document = mock(Document.class);
    when(document.getId()).thenReturn(100L);
    when(document.getOwner()).thenReturn(owner);
    when(document.isDeleted()).thenReturn(false);
    when(document.getTitle()).thenReturn("Updated Contract");
    when(document.getCategory()).thenReturn("Contracts");
    when(document.getOriginalFilename()).thenReturn("contract.txt");

    DocumentPermission permission = mock(DocumentPermission.class);
    when(permission.getPermissionType()).thenReturn(PermissionType.EDIT);

    when(userRepository.findByEmailIgnoreCase("editor@example.com"))
        .thenReturn(Optional.of(editor));

    when(documentRepository.findById(100L))
        .thenReturn(Optional.of(document));

    when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
        .thenReturn(Optional.of(permission));

    when(documentRepository.saveAndFlush(document))
        .thenReturn(document);

    mockMvc.perform(put("/api/documents/100")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "title": "Updated Contract",
                    "category": "Contracts"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated Contract"))
        .andExpect(jsonPath("$.category").value("Contracts"));

    verify(document).updateMetadata("Updated Contract", "Contracts");
    verify(documentRepository).saveAndFlush(document);
    verify(auditLogService).recordDocumentUpdate(document, editor);
}

@Test
@WithMockUser(
    username = "viewer@example.com",
    roles = "VIEWER"
)
void viewerWithViewPermissionCannotReplaceDocumentContent()
        throws Exception {

    AppUser viewer = mock(AppUser.class);
    when(viewer.getId()).thenReturn(2L);

    AppUser owner = mock(AppUser.class);
    when(owner.getId()).thenReturn(1L);

    Document document = mock(Document.class);
    
    when(document.getOwner()).thenReturn(owner);
    when(document.isDeleted()).thenReturn(false);

    DocumentPermission permission = mock(DocumentPermission.class);
    when(permission.getPermissionType()).thenReturn(PermissionType.VIEW);

    when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
        .thenReturn(Optional.of(viewer));

    when(documentRepository.findById(100L))
        .thenReturn(Optional.of(document));

    when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
        .thenReturn(Optional.of(permission));

    MockMultipartFile replacement = new MockMultipartFile(
        "file",
        "replacement.txt",
        "text/plain",
        "replacement content".getBytes(
            java.nio.charset.StandardCharsets.UTF_8)
    );

    mockMvc.perform(multipart("/api/documents/100/content")
            .file(replacement)
            .with(csrf()))
        .andExpect(status().isNotFound());

    verify(auditLogService).recordDocumentUpdateFailure(
        document, viewer, "DENIED"
    );

    verify(storageService, never()).store(any(MultipartFile.class));
}

@Test
@WithMockUser(
    username = "editor@example.com",
    roles = "VIEWER"
)
void userWithEditPermissionCanReplaceDocumentContent()
        throws Exception {

    AppUser editor = mock(AppUser.class);
    when(editor.getId()).thenReturn(2L);

    AppUser owner = mock(AppUser.class);
    when(owner.getId()).thenReturn(1L);

    Document document = mock(Document.class);
    when(document.getId()).thenReturn(100L);
    when(document.getOwner()).thenReturn(owner);
    when(document.isDeleted()).thenReturn(false);
    when(document.getStorageKey()).thenReturn("old-storage-key");
    when(document.getTitle()).thenReturn("Test Contract");
    when(document.getCategory()).thenReturn("Contracts");
    when(document.getOriginalFilename()).thenReturn("replacement.txt");
    when(document.getSizeBytes()).thenReturn(19L);

    DocumentPermission permission = mock(DocumentPermission.class);
    when(permission.getPermissionType()).thenReturn(PermissionType.EDIT);

    when(userRepository.findByEmailIgnoreCase("editor@example.com"))
        .thenReturn(Optional.of(editor));

    when(documentRepository.findById(100L))
        .thenReturn(Optional.of(document));

    when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
        .thenReturn(Optional.of(permission));

    byte[] content = "replacement content".getBytes(
        java.nio.charset.StandardCharsets.UTF_8);

    MockMultipartFile replacement = new MockMultipartFile(
        "file",
        "replacement.txt",
        "text/plain",
        content
    );

    when(storageService.store(any(MultipartFile.class)))
        .thenReturn("new-storage-key");

    when(documentRepository.saveAndFlush(document))
        .thenReturn(document);

    mockMvc.perform(multipart("/api/documents/100/content")
            .file(replacement)
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filename").value("replacement.txt"))
        .andExpect(jsonPath("$.message")
            .value("Document content replaced successfully."));

    verify(document).replaceStoredFile(
        "replacement.txt",
        "new-storage-key",
        "text/plain",
        (long) content.length
    );

    verify(documentRepository).saveAndFlush(document);
    verify(auditLogService).recordDocumentUpdate(document, editor);
}

@Test
@WithMockUser(
    username = "viewer@example.com",
    roles = "VIEWER"
)
void viewerCannotUploadDocuments() throws Exception {

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "blocked.txt",
        "text/plain",
        "viewer upload attempt".getBytes(
            java.nio.charset.StandardCharsets.UTF_8)
    );

    mockMvc.perform(multipart("/api/documents")
            .file(file)
            .param("title", "Unauthorized Upload")
            .param("category", "Reports")
            .with(csrf()))
        .andExpect(status().isForbidden());

    verify(storageService, never())
        .store(any(MultipartFile.class));

    verify(documentRepository, never())
        .saveAndFlush(any(Document.class));
}

@Test
@WithMockUser(
    username = "viewer@example.com",
    roles = "VIEWER"
)
void viewerListingAndSearchUseOnlySharedDocuments()
        throws Exception {

    AppUser viewer = mock(AppUser.class);
    when(viewer.getId()).thenReturn(2L);
    when(viewer.getRole()).thenReturn(UserRole.VIEWER);

    when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
        .thenReturn(Optional.of(viewer));

    when(documentRepository.findSharedDocuments(2L))
        .thenReturn(List.of());

    mockMvc.perform(get("/api/documents"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));

    mockMvc.perform(get("/api/documents/search")
            .param("keyword", "confidential"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));

    verify(documentRepository, times(2))
        .findSharedDocuments(2L);

    verify(documentRepository, never())
        .findOwnedOrSharedDocuments(2L);

    verify(documentRepository, never())
        .findByOwner_RoleAndDeletedFalseOrderByCreatedAtDesc(
            UserRole.ADMIN);
}


@Test
@WithMockUser(
    username = "viewer@example.com",
    roles = "VIEWER"
)
void revokedPermissionImmediatelyPreventsNextDownload()
        throws Exception {

    AppUser viewer = mock(AppUser.class);
    when(viewer.getId()).thenReturn(2L);

    AppUser owner = mock(AppUser.class);
    when(owner.getId()).thenReturn(1L);

    Document document = mock(Document.class);
    when(document.getId()).thenReturn(100L);
    when(document.getOwner()).thenReturn(owner);
    when(document.isDeleted()).thenReturn(false);
    when(document.getStorageKey()).thenReturn("revocation-test-key");
    when(document.getContentType()).thenReturn("text/plain");

    DocumentPermission permission = mock(DocumentPermission.class);
    when(permission.getPermissionType()).thenReturn(PermissionType.VIEW);

    when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
        .thenReturn(Optional.of(viewer));

    when(documentRepository.findById(100L))
        .thenReturn(Optional.of(document));

    // First request has permission; second request sees revoked access.
    when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
            .thenReturn(Optional.of(permission))
            .thenReturn(Optional.empty());

    java.nio.file.Path tempFile =
        java.nio.file.Files.createTempFile(
            "trustvault-revocation-", ".txt");

    try {
        java.nio.file.Files.writeString(tempFile, "Test document content");

        when(storageService.resolve("revocation-test-key"))
            .thenReturn(tempFile);

        // Access succeeds before revocation.
        mockMvc.perform(get("/api/documents/100/download"))
            .andExpect(status().isOk());

        // The very next request is denied.
        mockMvc.perform(get("/api/documents/100/download"))
            .andExpect(status().isNotFound());

        verify(auditLogService).recordDownload(document, viewer);

        verify(auditLogService).recordDownloadFailure(
            document, viewer, "DENIED");

        verify(permissionRepository, times(2))
            .findByDocument_IdAndUser_Id(100L, 2L);

    } finally {
        java.nio.file.Files.deleteIfExists(tempFile);
    }
}


}
