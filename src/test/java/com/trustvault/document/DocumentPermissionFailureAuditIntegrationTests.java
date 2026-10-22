package com.trustvault.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import com.trustvault.audit.AuditIntegrityService;
import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

@SpringBootTest
@TestPropertySource(properties = {
    "TRUSTVAULT_AUDIT_HMAC_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class DocumentPermissionFailureAuditIntegrationTests {

    @Autowired
    private DocumentPermissionService permissionService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditIntegrityService auditIntegrityService;

    @BeforeEach
    void ensureAuditChainHeadExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TV_AUDIT_CHAIN_HEAD WHERE ID = 1",
                Integer.class);

        if (count == null || count == 0) {
            jdbcTemplate.update("""
                INSERT INTO TV_AUDIT_CHAIN_HEAD (
                    ID, LAST_EVENT_ID, LAST_HASH, CHECKPOINT_HMAC,
                    UPDATED_AT, CHAIN_START_AFTER_ID,
                    HISTORICAL_BASELINE_HMAC
                )
                VALUES (
                    1, NULL, 'GENESIS', 'UNINITIALIZED',
                    CURRENT_TIMESTAMP, NULL, NULL
                )
                """);
        }
    }

    @Test
    void deniedGrantIsPersistedInAuditChain() {
        TestData data = createTestData();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> permissionService.grantPermission(
                        data.document().getId(),
                        data.recipient().getEmail(),
                        PermissionType.VIEW,
                        data.attacker().getEmail()));

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                exception.getStatusCode().value());

        assertEquals(0, countPermission(data.document().getId(),
                data.recipient().getId()));

        assertDeniedAuditExists(
                data.document().getId(),
                data.attacker().getId(),
                "PERMISSION_GRANT");

        auditIntegrityService.verifyIntegrity();
    }

    @Test
    void deniedRevocationIsPersistedInAuditChain() {
        TestData data = createTestData();

        DocumentPermission existingPermission =
                permissionRepository.saveAndFlush(
                        new DocumentPermission(
                                data.document(),
                                data.recipient(),
                                PermissionType.VIEW,
                                data.realOwner()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> permissionService.revokePermission(
                        data.document().getId(),
                        data.recipient().getId(),
                        data.attacker().getEmail()));

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                exception.getStatusCode().value());

        assertTrue(permissionRepository
                .findById(existingPermission.getId())
                .isPresent(),
                "Denied revocation must not remove the permission");

        assertDeniedAuditExists(
                data.document().getId(),
                data.attacker().getId(),
                "PERMISSION_REVOKE");

        auditIntegrityService.verifyIntegrity();
    }

    private void assertDeniedAuditExists(
            Long documentId, Long actorId, String action) {

        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM TV_AUDIT_LOGS
            WHERE DOCUMENT_ID = ?
              AND USER_ID = ?
              AND ACTION = ?
              AND OUTCOME = 'DENIED'
              AND EVENT_HASH IS NOT NULL
              AND PREVIOUS_HASH IS NOT NULL
            """,
            Integer.class,
            documentId,
            actorId,
            action);

        assertEquals(1, count,
                "Expected one hash-linked denial audit event");
    }

    private int countPermission(Long documentId, Long recipientId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM TV_DOCUMENT_PERMISSIONS
            WHERE DOCUMENT_ID = ?
              AND USER_ID = ?
            """,
            Integer.class,
            documentId,
            recipientId);

        return count == null ? 0 : count;
    }

    private TestData createTestData() {
        String suffix = UUID.randomUUID().toString();

        AppUser realOwner = saveUser(
                "real-owner-" + suffix + "@example.com",
                UserRole.OWNER);

        AppUser attacker = saveUser(
                "other-owner-" + suffix + "@example.com",
                UserRole.OWNER);

        AppUser recipient = saveUser(
                "recipient-" + suffix + "@example.com",
                UserRole.VIEWER);

        Document document = documentRepository.saveAndFlush(
                new Document(
                        "Private Document " + suffix,
                        "private-" + suffix + ".txt",
                        suffix,
                        "text/plain",
                        100L,
                        realOwner,
                        "Tests"));

        return new TestData(realOwner, attacker, recipient, document);
    }

    private AppUser saveUser(String email, UserRole role) {
        return userRepository.saveAndFlush(
                new AppUser(
                        email,
                        role.name() + " Test User",
                        "test-password-hash",
                        role));
    }

    private record TestData(
            AppUser realOwner,
            AppUser attacker,
            AppUser recipient,
            Document document) {
    }
}