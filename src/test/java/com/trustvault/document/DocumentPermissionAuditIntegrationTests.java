package com.trustvault.document;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.trustvault.audit.AuditLog;
import com.trustvault.audit.AuditLogRepository;
import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

@SpringBootTest
@TestPropertySource(properties = {
    "TRUSTVAULT_AUDIT_HMAC_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Transactional
class DocumentPermissionAuditIntegrationTests {

    @Autowired
    private DocumentPermissionService permissionService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureAuditChainHeadExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TV_AUDIT_CHAIN_HEAD WHERE ID = 1",
                Integer.class);

        if (count == null || count == 0) {
            jdbcTemplate.update("""
                INSERT INTO TV_AUDIT_CHAIN_HEAD (
                    ID,
                    LAST_EVENT_ID,
                    LAST_HASH,
                    CHECKPOINT_HMAC,
                    UPDATED_AT,
                    CHAIN_START_AFTER_ID,
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
    void successfulGrantPersistsHmacProtectedAuditEvent() {
        TestData data = createTestData();

        permissionService.grantPermission(
                data.document().getId(),
                data.recipient().getEmail(),
                PermissionType.VIEW,
                data.owner().getEmail());

        assertTrue(hasAuditEvent(
                data.document(),
                data.owner(),
                "PERMISSION_GRANT",
                "SUCCESS"),
                "Successful grant audit event should be persisted");

        assertTrue(hasHashedAuditEvent(
                data.document(),
                data.owner(),
                "PERMISSION_GRANT"),
                "Grant event should have integrity hashes");
    }

    @Test
    void successfulRevocationPersistsHmacProtectedAuditEvent() {
        TestData data = createTestData();

        permissionService.grantPermission(
                data.document().getId(),
                data.recipient().getEmail(),
                PermissionType.VIEW,
                data.owner().getEmail());

        permissionService.revokePermission(
                data.document().getId(),
                data.recipient().getId(),
                data.owner().getEmail());

        assertTrue(hasAuditEvent(
                data.document(),
                data.owner(),
                "PERMISSION_REVOKE",
                "SUCCESS"),
                "Successful revocation audit event should be persisted");

        assertTrue(hasHashedAuditEvent(
                data.document(),
                data.owner(),
                "PERMISSION_REVOKE"),
                "Revocation event should have integrity hashes");
    }

    private TestData createTestData() {
        String suffix = UUID.randomUUID().toString();

        AppUser owner = userRepository.saveAndFlush(
                new AppUser(
                        "owner-" + suffix + "@example.com",
                        "Test Owner",
                        "test-password-hash",
                        UserRole.OWNER));

        AppUser recipient = userRepository.saveAndFlush(
                new AppUser(
                        "viewer-" + suffix + "@example.com",
                        "Test Viewer",
                        "test-password-hash",
                        UserRole.VIEWER));

        Document document = documentRepository.saveAndFlush(
                new Document(
                        "Permission Audit " + suffix,
                        "permission-audit.txt",
                        suffix,
                        "text/plain",
                        100L,
                        owner,
                        "Tests"));

        return new TestData(owner, recipient, document);
    }

    private boolean hasAuditEvent(
            Document document,
            AppUser actor,
            String action,
            String outcome) {

        return auditLogRepository.findAll().stream()
                .anyMatch(event ->
                        event.getDocument().getId().equals(document.getId())
                        && event.getUser().getId().equals(actor.getId())
                        && event.getAction().equals(action)
                        && event.getOutcome().equals(outcome));
    }

    private boolean hasHashedAuditEvent(
            Document document,
            AppUser actor,
            String action) {

        return auditLogRepository.findAll().stream()
                .anyMatch(event ->
                        event.getDocument().getId().equals(document.getId())
                        && event.getUser().getId().equals(actor.getId())
                        && event.getAction().equals(action)
                        && event.getEventHash() != null
                        && event.getPreviousHash() != null);
    }

    private record TestData(
            AppUser owner,
            AppUser recipient,
            Document document) {
    }
}