package com.trustvault.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "TV_AUDIT_CHAIN_HEAD")
public class AuditChainHead {

    @Id
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "LAST_EVENT_ID")
    private Long lastEventId;

    @Column(name = "LAST_HASH", nullable = false, length = 64)
    private String lastHash;

    @Column(name = "CHECKPOINT_HMAC", nullable = false, length = 64)
    private String checkpointHmac;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "CHAIN_START_AFTER_ID")
    private Long chainStartAfterId;
    
    @Column(name = "HISTORICAL_BASELINE_HMAC", length = 64)
    private String historicalBaselineHmac;

    public String getHistoricalBaselineHmac() {
        return historicalBaselineHmac;
    }

    public void setHistoricalBaselineHmac(String historicalBaselineHmac) {
        this.historicalBaselineHmac = historicalBaselineHmac;
    }

    protected AuditChainHead() {
    }

    public Long getId() {
        return id;
    }

    public Long getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(Long lastEventId) {
        this.lastEventId = lastEventId;
    }

    public String getLastHash() {
        return lastHash;
    }

    public void setLastHash(String lastHash) {
        this.lastHash = lastHash;
    }

    public String getCheckpointHmac() {
        return checkpointHmac;
    }

    public void setCheckpointHmac(String checkpointHmac) {
        this.checkpointHmac = checkpointHmac;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Long getChainStartAfterId() {
        return chainStartAfterId;
    }

    public void setChainStartAfterId(Long chainStartAfterId) {
        this.chainStartAfterId = chainStartAfterId;
    }
}