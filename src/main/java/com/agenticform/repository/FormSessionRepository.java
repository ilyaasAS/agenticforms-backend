package com.agenticform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agenticform.model.entity.FormSession;
import com.agenticform.model.entity.FormSessionStatus;

public interface FormSessionRepository extends JpaRepository<FormSession, String> {

    @Query("""
            SELECT s FROM FormSession s
            JOIN FETCH s.form
            WHERE s.form.id = :formId AND s.status = :status
            ORDER BY s.updatedAt DESC
            """)
    List<FormSession> findAllByForm_IdAndStatusOrderByUpdatedAtDesc(
            @Param("formId") Long formId,
            @Param("status") FormSessionStatus status);

    long countByForm_IdAndStatus(Long formId, FormSessionStatus status);

    Optional<FormSession> findFirstByForm_IdAndRespondentEmailIgnoreCaseAndStatusOrderByUpdatedAtDesc(
            Long formId,
            String respondentEmail,
            FormSessionStatus status);

    List<FormSession> findAllByForm_IdAndRespondentEmailIgnoreCaseAndStatus(
            Long formId,
            String respondentEmail,
            FormSessionStatus status);
}
