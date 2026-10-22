package com.trustvault.document;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentVisibilityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Test
    @Transactional
    @WithMockUser(
        username = "viewer@example.com",
        roles = "VIEWER"
    )
    void viewerSeesOnlyExplicitlySharedDocumentsInListingAndSearch()
            throws Exception {

        AppUser viewer = saveUser(
                "viewer@example.com", UserRole.VIEWER);

        AppUser owner = saveUser(
                "owner@example.com", UserRole.OWNER);

        Document sharedDocument = saveDocument(
                owner, "Shared Contract");

        Document privateDocument = saveDocument(
                owner, "Secret Salary Report");

        permissionRepository.saveAndFlush(
                new DocumentPermission(
                        sharedDocument,
                        viewer,
                        PermissionType.VIEW,
                        owner));

        // Listing includes the shared document but not the private one.
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[*].id",
                        hasItem(sharedDocument.getId().intValue())))
                .andExpect(jsonPath(
                        "$[*].id",
                        not(hasItem(privateDocument.getId().intValue()))));

        // Search can find the document explicitly shared with the viewer.
        mockMvc.perform(get("/api/documents/search")
                        .param("keyword", "Shared Contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[*].id",
                        hasItem(sharedDocument.getId().intValue())));

        // Searching for an unshared document must not reveal it.
        mockMvc.perform(get("/api/documents/search")
                        .param("keyword", "Secret Salary Report"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @Transactional
    @WithMockUser(
        username = "owner@example.com",
        roles = "OWNER"
    )
    void ownerSeesOwnedAndSharedDocumentsButNotUnrelatedDocuments()
            throws Exception {

        AppUser owner = saveUser(
                "owner@example.com", UserRole.OWNER);

        AppUser otherOwner = saveUser(
                "other-owner@example.com", UserRole.OWNER);

        AppUser recipient = saveUser(
                "recipient@example.com", UserRole.VIEWER);

        Document ownedDocument = saveDocument(
                owner, "My Project Plan");

        Document sharedDocument = saveDocument(
                otherOwner, "Shared External Contract");

        Document privateDocument = saveDocument(
                otherOwner, "Other Private Report");

        permissionRepository.saveAndFlush(
                new DocumentPermission(
                        sharedDocument,
                        owner,
                        PermissionType.VIEW,
                        otherOwner));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[*].id",
                        hasItem(ownedDocument.getId().intValue())))
                .andExpect(jsonPath(
                        "$[*].id",
                        hasItem(sharedDocument.getId().intValue())))
                .andExpect(jsonPath(
                        "$[*].id",
                        not(hasItem(privateDocument.getId().intValue()))));

        mockMvc.perform(get("/api/documents/search")
                        .param("keyword", "Shared External Contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[*].id",
                        hasItem(sharedDocument.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(
        username = "admin@example.com",
        roles = "ADMIN"
    )
    void adminSeesAdminOwnedDocumentsOnly()
            throws Exception {

        AppUser admin = saveUser(
                "admin@example.com", UserRole.ADMIN);

        AppUser owner = saveUser(
                "owner@example.com", UserRole.OWNER);

        Document adminDocument = saveDocument(
                admin, "Admin Policy Report");

        Document ownerDocument = saveDocument(
                owner, "Owner Confidential Report");

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[*].id",
                        hasItem(adminDocument.getId().intValue())))
                .andExpect(jsonPath(
                        "$[*].id",
                        not(hasItem(ownerDocument.getId().intValue()))));

        mockMvc.perform(get("/api/documents/search")
                        .param("keyword", "Owner Confidential Report"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    private AppUser saveUser(String email, UserRole role) {
        return userRepository.saveAndFlush(
                new AppUser(
                        email,
                        role.name() + " Test User",
                        "test-password-hash",
                        role));
    }

    private Document saveDocument(AppUser owner, String title) {
        String suffix = UUID.randomUUID().toString();

        return documentRepository.saveAndFlush(
                new Document(
                        title,
                        title.replace(' ', '-') + ".txt",
                        suffix,
                        "text/plain",
                        100L,
                        owner,
                        "Tests"));
    }
}