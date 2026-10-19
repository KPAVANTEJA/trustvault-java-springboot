package com.trustvault.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    Optional<Document> findByIdAndOwner_Id(Long id, Long ownerId);

    boolean existsByStorageKey(String storageKey);
}