package com.trustvault.audit;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class AuditIntegrityService {

    private static final Long CHAIN_HEAD_ID = 1L;
    private static final String UNINITIALIZED = "UNINITIALIZED";
    private static final String GENESIS = "GENESIS";
    

    private final AuditLogRepository auditLogRepository;
    private final AuditChainHeadRepository chainHeadRepository;
    private final String encodedSecret;
    
    @PersistenceContext
    private EntityManager entityManager;

    public AuditIntegrityService(
            AuditLogRepository auditLogRepository,
            AuditChainHeadRepository chainHeadRepository,
            @Value("${TRUSTVAULT_AUDIT_HMAC_KEY:}") String encodedSecret) {

        this.auditLogRepository = auditLogRepository;
        this.chainHeadRepository = chainHeadRepository;
        this.encodedSecret = encodedSecret;
    }

    /**
     * Appends one event inside the caller's existing transaction.
     * All AuditLogService write methods must call this method.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(AuditLog event) {

        AuditChainHead head = chainHeadRepository
                .findByIdForUpdate(CHAIN_HEAD_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Audit chain head is missing."));

        if (UNINITIALIZED.equals(head.getCheckpointHmac())
                || head.getCheckpointHmac() == null) {
            initializeChain(head);
        }

        verifyLocked(head);

        // Link the new event to the current verified chain head.
        event.setPreviousHash(head.getLastHash());
        event.setEventHash(null);

     // Persist first so the database assigns the ID and timestamp.
        auditLogRepository.saveAndFlush(event);

        // Reload database-normalized values before calculating the hash.
        entityManager.refresh(event);

        String eventHash = calculateEventHash(event);
        event.setEventHash(eventHash);

        // Persist the signed event within the same transaction.
        auditLogRepository.saveAndFlush(event);

        head.setLastEventId(event.getId());
        head.setLastHash(eventHash);
        head.setUpdatedAt(LocalDateTime.now());
        head.setCheckpointHmac(calculateCheckpoint(head));

        chainHeadRepository.saveAndFlush(head);
    }

    /**
     * Verifies the chain while holding the chain-head lock so that
     * a concurrent append cannot change the history during verification.
     * Throws IllegalStateException if integrity verification fails.
     */
    @Transactional
    public void verifyIntegrity() {

        AuditChainHead head = chainHeadRepository
                .findByIdForUpdate(CHAIN_HEAD_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Audit chain head is missing."));

        if (head.getCheckpointHmac() == null
                || UNINITIALIZED.equals(head.getCheckpointHmac())) {
            throw new IllegalStateException(
                    "Audit chain has not been initialized.");
        }

        verifyLocked(head);
    }

    private void initializeChain(AuditChainHead head) {

        // Do not allow a reset to silently re-sign an existing chain.
        if (!GENESIS.equals(head.getLastHash())
                || head.getLastEventId() != null
                || head.getChainStartAfterId() != null
                || head.getHistoricalBaselineHmac() != null) {
            throw new IllegalStateException(
                    "Unsafe audit-chain reinitialization was detected.");
        }

        List<AuditLog> history = auditLogRepository.findAll(
                Sort.by(Sort.Direction.ASC, "id"));

        // Existing hash values mean a chain may already have existed.
        for (AuditLog item : history) {
            if (item.getPreviousHash() != null
                    || item.getEventHash() != null) {
                throw new IllegalStateException(
                        "Cannot initialize over existing hashed audit records.");
            }
        }

        Long baselineId = history.isEmpty()
                ? null
                : history.get(history.size() - 1).getId();

        String baselineHash = calculateHistoricalBaseline(history);

        head.setChainStartAfterId(baselineId);
        head.setHistoricalBaselineHmac(baselineHash);
        head.setLastEventId(baselineId);
        head.setLastHash(baselineHash);
        head.setUpdatedAt(LocalDateTime.now());
        head.setCheckpointHmac(calculateCheckpoint(head));

        chainHeadRepository.saveAndFlush(head);
    }

    private void verifyLocked(AuditChainHead head) {

        String storedCheckpoint = head.getCheckpointHmac();

        if (storedCheckpoint == null
                || UNINITIALIZED.equals(storedCheckpoint)
                || !constantTimeEquals(
                        storedCheckpoint, calculateCheckpoint(head))) {
            throw new IllegalStateException(
                    "Audit chain checkpoint verification failed.");
        }

        List<AuditLog> allRecords = auditLogRepository.findAll(
                Sort.by(Sort.Direction.ASC, "id"));

        Long baselineId = head.getChainStartAfterId();
        List<AuditLog> historicalRecords = new ArrayList<>();

        for (AuditLog item : allRecords) {
            if (baselineId != null && item.getId() <= baselineId) {
                historicalRecords.add(item);
            }
        }

        String actualBaseline = calculateHistoricalBaseline(
                historicalRecords);

        if (!constantTimeEquals(
                head.getHistoricalBaselineHmac(), actualBaseline)) {
            throw new IllegalStateException(
                    "Historical audit baseline verification failed.");
        }

        String expectedPreviousHash = head.getHistoricalBaselineHmac();
        Long expectedLastId = baselineId;

        for (AuditLog item : allRecords) {

            if (baselineId != null && item.getId() <= baselineId) {
                continue;
            }

            if (item.getPreviousHash() == null
                    || item.getEventHash() == null) {
                throw new IllegalStateException(
                        "An audit record is missing its integrity hashes.");
            }

            if (!constantTimeEquals(
                    expectedPreviousHash, item.getPreviousHash())) {
                throw new IllegalStateException(
                        "Audit chain link verification failed at event "
                                + item.getId());
            }

            String calculatedEventHash = calculateEventHash(item);

            if (!constantTimeEquals(
                    calculatedEventHash, item.getEventHash())) {
                throw new IllegalStateException(
                        "Audit event hash verification failed at event "
                                + item.getId());
            }

            expectedPreviousHash = item.getEventHash();
            expectedLastId = item.getId();
        }

        if (!Objects.equals(expectedLastId, head.getLastEventId())
                || !constantTimeEquals(
                        expectedPreviousHash, head.getLastHash())) {
            throw new IllegalStateException(
                    "Audit chain head does not match the stored history.");
        }
    }


private String calculateEventHash(AuditLog event) {

    Mac mac = newMac();

    boolean hasPermissionMetadata =
            event.getTargetUserId() != null
            || event.getTargetUserEmail() != null
            || event.getPreviousPermissionType() != null
            || event.getNewPermissionType() != null;

    // Preserve the original calculation for existing events.
    // Use V2 when permission-change metadata is present.
    updateField(mac, hasPermissionMetadata
            ? "TRUSTVAULT_EVENT_V2"
            : "TRUSTVAULT_EVENT_V1");

    updateField(mac, event.getPreviousHash());
    updateField(mac, valueOf(event.getId()));
    updateField(mac, valueOf(event.getDocument().getId()));
    updateField(mac, valueOf(event.getUser().getId()));
    updateField(mac, event.getAction());
    updateField(mac, event.getOutcome());
    updateField(mac, event.getEventTime() == null
            ? null : event.getEventTime().toString());

    if (hasPermissionMetadata) {
        updateField(mac, valueOf(event.getTargetUserId()));
        updateField(mac, event.getTargetUserEmail());
        updateField(mac, event.getPreviousPermissionType());
        updateField(mac, event.getNewPermissionType());
    }

    return HexFormat.of().formatHex(mac.doFinal());
}


private String calculateHistoricalBaseline(List<AuditLog> records) {

    Mac mac = newMac();

    // Keep the original baseline format when no permission metadata exists.
    // This preserves compatibility with existing historical baselines.
    boolean hasPermissionMetadata = records.stream().anyMatch(event ->
            event.getTargetUserId() != null
            || event.getTargetUserEmail() != null
            || event.getPreviousPermissionType() != null
            || event.getNewPermissionType() != null);

    updateField(mac, hasPermissionMetadata
            ? "TRUSTVAULT_HISTORICAL_BASELINE_V2"
            : "TRUSTVAULT_HISTORICAL_BASELINE_V1");

    updateField(mac, Integer.toString(records.size()));

    for (AuditLog event : records) {
        updateField(mac, valueOf(event.getId()));
        updateField(mac, valueOf(event.getDocument().getId()));
        updateField(mac, valueOf(event.getUser().getId()));
        updateField(mac, event.getAction());
        updateField(mac, event.getOutcome());
        updateField(mac, event.getEventTime() == null
                ? null : event.getEventTime().toString());
        updateField(mac, event.getPreviousHash());
        updateField(mac, event.getEventHash());

        // V2 protects permission metadata for historical records.
        if (hasPermissionMetadata) {
            updateField(mac, valueOf(event.getTargetUserId()));
            updateField(mac, event.getTargetUserEmail());
            updateField(mac, event.getPreviousPermissionType());
            updateField(mac, event.getNewPermissionType());
        }
    }

    return HexFormat.of().formatHex(mac.doFinal());
}

    private String calculateCheckpoint(AuditChainHead head) {

        Mac mac = newMac();

        updateField(mac, "TRUSTVAULT_CHECKPOINT_V1");
        updateField(mac, valueOf(head.getId()));
        updateField(mac, valueOf(head.getChainStartAfterId()));
        updateField(mac, valueOf(head.getLastEventId()));
        updateField(mac, head.getLastHash());
        updateField(mac, head.getHistoricalBaselineHmac());

        return HexFormat.of().formatHex(mac.doFinal());
    }

    private Mac newMac() {

        try {
            if (encodedSecret == null || encodedSecret.isBlank()) {
                throw new IllegalStateException(
                        "TRUSTVAULT_AUDIT_HMAC_KEY is not configured.");
            }

            byte[] keyBytes = Base64.getDecoder().decode(encodedSecret.trim());

            if (keyBytes.length < 32) {
                throw new IllegalStateException(
                        "The audit HMAC key must contain at least 32 bytes.");
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));

            return mac;

        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Unable to initialize audit HMAC.", ex);
        }
    }

    /**
     * Length-prefix fields to avoid ambiguous concatenation.
     */
    private void updateField(Mac mac, String value) {

        if (value == null) {
            mac.update(ByteBuffer.allocate(Integer.BYTES)
                    .putInt(-1).array());
            return;
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        mac.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(bytes.length).array());
        mac.update(bytes);
    }

    private String valueOf(Long value) {
        return value == null ? null : value.toString();
    }

    private boolean constantTimeEquals(String expected, String actual) {

        if (expected == null || actual == null) {
            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }
}