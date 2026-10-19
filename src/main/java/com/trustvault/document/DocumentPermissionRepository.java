
package com.trustvault.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPermissionRepository
        extends JpaRepository<DocumentPermission, Long> {

    Optional<DocumentPermission> findByDocument_IdAndUser_Id(
            Long documentId, Long userId);

    List<DocumentPermission> findByDocument_Id(Long documentId);

    List<DocumentPermission> findByUser_Id(Long userId);

    boolean existsByDocument_IdAndUser_Id(
            Long documentId, Long userId);

    void deleteByDocument_IdAndUser_Id(
            Long documentId, Long userId);
}
