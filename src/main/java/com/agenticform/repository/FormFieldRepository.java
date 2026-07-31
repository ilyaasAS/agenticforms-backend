package com.agenticform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agenticform.model.entity.FormField;

public interface FormFieldRepository extends JpaRepository<FormField, Long> {

    List<FormField> findAllByFormIdOrderByDisplayOrderAsc(Long formId);

    Optional<FormField> findByIdAndFormId(Long id, Long formId);

    int countByFormId(Long formId);

    void deleteByIdAndFormId(Long id, Long formId);
}
