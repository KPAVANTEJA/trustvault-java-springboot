package com.trustvault.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.trustvault.audit.AuditLogService;
import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class DocumentPermissionAuthorizationTests {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentPermissionRepository permissionRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DocumentPermissionService permissionService;

    @Test
    void ownerCannotGrantPermissionOnAnotherUsersDocument() {
        AppUser requester = mock(AppUser.class);
        when(requester.getId()).thenReturn(2L);

        AppUser actualOwner = mock(AppUser.class);
        when(actualOwner.getId()).thenReturn(1L);

        Document document = mock(Document.class);
        when(document.getOwner()).thenReturn(actualOwner);
        when(document.isDeleted()).thenReturn(false);

        when(userRepository.findByEmailIgnoreCase("owner2@example.com"))
                .thenReturn(Optional.of(requester));

        when(documentRepository.findById(100L))
                .thenReturn(Optional.of(document));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> permissionService.grantPermission(
                        100L,
                        "recipient@example.com",
                        PermissionType.VIEW,
                        "owner2@example.com"));

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                exception.getStatusCode().value());

        verify(auditLogService).recordPermissionChangeFailure(
                eq(document),
                any(AppUser.class),
                isNull(),
                eq("recipient@example.com"),
                isNull(),
                eq("VIEW"),
                eq("PERMISSION_GRANT"),
                eq("DENIED")
        );

        verify(permissionRepository, never())
                .save(any(DocumentPermission.class));

        verify(userRepository, never())
                .findByEmailIgnoreCase("recipient@example.com");
    }

    @Test
    void ownerCannotRevokePermissionOnAnotherUsersDocument() {
        AppUser requester = mock(AppUser.class);
        when(requester.getId()).thenReturn(2L);

        AppUser actualOwner = mock(AppUser.class);
        when(actualOwner.getId()).thenReturn(1L);

        Document document = mock(Document.class);
        when(document.getOwner()).thenReturn(actualOwner);
        when(document.isDeleted()).thenReturn(false);

        when(userRepository.findByEmailIgnoreCase("owner2@example.com"))
                .thenReturn(Optional.of(requester));

        when(documentRepository.findById(100L))
                .thenReturn(Optional.of(document));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> permissionService.revokePermission(
                        100L, 3L, "owner2@example.com"));

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                exception.getStatusCode().value());

        verify(auditLogService).recordPermissionChangeFailure(
                eq(document),
                any(AppUser.class),
                eq(3L),
                isNull(),
                isNull(),
                isNull(),
                eq("PERMISSION_REVOKE"),
                eq("DENIED")
        );

        verify(permissionRepository, never())
                .delete(any(DocumentPermission.class));

        verify(permissionRepository, never())
                .findByDocument_IdAndUser_Id(100L, 3L);
    }
    
    @Test
    void successfulGrantRecordsAuditEvent() {
        AppUser owner = mock(AppUser.class);
        when(owner.getId()).thenReturn(1L);
        

        AppUser recipient = mock(AppUser.class);
        when(recipient.getId()).thenReturn(2L);
        when(recipient.getEmail()).thenReturn("viewer@example.com");
        

        Document document = mock(Document.class);
        when(document.getOwner()).thenReturn(owner);
        when(document.isDeleted()).thenReturn(false);

        when(userRepository.findByEmailIgnoreCase("owner@example.com"))
                .thenReturn(Optional.of(owner));

        when(documentRepository.findById(100L))
                .thenReturn(Optional.of(document));

        when(userRepository.findByEmailIgnoreCase("recipient@example.com"))
                .thenReturn(Optional.of(recipient));

        when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
                .thenReturn(Optional.empty());

        when(permissionRepository.saveAndFlush(any(DocumentPermission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentPermission saved = permissionService.grantPermission(
                100L,
                "recipient@example.com",
                PermissionType.VIEW,
                "owner@example.com");

        assertEquals(PermissionType.VIEW, saved.getPermissionType());

        verify(auditLogService).recordPermissionChange(
        	    document,
        	    owner,
        	    2L,
        	    "viewer@example.com",
        	    null,
        	    "VIEW",
        	    "PERMISSION_GRANT",
        	    "SUCCESS"
        	);
    }
    
    @Test
    void successfulRevocationRecordsAuditEvent() {
        AppUser owner = mock(AppUser.class);
        when(owner.getId()).thenReturn(1L);


        Document document = mock(Document.class);
        when(document.getOwner()).thenReturn(owner);
        when(document.isDeleted()).thenReturn(false);

        AppUser recipient = mock(AppUser.class);
        when(recipient.getId()).thenReturn(2L);
        when(recipient.getEmail()).thenReturn("viewer@example.com");

        DocumentPermission permission = mock(DocumentPermission.class);
        when(permission.getUser()).thenReturn(recipient);
        when(permission.getPermissionType()).thenReturn(PermissionType.VIEW);

        when(userRepository.findByEmailIgnoreCase("owner@example.com"))
                .thenReturn(Optional.of(owner));

        when(documentRepository.findById(100L))
                .thenReturn(Optional.of(document));

        when(permissionRepository.findByDocument_IdAndUser_Id(100L, 2L))
                .thenReturn(Optional.of(permission));

        permissionService.revokePermission(
                100L, 2L, "owner@example.com");

        verify(permissionRepository).delete(permission);
        verify(permissionRepository).flush();

        verify(auditLogService).recordPermissionChange(
                document,
                owner,
                2L,
                "viewer@example.com",
                "VIEW",
                null,
                "PERMISSION_REVOKE",
                "SUCCESS"
        );
    }
}