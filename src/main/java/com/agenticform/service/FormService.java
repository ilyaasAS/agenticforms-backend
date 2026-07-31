package com.agenticform.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.CreateFormFieldRequest;
import com.agenticform.dto.CreateFormRequest;
import com.agenticform.dto.FormFieldResponse;
import com.agenticform.dto.FormResponse;
import com.agenticform.dto.FormResultsFieldResponse;
import com.agenticform.dto.FormResultsResponse;
import com.agenticform.dto.FormSubmissionRowResponse;
import com.agenticform.dto.FormSummaryResponse;
import com.agenticform.dto.InProgressSessionResponse;
import com.agenticform.dto.ReorderFormFieldsRequest;
import com.agenticform.dto.UpdateFormFieldRequest;
import com.agenticform.dto.UpdateFormRequest;
import com.agenticform.exception.FormFieldNotFoundException;
import com.agenticform.exception.FormNotFoundException;
import com.agenticform.exception.InvalidFormFieldException;
import com.agenticform.model.entity.FieldType;
import com.agenticform.model.entity.Form;
import com.agenticform.model.entity.FormField;
import com.agenticform.model.entity.FormSession;
import com.agenticform.model.entity.FormSessionStatus;
import com.agenticform.model.entity.FormStatus;
import com.agenticform.model.entity.FormSubmission;
import com.agenticform.model.entity.FormSubmissionAnswer;
import com.agenticform.model.entity.User;
import com.agenticform.model.entity.Workspace;
import com.agenticform.repository.FormFieldRepository;
import com.agenticform.repository.FormRepository;
import com.agenticform.repository.FormSessionRepository;
import com.agenticform.repository.FormSubmissionRepository;
import com.agenticform.repository.UserRepository;

@Service
public class FormService {

    private final FormRepository formRepository;
    private final FormFieldRepository formFieldRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final FormSessionRepository formSessionRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService authorizationService;
    private final FormMapper formMapper;

    public FormService(
            FormRepository formRepository,
            FormFieldRepository formFieldRepository,
            FormSubmissionRepository formSubmissionRepository,
            FormSessionRepository formSessionRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService authorizationService,
            FormMapper formMapper) {
        this.formRepository = formRepository;
        this.formFieldRepository = formFieldRepository;
        this.formSubmissionRepository = formSubmissionRepository;
        this.formSessionRepository = formSessionRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.formMapper = formMapper;
    }

