package com.agenticform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agenticform.model.entity.FormSubmission;

public interface FormSubmissionRepository extends JpaRepository<FormSubmission, Long> {

    long countByFormId(Long formId);

    @Query("""
            SELECT DISTINCT s FROM FormSubmission s
            LEFT JOIN FETCH s.answers a
            LEFT JOIN FETCH a.field
            WHERE s.form.id = :formId
            """)
    List<FormSubmission> findAllByFormIdWithAnswers(@Param("formId") Long formId);
}
