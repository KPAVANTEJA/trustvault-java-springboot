package com.trustvault.document;

import java.util.List;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.trustvault.user.UserRole;

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
    
    @Query("""
    	    SELECT DISTINCT d
    	    FROM Document d
    	    LEFT JOIN DocumentPermission p ON p.document = d
    	    WHERE d.deleted = false
    	      AND (
    	          d.owner.id = :userId
    	          OR p.user.id = :userId
    	      )
    	      AND LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
    	    ORDER BY d.createdAt DESC
    	    """)
    	List<Document> searchAccessibleDocuments(
    	    @Param("userId") Long userId,
    	    @Param("keyword") String keyword
    	);
    
    List<Document> findByOwner_RoleAndDeletedFalseOrderByCreatedAtDesc(
    	    UserRole role
    	);
    
    @Query("""
    	    SELECT d
    	    FROM Document d
    	    WHERE d.deleted = false
    	      AND d.owner.role = :role
    	      AND LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
    	    ORDER BY d.createdAt DESC
    	    """)
    	List<Document> searchByOwnerRoleAndTitle(
    	    @Param("role") UserRole role,
    	    @Param("keyword") String keyword
    	);
    
    @Query("""
    	    SELECT DISTINCT d
    	    FROM Document d
    	    JOIN DocumentPermission p ON p.document = d
    	    WHERE d.deleted = false
    	      AND p.user.id = :userId
    	    ORDER BY d.createdAt DESC
    	    """)
    	List<Document> findSharedDocuments(@Param("userId") Long userId);
    
    
    @Query("""
    	    SELECT DISTINCT d
    	    FROM Document d
    	    JOIN DocumentPermission p ON p.document = d
    	    WHERE d.deleted = false
    	      AND p.user.id = :userId
    	      AND LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
    	    ORDER BY d.createdAt DESC
    	    """)
    	List<Document> searchSharedDocuments(
    	        @Param("userId") Long userId,
    	        @Param("keyword") String keyword);
}