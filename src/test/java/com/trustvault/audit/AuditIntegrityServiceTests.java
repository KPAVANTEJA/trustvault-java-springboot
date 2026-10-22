package com.trustvault.audit;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.trustvault.document.Document;
import com.trustvault.document.DocumentRepository;
import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

import jakarta.transaction.Transactional;

@SpringBootTest
@TestPropertySource(properties = {
	    "TRUSTVAULT_AUDIT_HMAC_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
	})
class AuditIntegrityServiceTests {
	
	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
	@Test
	void diagnoseAuditChainHeadDatabase() {
	    jdbcTemplate.execute(
	        (org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
	            var metadata = connection.getMetaData();

	            System.out.println("===== TEST DATABASE =====");
	            System.out.println("Database: "
	                    + metadata.getDatabaseProductName());
	            System.out.println("Database version: "
	                    + metadata.getDatabaseProductVersion());
	            System.out.println("JDBC URL: "
	                    + metadata.getURL());
	            System.out.println("Database user: "
	                    + metadata.getUserName());

	            return null;
	        });

	    try {
	        System.out.println("===== TEST CHAIN HEAD =====");
	        System.out.println(jdbcTemplate.queryForList(
	            "SELECT ID, LAST_EVENT_ID, LAST_HASH " +
	            "FROM TV_AUDIT_CHAIN_HEAD WHERE ID = 1"));
	    } catch (Exception ex) {
	        System.out.println("Could not read chain head: "
	                + ex.getMessage());
	        throw ex;
	    }
	}
	
	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private DocumentRepository documentRepository;
	
	
	@BeforeEach
	void ensureAuditChainHeadExists() {
	    Integer count = jdbcTemplate.queryForObject(
	        "SELECT COUNT(*) FROM TV_AUDIT_CHAIN_HEAD WHERE ID = 1",
	        Integer.class
	    );

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
	                1,
	                NULL,
	                'GENESIS',
	                'UNINITIALIZED',
	                CURRENT_TIMESTAMP,
	                NULL,
	                NULL
	            )
	            """);
	    }
	}

    @Autowired
    private AuditIntegrityService auditIntegrityService;

    @Test
    @Transactional
    void currentAuditChainPassesVerification() {
        String suffix = UUID.randomUUID().toString();

        AppUser user = userRepository.saveAndFlush(
            new AppUser(
                "audit-test-" + suffix + "@example.com",
                "Audit Integrity Test",
                "test-password-hash",
                UserRole.OWNER
            )
        );

        Document document = documentRepository.saveAndFlush(
            new Document(
                "Integrity Test " + suffix,
                "integrity-test.txt",
                suffix,
                "text/plain",
                1L,
                user,
                "Tests"
            )
        );

        AuditLog event = new AuditLog(
            document,
            user,
            "TEST_VERIFICATION",
            "SUCCESS"
        );

        auditIntegrityService.append(event);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
            () -> auditIntegrityService.verifyIntegrity()
        );
    }
}