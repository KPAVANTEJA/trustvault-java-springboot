package com.trustvault.document;

import java.util.List;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    Optional<Document> findByIdAndOwner_Id(Long id, Long ownerId);

    boolean existsByStorageKey(String storageKey);
    
    List<Document> findByOwner_IdAndDeletedFalseOrderByCreatedAtDesc(Long ownerId);
    
    @Query("""
    	    SELECT DISTINCT d
    	    FROM Document d
    	    LEFT JOIN DocumentPermission p ON p.document = d
    	    WHERE d.deleted = false
    	    AND ( 
    	    		d.owner.id = :userId
    	       OR p.user.id = :userId 
    	       )
    	    ORDER BY d.createdAt DESC
    	    """)
    	List<Document> findOwnedOrSharedDocuments(
    	    @Param("userId") Long userId
    	);
}