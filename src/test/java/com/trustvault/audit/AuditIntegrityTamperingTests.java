package com.trustvault.audit;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.trustvault.document.Document;
import com.trustvault.document.DocumentRepository;
import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

@SpringBootTest
class AuditIntegrityTamperingTests {

    @Autowired
    private AuditIntegrityService auditIntegrityService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditChainHeadRepository chainHeadRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;
    
    

    @Test
    @Transactional
    void detectsModifiedAuditData() {
        AuditLog event = createTestEvent("TEST_MODIFIED");

        // Simulate changing a stored audit field directly in Oracle.
        jdbcTemplate.update(
                "UPDATE TV_AUDIT_LOGS SET OUTCOME = ? WHERE ID = ?",
                "TAMPERED",
                event.getId());

        // Force subsequent queries to reload the modified database value.
        entityManager.clear();

        assertThrows(
                IllegalStateException.class,
                () -> auditIntegrityService.verifyIntegrity());
    }

    @Test
    @Transactional
    void detectsDeletedAuditEvent() {
        AuditLog event = createTestEvent("TEST_DELETED");

        auditLogRepository.delete(event);
        auditLogRepository.flush();
        entityManager.clear();

        assertThrows(
                IllegalStateException.class,
                () -> auditIntegrityService.verifyIntegrity());
    }

    @Test
    @Transactional
    void detectsBrokenEventOrderingLink() {
        AuditLog first = createTestEvent("TEST_ORDER_FIRST");
        AuditLog second = createTestEvent("TEST_ORDER_SECOND");

        // Break the second event's link to its actual predecessor.
        second.setPreviousHash(first.getPreviousHash());
        auditLogRepository.saveAndFlush(second);

        entityManager.clear();

        assertThrows(
                IllegalStateException.class,
                () -> auditIntegrityService.verifyIntegrity());
    }

    @Test
    @Transactional
    void detectsModifiedCheckpoint() {
        createTestEvent("TEST_CHECKPOINT");

        AuditChainHead head = chainHeadRepository.findById(1L)
                .orElseThrow();

        head.setCheckpointHmac("0".repeat(64));
        chainHeadRepository.saveAndFlush(head);

        entityManager.clear();

        assertThrows(
                IllegalStateException.class,
                () -> auditIntegrityService.verifyIntegrity());
    }

    private AuditLog createTestEvent(String action) {
        String suffix = UUID.randomUUID().toString();

        AppUser user = userRepository.saveAndFlush(
                new AppUser(
                        "audit-test-" + suffix + "@example.com",
                        "Audit Integrity Test",
                        "test-password-hash",
                        UserRole.OWNER));

        Document document = documentRepository.saveAndFlush(
                new Document(
                        "Integrity Test " + suffix,
                        "integrity-test.txt",
                        suffix,
                        "text/plain",
                        1L,
                        user,
                        "Tests"));

        AuditLog event = new AuditLog(
                document,
                user,
                action,
                "SUCCESS");

        auditIntegrityService.append(event);

        return event;
    }
}