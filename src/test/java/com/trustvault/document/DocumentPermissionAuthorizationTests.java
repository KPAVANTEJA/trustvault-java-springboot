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

        when(userRepository.findByEmailIgnoreCase("owner2@example.com"))
                .thenReturn(Optional.of(requester));

        // The document is not owned by this requester.
        when(documentRepository.findByIdAndOwner_Id(100L, 2L))
                .thenReturn(Optional.empty());

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

        verify(permissionRepository, never())
                .save(any(DocumentPermission.class));

        verify(userRepository, never())
                .findByEmailIgnoreCase("recipient@example.com");
    }

    @Test
    void ownerCannotRevokePermissionOnAnotherUsersDocument() {

        AppUser requester = mock(AppUser.class);
        when(requester.getId()).thenReturn(2L);

        when(userRepository.findByEmailIgnoreCase("owner2@example.com"))
                .thenReturn(Optional.of(requester));

        // The document is not owned by this requester.
        when(documentRepository.findByIdAndOwner_Id(100L, 2L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> permissionService.revokePermission(
                        100L,
                        3L,
                        "owner2@example.com"));

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                exception.getStatusCode().value());

        verify(permissionRepository, never())
                .delete(any(DocumentPermission.class));

        verify(permissionRepository, never())
                .deleteByDocument_IdAndUser_Id(100L, 3L);

        verify(permissionRepository, never())
                .findByDocument_IdAndUser_Id(100L, 3L);
    }
}