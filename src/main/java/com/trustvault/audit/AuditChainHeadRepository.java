package com.trustvault.audit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AuditChainHeadRepository
        extends JpaRepository<AuditChainHead, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT h
        FROM AuditChainHead h
        WHERE h.id = :id
        """)
    Optional<AuditChainHead> findByIdForUpdate(
            @Param("id") Long id);
    
 
}