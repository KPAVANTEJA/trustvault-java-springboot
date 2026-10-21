
package com.trustvault.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;


public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
	
	
	@Query("SELECT MAX(a.id) FROM AuditLog a")
	Long findMaxAuditLogId();

	List<AuditLog> findByIdLessThanEqualOrderByIdAsc(Long id);

	List<AuditLog> findByIdGreaterThanOrderByIdAsc(Long id);

	boolean existsByEventHashIsNotNull();

	List<AuditLog> findByEventHashIsNotNullOrderByIdAsc();

	boolean existsByIdGreaterThanAndEventHashIsNull(Long id);
	

@Query("""
    SELECT new com.trustvault.audit.AuditLogResponse(
        a.id,
        a.document.id,
        a.user.id,
        a.action,
        a.outcome,
        a.eventTime
    )
    FROM AuditLog a
    ORDER BY a.eventTime DESC
    """)
Page<AuditLogResponse> findRecentAuditLogs(Pageable pageable);

}
