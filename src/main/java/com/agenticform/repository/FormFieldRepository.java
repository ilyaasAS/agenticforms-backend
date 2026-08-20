package com.agenticform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agenticform.model.entity.FormField;

public interface FormFieldRepository extends JpaRepository<FormField, Long> {

    List<FormField> findAllByFormIdAndDeletedAtIsNullOrderByDisplayOrderAsc(Long formId);

    List<FormField> findAllByFormIdOrderByDisplayOrderAsc(Long formId);

    Optional<FormField> findByIdAndFormIdAndDeletedAtIsNull(Long id, Long formId);

    Optional<FormField> findByIdAndFormId(Long id, Long formId);

    int countByFormIdAndDeletedAtIsNull(Long formId);

    @Query("""
            select distinct a.field from FormSubmissionAnswer a
            where a.submission.form.id = :formId
              and a.field.deletedAt is not null
            """)
    List<FormField> findDeletedFieldsWithAnswersByFormId(@Param("formId") Long formId);
}
