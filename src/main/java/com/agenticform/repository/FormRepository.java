package com.agenticform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agenticform.model.entity.Form;

public interface FormRepository extends JpaRepository<Form, Long> {

    List<Form> findAllByWorkspaceIdOrderByUpdatedAtDesc(Long workspaceId);

    Optional<Form> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("""
            SELECT DISTINCT f FROM Form f
            LEFT JOIN FETCH f.fields
            WHERE f.id = :id AND f.workspace.id = :workspaceId
            """)
    Optional<Form> findByIdAndWorkspaceIdWithFields(
            @Param("id") Long id,
            @Param("workspaceId") Long workspaceId);

    @Query("""
            SELECT DISTINCT f FROM Form f
            LEFT JOIN FETCH f.fields
            WHERE f.id = :id
            """)
    Optional<Form> findByIdWithFields(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Form f SET f.viewCount = f.viewCount + 1 WHERE f.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
