
package com.trustvault.audit;

import org.springframework.data.jpa.repository.JpaRepository;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;


public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
	

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