    @Transactional(readOnly = true)
    public List<FormSummaryResponse> listForms(Long workspaceId, Long userId) {
        authorizationService.requireCanView(workspaceId, userId);
        return formRepository.findAllByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream()
                .map(form -> formMapper.toSummary(form, formFieldRepository.countByFormId(form.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public FormResponse getForm(Long workspaceId, Long formId, Long userId) {
        authorizationService.requireCanView(workspaceId, userId);
        Form form = requireFormInWorkspaceWithFields(workspaceId, formId);
        return formMapper.toResponse(form);
    }

    @Transactional
    public FormResponse createForm(Long workspaceId, Long userId, CreateFormRequest request) {
        authorizationService.requireCanUpdateWorkspace(workspaceId, userId);
        Workspace workspace = authorizationService.requireExistingWorkspace(workspaceId);
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: id=" + userId));

        Form form = Form.builder()
                .workspace(workspace)
                .title(request.title().trim())
                .description(normalizeText(request.description()))
                .status(request.status() != null ? request.status() : FormStatus.DRAFT)
                .createdBy(creator)
                .logicRulesJson(formMapper.serializeLogicRules(
                        request.logicRules() != null ? request.logicRules() : List.of()))
                .calculationsJson(formMapper.serializeCalculations(
                        request.calculations() != null ? request.calculations() : List.of()))
                .build();

        if (request.fields() != null) {
            int index = 0;
            for (CreateFormFieldRequest fieldRequest : request.fields()) {
                FormField field = buildField(fieldRequest, index);
                form.addField(field);
                index++;
            }
        }

        Form saved = formRepository.save(form);
        return formMapper.toResponse(saved);
    }

    @Transactional
    public FormResponse updateForm(Long workspaceId, Long formId, Long userId, UpdateFormRequest request) {
        authorizationService.requireCanUpdateWorkspace(workspaceId, userId);
        Form form = requireFormInWorkspace(workspaceId, formId);

        if (request.title() != null) {
            String trimmed = request.title().trim();
            if (!trimmed.isEmpty()) {
                form.setTitle(trimmed);
            }
        }
        if (request.description() != null) {
            form.setDescription(normalizeText(request.description()));
        }
        if (request.status() != null) {
            form.setStatus(request.status());
        }
        if (request.logicRules() != null) {
            form.setLogicRulesJson(formMapper.serializeLogicRules(request.logicRules()));
        }
        if (request.calculations() != null) {
            form.setCalculationsJson(formMapper.serializeCalculations(request.calculations()));
        }
        if (request.pages() != null) {
            form.setPagesJson(formMapper.serializePages(request.pages()));
        }

        Form saved = formRepository.save(form);
        return formMapper.toResponse(
                formRepository.findByIdAndWorkspaceIdWithFields(saved.getId(), workspaceId)
                        .orElse(saved));
    }

    @Transactional
    public void deleteForm(Long workspaceId, Long formId, Long userId) {
        authorizationService.requireCanUpdateWorkspace(workspaceId, userId);
        Form form = requireFormInWorkspace(workspaceId, formId);
        formRepository.delete(form);
    }

    @Transactional(readOnly = true)
    public List<FormFieldResponse> listFields(Long workspaceId, Long formId, Long userId) {
        authorizationService.requireCanView(workspaceId, userId);
        requireFormInWorkspace(workspaceId, formId);
        return formFieldRepository.findAllByFormIdOrderByDisplayOrderAsc(formId).stream()
                .map(formMapper::toFieldResponse)
                .toList();
    }

    @Transactional
    public FormFieldResponse createField(
            Long workspaceId,
            Long formId,
            Long userId,
            CreateFormFieldRequest request) {
        authorizationService.requireCanUpdateWorkspace(workspaceId, userId);
        Form form = requireFormInWorkspace(workspaceId, formId);

        int nextOrder = request.displayOrder() != null
                ? request.displayOrder()
                : formFieldRepository.countByFormId(formId);

        FormField field = buildField(request, nextOrder);
        field.setForm(form);
        FormField saved = formFieldRepository.save(field);

        return formMapper.toFieldResponse(saved);
    }

    @Transactional
    public FormFieldResponse updateField(
            Long workspaceId,
            Long formId,
            Long fieldId,
            Long userId,
            UpdateFormFieldRequest request) {
        authorizationService.requireCanUpdateWorkspace(workspaceId, userId);
        requireFormInWorkspace(workspaceId, formId);

        FormField field = formFieldRepository.findByIdAndFormId(fieldId, formId)
                .orElseThrow(() -> new FormFieldNotFoundException(fieldId));

        if (request.label() != null) {
            String trimmed = request.label().trim();
            if (!trimmed.isEmpty()) {
                field.setLabel(trimmed);
            }
        }
        if (request.fieldType() != null) {
            field.setFieldType(request.fieldType());
        }
        if (request.required() != null) {
            field.setRequired(request.required());
        }
        if (request.displayOrder() != null) {
            field.setDisplayOrder(request.displayOrder());
        }
        if (request.placeholder() != null) {
            field.setPlaceholder(normalizeText(request.placeholder()));
        }
        if (request.uiComponent() != null) {
            String ui = request.uiComponent().trim();
            field.setUiComponent(ui.isEmpty() ? null : ui);
        }
        if (request.settings() != null) {
            field.setSettingsJson(formMapper.serializeFieldSettings(request.settings()));
        }
        if (request.options() != null) {
            FieldType effectiveType = request.fieldType() != null ? request.fieldType() : field.getFieldType();
            validateOptions(effectiveType, request.options());
            field.setOptionsJson(formMapper.serializeOptions(request.options()));
        } else if (request.fieldType() != null && !formMapper.requiresOptions(request.fieldType())) {
            field.setOptionsJson(null);
        }

        FormField saved = formFieldRepository.save(field);
        return formMapper.toFieldResponse(saved);
    }

    @Transactional
    public void deleteField(Long workspaceId, Long formId, Long fieldId, Long userId) {
        authorizationService.requireCanUpdateWorkspace(workspaceId, userId);
        requireFormInWorkspace(workspaceId, formId);
        FormField field = formFieldRepository.findByIdAndFormId(fieldId, formId)
                .orElseThrow(() -> new FormFieldNotFoundException(fieldId));
        formFieldRepository.delete(field);
    }

    @Transactional
    public List<FormFieldResponse> reorderFields(
            Long workspaceId,
            Long formId,
            Long userId,
            ReorderFormFieldsRequest request) {
        authorizationService.requireCanUpdateWorkspace(workspaceId, userId);
        requireFormInWorkspace(workspaceId, formId);

        List<FormField> existing = formFieldRepository.findAllByFormIdOrderByDisplayOrderAsc(formId);
        if (existing.size() != request.fieldIds().size()) {
            throw new InvalidFormFieldException(
                    "La liste de réordonnancement doit contenir exactement tous les champs du formulaire.");
        }

        Set<Long> existingIds = new HashSet<>();
        Map<Long, FormField> byId = new HashMap<>();
        for (FormField field : existing) {
            existingIds.add(field.getId());
            byId.put(field.getId(), field);
        }

        Set<Long> requestedIds = new HashSet<>(request.fieldIds());
        if (!existingIds.equals(requestedIds)) {
            throw new InvalidFormFieldException(
                    "La liste de réordonnancement ne correspond pas aux champs du formulaire.");
        }

        List<FormField> reordered = new ArrayList<>();
        int order = 0;
        for (Long fieldId : request.fieldIds()) {
            FormField field = byId.get(fieldId);
            field.setDisplayOrder(order++);
            reordered.add(field);
        }
        formFieldRepository.saveAll(reordered);

        return reordered.stream().map(formMapper::toFieldResponse).toList();
    }

    @Transactional(readOnly = true)
    public FormResultsResponse getFormResults(Long workspaceId, Long formId, Long userId) {
        authorizationService.requireCanView(workspaceId, userId);
        Form form = requireFormInWorkspaceWithFields(workspaceId, formId);

        List<FormResultsFieldResponse> fields = form.getFields().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(field -> new FormResultsFieldResponse(
                        field.getId(),
                        field.getLabel(),
                        field.getFieldType().name(),
                        field.getDisplayOrder(),
                        formMapper.parseOptions(field.getOptionsJson())))
                .toList();

        List<FormSubmission> submissions = formSubmissionRepository.findAllByFormIdWithAnswers(formId)
                .stream()
                .sorted((a, b) -> b.getSubmittedAt().compareTo(a.getSubmittedAt()))
                .toList();

        List<FormSubmissionRowResponse> rows = submissions.stream()
                .map(this::toSubmissionRow)
                .toList();

        long submissionCount = rows.size();
        long viewCount = form.getViewCount();
        Double completionRate = viewCount > 0
                ? Math.round((submissionCount * 10000.0) / viewCount) / 100.0
                : null;

        return new FormResultsResponse(
                form.getId(),
                form.getTitle(),
                submissionCount,
                viewCount,
                completionRate,
                fields,
                rows);
    }

    @Transactional(readOnly = true)
    public List<InProgressSessionResponse> listInProgressSessions(
            Long workspaceId,
            Long formId,
            Long userId) {
        authorizationService.requireCanView(workspaceId, userId);
        Form form = requireFormInWorkspaceWithFields(workspaceId, formId);

        List<FormField> orderedFields = form.getFields().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .toList();
        int totalSteps = Math.max(1, orderedFields.size());
        Map<Long, FormField> fieldsById = new HashMap<>();
        Map<Long, Integer> stepByFieldId = new HashMap<>();
        for (int i = 0; i < orderedFields.size(); i++) {
            FormField field = orderedFields.get(i);
            fieldsById.put(field.getId(), field);
            stepByFieldId.put(field.getId(), i + 1);
        }

        return formSessionRepository
                .findAllByForm_IdAndStatusOrderByUpdatedAtDesc(formId, FormSessionStatus.IN_PROGRESS)
                .stream()
                .map(session -> toInProgressResponse(session, fieldsById, stepByFieldId, totalSteps))
                .toList();
    }

    private InProgressSessionResponse toInProgressResponse(
            FormSession session,
            Map<Long, FormField> fieldsById,
            Map<Long, Integer> stepByFieldId,
            int totalSteps) {
        try {
            Map<Long, String> answers = formMapper.parseAnswerMap(
                    session != null ? session.getAnswersJson() : null);
            Long lastFieldId = session != null ? session.getLastFieldId() : null;
            String lastFieldLabel = null;
            int currentStep = 1;

            if (lastFieldId != null && fieldsById.containsKey(lastFieldId)) {
                FormField lastField = fieldsById.get(lastFieldId);
                lastFieldLabel = lastField != null ? lastField.getLabel() : null;
                currentStep = stepByFieldId.getOrDefault(lastFieldId, 1);
            } else if (!answers.isEmpty()) {
                final int inferredStep = answers.keySet().stream()
                        .map(id -> stepByFieldId.getOrDefault(id, 1))
                        .max(Integer::compareTo)
                        .orElse(1);
                currentStep = inferredStep;
                Long inferred = stepByFieldId.entrySet().stream()
                        .filter(e -> e.getValue() == inferredStep)
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
                if (inferred != null && fieldsById.containsKey(inferred)) {
                    lastFieldId = inferred;
                    FormField inferredField = fieldsById.get(inferred);
                    lastFieldLabel = inferredField != null ? inferredField.getLabel() : null;
                }
            }

            Long formId = null;
            if (session != null && session.getForm() != null) {
                formId = session.getForm().getId();
            }

            double progressPercent = Math.round((currentStep * 1000.0) / Math.max(1, totalSteps)) / 10.0;
            return new InProgressSessionResponse(
                    session != null ? session.getSessionId() : null,
                    formId,
                    lastFieldId,
                    lastFieldLabel,
                    currentStep,
                    Math.max(1, totalSteps),
                    progressPercent,
                    answers != null ? answers : Map.of(),
                    session != null ? session.getUpdatedAt() : null,
                    session != null ? session.getCreatedAt() : null);
        } catch (RuntimeException ex) {
            // Une session corrompue ne doit pas faire échouer toute la liste.
            Long fallbackFormId = null;
            try {
                if (session != null && session.getForm() != null) {
                    fallbackFormId = session.getForm().getId();
                }
            } catch (RuntimeException ignored) {
                // ignore lazy/proxy failures
            }
            return new InProgressSessionResponse(
                    session != null ? session.getSessionId() : "unknown",
                    fallbackFormId,
                    null,
                    null,
                    1,
                    Math.max(1, totalSteps),
                    0.0,
                    Map.of(),
                    session != null ? session.getUpdatedAt() : null,
                    session != null ? session.getCreatedAt() : null);
        }
    }

    private FormSubmissionRowResponse toSubmissionRow(FormSubmission submission) {
        Map<Long, String> answers = new HashMap<>();
        if (submission.getAnswers() != null) {
            for (FormSubmissionAnswer answer : submission.getAnswers()) {
                if (answer.getField() == null) {
                    continue;
                }
                answers.put(answer.getField().getId(), answer.getValueText());
            }
        }
        return new FormSubmissionRowResponse(
                submission.getId(),
                submission.getSubmittedAt(),
                answers);
    }

    private FormField buildField(CreateFormFieldRequest request, int fallbackOrder) {
        validateOptions(request.fieldType(), request.options());

        int order = request.displayOrder() != null ? request.displayOrder() : fallbackOrder;
        return FormField.builder()
                .label(request.label().trim())
                .fieldType(request.fieldType())
                .required(Boolean.TRUE.equals(request.required()))
                .displayOrder(order)
                .optionsJson(formMapper.serializeOptions(request.options()))
                .placeholder(normalizeText(request.placeholder()))
                .uiComponent(normalizeText(request.uiComponent()))
                .settingsJson(formMapper.serializeFieldSettings(request.settings()))
                .build();
    }

    private void validateOptions(FieldType fieldType, List<String> options) {
        if (!formMapper.requiresOptions(fieldType)) {
            return;
        }
        if (options == null || options.stream().noneMatch(o -> o != null && !o.isBlank())) {
            throw new InvalidFormFieldException(
                    "Les champs de type choix doivent contenir au moins une option.");
        }
    }

    private Form requireFormInWorkspace(Long workspaceId, Long formId) {
        return formRepository.findByIdAndWorkspaceId(formId, workspaceId)
                .orElseThrow(() -> new FormNotFoundException(formId));
    }

    private Form requireFormInWorkspaceWithFields(Long workspaceId, Long formId) {
        return formRepository.findByIdAndWorkspaceIdWithFields(formId, workspaceId)
                .orElseThrow(() -> new FormNotFoundException(formId));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
