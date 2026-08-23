package com.agenticform.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.AdminFormResponse;
import com.agenticform.exception.FormNotFoundException;
import com.agenticform.model.entity.Form;
import com.agenticform.model.entity.User;
import com.agenticform.model.entity.Workspace;
import com.agenticform.repository.FormRepository;

@Service
public class AdminFormService {

    private final FormRepository formRepository;

    public AdminFormService(FormRepository formRepository) {
        this.formRepository = formRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminFormResponse> list() {
        return formRepository.findAllForAdmin().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminFormResponse setBlocked(Long formId, boolean blocked) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new FormNotFoundException(formId));
        form.setBlocked(blocked);
        return toResponse(formRepository.save(form));
    }

    @Transactional
    public void delete(Long formId) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new FormNotFoundException(formId));
        formRepository.delete(form);
    }

    private AdminFormResponse toResponse(Form form) {
        Workspace workspace = form.getWorkspace();
        User owner = form.getCreatedBy();
        if (owner == null && workspace != null) {
            owner = workspace.getOwner();
        }
        return new AdminFormResponse(
                form.getId(),
                form.getTitle(),
                form.getStatus() == null ? null : form.getStatus().name(),
                form.isBlocked(),
                workspace == null ? null : workspace.getId(),
                workspace == null ? null : workspace.getName(),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getEmail(),
                owner == null ? null : owner.getFullName(),
                form.getCreatedAt(),
                form.getUpdatedAt());
    }
}
