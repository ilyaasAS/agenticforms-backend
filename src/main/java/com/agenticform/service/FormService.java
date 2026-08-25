package com.agenticform.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.CreateFormFieldRequest;
import com.agenticform.dto.CreateFormRequest;
import com.agenticform.dto.FormFieldResponse;
import com.agenticform.dto.FormPageDto;
import com.agenticform.dto.FormResponse;
import com.agenticform.dto.FormResultsFieldResponse;
import com.agenticform.dto.FormResultsResponse;
import com.agenticform.dto.FormSubmissionRowResponse;
import com.agenticform.dto.FormSummaryResponse;
import com.agenticform.dto.InProgressSessionResponse;
import com.agenticform.dto.ProgressBarConfigDto;
import com.agenticform.dto.PublicFormFieldResponse;
import com.agenticform.dto.PublicFormResponse;
import com.agenticform.dto.ReorderFormFieldsRequest;
import com.agenticform.dto.SetFormLoginPasswordRequest;
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
import com.agenticform.model.entity.Role;
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
    private final LoginConfigSupport loginConfigSupport;
    private final FormLoginService formLoginService;

    public FormService(
            FormRepository formRepository,
            FormFieldRepository formFieldRepository,
            FormSubmissionRepository formSubmissionRepository,
            FormSessionRepository formSessionRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService authorizationService,
            FormMapper formMapper,
            LoginConfigSupport loginConfigSupport,
            FormLoginService formLoginService) {
        this.formRepository = formRepository;
        this.formFieldRepository = formFieldRepository;
        this.formSubmissionRepository = formSubmissionRepository;
        this.formSessionRepository = formSessionRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.formMapper = formMapper;
        this.loginConfigSupport = loginConfigSupport;
        this.formLoginService = formLoginService;
    }

    @Transactional(readOnly = true)
    public List<FormSummaryResponse> listForms(Long workspaceId, Long userId) {
        requireFormView(workspaceId, userId);
        return formRepository.findAllByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream()
                .map(form -> formMapper.toSummary(form, formFieldRepository.countByFormIdAndDeletedAtIsNull(form.getId())))
                .toList();
    }

    @Transactional
    public FormResponse getForm(Long workspaceId, Long formId, Long userId) {
        requireFormView(workspaceId, userId);
        Form form = requireFormInWorkspaceWithFields(workspaceId, formId);
        pruneOrphanSchedulingSlots(form);
        ensurePublishedSnapshot(form);
        return formMapper.toResponse(form);
    }

    @Transactional
    public FormResponse createForm(Long workspaceId, Long userId, CreateFormRequest request) {
        requireFormWrite(workspaceId, userId);
        Workspace workspace = authorizationService.requireExistingWorkspace(workspaceId);
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: id=" + userId));

        Form form = Form.builder()
                .workspace(workspace)
                .title(request.title().trim())
                .description(normalizeText(request.description()))
                .status(request.status() != null ? request.status() : FormStatus.DRAFT)
                .createdBy(creator)
                .themeId(request.themeId() != null && !request.themeId().isBlank()
                        ? request.themeId().trim()
                        : "dark")
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
    public FormResponse duplicateForm(Long workspaceId, Long formId, Long userId) {
        requireFormWrite(workspaceId, userId);
        Form source = requireFormInWorkspaceWithFields(workspaceId, formId);
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: id=" + userId));

        Form copy = Form.builder()
                .workspace(source.getWorkspace())
                .title(duplicateTitle(source.getTitle()))
                .description(source.getDescription())
                .status(FormStatus.DRAFT)
                .createdBy(creator)
                .themeId(source.getThemeId() != null && !source.getThemeId().isBlank()
                        ? source.getThemeId()
                        : "dark")
                .logicRulesJson(source.getLogicRulesJson())
                .calculationsJson(source.getCalculationsJson())
                .pagesJson(source.getPagesJson())
                .hasUnpublishedChanges(true)
                .build();

        List<FormField> sourceFields = source.getFields() == null
                ? List.of()
                : source.getFields().stream().filter(field -> !field.isDeleted()).toList();
        for (FormField field : sourceFields) {
            copy.addField(cloneField(field));
        }

        Form saved = formRepository.saveAndFlush(copy);
        Map<Long, Long> fieldIdMap = new LinkedHashMap<>();
        List<FormField> copiedFields = saved.getFields() == null ? List.of() : saved.getFields();
        int limit = Math.min(sourceFields.size(), copiedFields.size());
        for (int i = 0; i < limit; i++) {
            Long oldId = sourceFields.get(i).getId();
            Long newId = copiedFields.get(i).getId();
            if (oldId != null && newId != null) {
                fieldIdMap.put(oldId, newId);
            }
        }

        if (!fieldIdMap.isEmpty()) {
            saved.setPagesJson(formMapper.remapFieldIds(saved.getPagesJson(), fieldIdMap));
            saved.setLogicRulesJson(formMapper.remapFieldIds(saved.getLogicRulesJson(), fieldIdMap));
            saved.setCalculationsJson(formMapper.remapFieldIds(saved.getCalculationsJson(), fieldIdMap));
            for (FormField field : copiedFields) {
                field.setSettingsJson(formMapper.remapFieldIds(field.getSettingsJson(), fieldIdMap));
            }
            saved = formRepository.save(saved);
        }

        return formMapper.toResponse(
                formRepository.findByIdAndWorkspaceIdWithFields(saved.getId(), workspaceId)
                        .orElse(saved));
    }

    @Transactional
    public FormResponse updateForm(Long workspaceId, Long formId, Long userId, UpdateFormRequest request) {
        requireFormWrite(workspaceId, userId);
        Form form = requireFormInWorkspace(workspaceId, formId);
        boolean contentChanged = false;

        if (request.title() != null) {
            String trimmed = request.title().trim();
            if (!trimmed.isEmpty()) {
                form.setTitle(trimmed);
                contentChanged = true;
            }
        }
        if (request.description() != null) {
            form.setDescription(normalizeText(request.description()));
            contentChanged = true;
        }
        if (request.logicRules() != null) {
            form.setLogicRulesJson(formMapper.serializeLogicRules(request.logicRules()));
            contentChanged = true;
        }
        if (request.calculations() != null) {
            form.setCalculationsJson(formMapper.serializeCalculations(request.calculations()));
            contentChanged = true;
        }
        if (request.pages() != null) {
            ProgressBarConfigDto progressBar = request.progressBar();
            if (progressBar == null) {
                progressBar = formMapper.parseProgressBar(form.getPagesJson());
            }
            List<FormPageDto> existingPages = formMapper.parsePages(form.getPagesJson());
            List<FormPageDto> mergedPages = loginConfigSupport.mergeLoginPasswordHashes(
                    existingPages, request.pages());
            form.setPagesJson(formMapper.serializePagesDocument(mergedPages, progressBar));
            contentChanged = true;
        } else if (request.progressBar() != null) {
            List<FormPageDto> pages = formMapper.parsePages(form.getPagesJson());
            form.setPagesJson(formMapper.serializePagesDocument(pages, request.progressBar()));
            contentChanged = true;
        }
        if (request.themeId() != null) {
            String themeId = request.themeId().trim();
            if (!themeId.isEmpty()) {
                form.setThemeId(themeId);
                contentChanged = true;
            }
        }

        if (request.status() == FormStatus.PUBLISHED) {
            // Publier = figer le brouillon actuel (y compris les champs ci-dessus).
            publishFormSnapshot(form);
        } else {
            if (request.status() != null) {
                form.setStatus(request.status());
            }
            if (contentChanged) {
                markDraftChanged(form);
            }
        }

        Form saved = formRepository.save(form);
        return formMapper.toResponse(
                formRepository.findByIdAndWorkspaceIdWithFields(saved.getId(), workspaceId)
                        .orElse(saved));
    }

    /**
     * Publie le brouillon : le lien public sert désormais ce snapshot jusqu’au prochain Publish.
     */
    @Transactional
    public FormResponse publishForm(Long workspaceId, Long formId, Long userId) {
        requireFormWrite(workspaceId, userId);
        Form form = requireFormInWorkspaceWithFields(workspaceId, formId);
        publishFormSnapshot(form);
        Form saved = formRepository.save(form);
        return formMapper.toResponse(
                formRepository.findByIdAndWorkspaceIdWithFields(saved.getId(), workspaceId)
                        .orElse(saved));
    }

    /** Aperçu éditeur : payload public construit depuis le brouillon (pas le snapshot). */
    @Transactional(readOnly = true)
    public PublicFormResponse getDraftAsPublic(Long workspaceId, Long formId, Long userId) {
        requireFormView(workspaceId, userId);
        Form form = requireFormInWorkspaceWithFields(workspaceId, formId);
        return formMapper.toPublicResponse(form);
    }

    private void publishFormSnapshot(Form form) {
        pruneStaleFormFields(form);
        applyPublishedSnapshot(form);
    }

    /**
     * Répare les snapshots publiés legacy sans champ « Créneau » sur les pages planification.
     */
    @Transactional
    public void repairPublishedSchedulingSnapshot(Form form) {
        if (form == null || form.getStatus() != FormStatus.PUBLISHED) {
            return;
        }
        PublicFormResponse previousSnapshot = formMapper.parsePublishedSnapshot(form.getPublishedSnapshotJson());
        pruneStaleFormFields(form);
        if (!schedulingSnapshotOutOfDate(previousSnapshot, form)) {
            return;
        }
        applyPublishedSnapshot(form);
        formRepository.save(form);
    }

    void applyPublishedSnapshot(Form form) {
        Instant now = Instant.now();
        PublicFormResponse live = formMapper.toPublishedSnapshotResponse(form);
        PublicFormResponse snapshot = new PublicFormResponse(
                live.id(),
                live.title(),
                live.description(),
                FormStatus.PUBLISHED.name(),
                live.fields(),
                live.logicRules(),
                live.calculations(),
                live.pages(),
                live.themeId(),
                live.progressBar(),
                now);
        form.setPublishedSnapshotJson(formMapper.serializePublishedSnapshot(snapshot));
        form.setPublishedAt(now);
        form.setStatus(FormStatus.PUBLISHED);
        form.setHasUnpublishedChanges(false);
    }

    void markDraftChanged(Form form) {
        form.setHasUnpublishedChanges(true);
    }

    void ensurePublishedSnapshot(Form form) {
        if (form.getStatus() != FormStatus.PUBLISHED) {
            return;
        }
        if (form.getPublishedSnapshotJson() != null && !form.getPublishedSnapshotJson().isBlank()) {
            return;
        }
        // Backfill legacy : fige l’état actuel sans toucher hasUnpublishedChanges.
        Instant at = form.getUpdatedAt() != null ? form.getUpdatedAt() : Instant.now();
        PublicFormResponse live = formMapper.toPublishedSnapshotResponse(form);
        PublicFormResponse snapshot = new PublicFormResponse(
                live.id(),
                live.title(),
                live.description(),
                FormStatus.PUBLISHED.name(),
                live.fields(),
                live.logicRules(),
                live.calculations(),
                live.pages(),
                live.themeId(),
                live.progressBar(),
                at);
        form.setPublishedSnapshotJson(formMapper.serializePublishedSnapshot(snapshot));
        if (form.getPublishedAt() == null) {
            form.setPublishedAt(at);
        }
        formRepository.save(form);
    }

    @Transactional
    public FormResponse setLoginPassword(
            Long workspaceId,
            Long formId,
            Long userId,
            SetFormLoginPasswordRequest request) {
        requireFormWrite(workspaceId, userId);
        Form form = requireFormInWorkspaceWithFields(workspaceId, formId);
        formLoginService.setLoginPassword(form, request.password());
        if (form.getStatus() == FormStatus.PUBLISHED) {
            applyPublishedSnapshot(form);
        } else {
            markDraftChanged(form);
        }
        Form saved = formRepository.save(form);
        return formMapper.toResponse(
                formRepository.findByIdAndWorkspaceIdWithFields(saved.getId(), workspaceId)
                        .orElse(saved));
    }

    @Transactional
    public void deleteForm(Long workspaceId, Long formId, Long userId) {
        requireFormWrite(workspaceId, userId);
        Form form = requireFormInWorkspace(workspaceId, formId);
        formRepository.delete(form);
    }

    @Transactional(readOnly = true)
    public List<FormFieldResponse> listFields(Long workspaceId, Long formId, Long userId) {
        requireFormView(workspaceId, userId);
        requireFormInWorkspace(workspaceId, formId);
        return formFieldRepository.findAllByFormIdAndDeletedAtIsNullOrderByDisplayOrderAsc(formId).stream()
                .map(formMapper::toFieldResponse)
                .toList();
    }

    @Transactional
    public FormFieldResponse createField(
            Long workspaceId,
            Long formId,
            Long userId,
            CreateFormFieldRequest request) {
        requireFormWrite(workspaceId, userId);
        Form form = requireFormInWorkspace(workspaceId, formId);

        int nextOrder = request.displayOrder() != null
                ? request.displayOrder()
                : formFieldRepository.countByFormIdAndDeletedAtIsNull(formId);

        FormField field = buildField(request, nextOrder);
        field.setForm(form);
        FormField saved = formFieldRepository.save(field);
        markDraftChanged(form);
        formRepository.save(form);

        return formMapper.toFieldResponse(saved);
    }

    @Transactional
    public FormFieldResponse updateField(
            Long workspaceId,
            Long formId,
            Long fieldId,
            Long userId,
            UpdateFormFieldRequest request) {
        requireFormWrite(workspaceId, userId);
        Form form = requireFormInWorkspace(workspaceId, formId);

        FormField field = formFieldRepository.findByIdAndFormIdAndDeletedAtIsNull(fieldId, formId)
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
            String normalizedPlaceholder = normalizeText(request.placeholder());
            field.setPlaceholder(
                    normalizedPlaceholder == null || normalizedPlaceholder.isBlank()
                            ? null
                            : normalizedPlaceholder);
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
        markDraftChanged(form);
        formRepository.save(form);
        return formMapper.toFieldResponse(saved);
    }

    @Transactional
    public void deleteField(Long workspaceId, Long formId, Long fieldId, Long userId) {
        requireFormWrite(workspaceId, userId);
        Form form = requireFormInWorkspace(workspaceId, formId);
        FormField field = formFieldRepository.findByIdAndFormIdAndDeletedAtIsNull(fieldId, formId)
                .orElseThrow(() -> new FormFieldNotFoundException(fieldId));
        field.setDeletedAt(Instant.now());
        formFieldRepository.save(field);
        removeFieldFromPages(form, fieldId);
        markDraftChanged(form);
        formRepository.save(form);
    }

    @Transactional
    public List<FormFieldResponse> reorderFields(
            Long workspaceId,
            Long formId,
            Long userId,
            ReorderFormFieldsRequest request) {
        requireFormWrite(workspaceId, userId);
        Form form = requireFormInWorkspace(workspaceId, formId);

        List<FormField> existing =
                formFieldRepository.findAllByFormIdAndDeletedAtIsNullOrderByDisplayOrderAsc(formId);
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
        markDraftChanged(form);
        formRepository.save(form);

        return reordered.stream().map(formMapper::toFieldResponse).toList();
    }

    @Transactional(readOnly = true)
    public FormResultsResponse getFormResults(Long workspaceId, Long formId, Long userId) {
        requireFormView(workspaceId, userId);
        Form form = requireFormInWorkspaceWithFields(workspaceId, formId);

        List<FormResultsFieldResponse> activeFields = form.getFields().stream()
                .filter(field -> !field.isDeleted())
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(field -> toResultsField(field, false))
                .toList();

        // Champs retirés qui ont encore des réponses historiques.
        List<FormResultsFieldResponse> removedFields = formFieldRepository
                .findDeletedFieldsWithAnswersByFormId(formId)
                .stream()
                .sorted((a, b) -> {
                    int byOrder = Integer.compare(a.getDisplayOrder(), b.getDisplayOrder());
                    if (byOrder != 0) {
                        return byOrder;
                    }
                    return Long.compare(a.getId(), b.getId());
                })
                .map(field -> toResultsField(field, true))
                .toList();

        List<FormResultsFieldResponse> fields = new ArrayList<>(activeFields.size() + removedFields.size());
        fields.addAll(activeFields);
        fields.addAll(removedFields);

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
        requireFormView(workspaceId, userId);
        Form form = requireFormInWorkspaceWithFields(workspaceId, formId);

        List<FormField> orderedFields = form.getFields().stream()
                .filter(field -> !field.isDeleted())
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
                    session != null ? session.getRespondentEmail() : null,
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
                    session != null ? session.getRespondentEmail() : null,
                    session != null ? session.getUpdatedAt() : null,
                    session != null ? session.getCreatedAt() : null);
        }
    }

    private FormResultsFieldResponse toResultsField(FormField field, boolean removed) {
        return new FormResultsFieldResponse(
                field.getId(),
                field.getLabel(),
                field.getFieldType().name(),
                field.getDisplayOrder(),
                formMapper.parseOptions(field.getOptionsJson()),
                removed,
                formMapper.parseFieldSettings(field.getSettingsJson()));
    }

    @Transactional
    public void pruneOrphanSchedulingSlots(Form form) {
        pruneStaleFormFields(form);
    }

    @Transactional
    public void pruneStaleFormFields(Form form) {
        if (form == null) {
            return;
        }
        if (form.getFields() == null) {
            form.setFields(new ArrayList<>());
        }
        var document = formMapper.parsePagesDocument(form.getPagesJson());
        List<FormPageDto> pages = document.pages() == null ? List.of() : document.pages();
        Map<Long, FormField> fieldsById = new HashMap<>();
        for (FormField field : form.getFields()) {
            if (!field.isDeleted()) {
                fieldsById.put(field.getId(), field);
            }
        }

        Set<Long> assignedOnPages = new HashSet<>();
        boolean pagesChanged = false;
        boolean fieldsChanged = false;
        List<FormPageDto> nextPages = new ArrayList<>();

        for (FormPageDto page : pages) {
            if (page == null) {
                continue;
            }
            List<Long> fieldIds = page.fieldIds() == null ? List.of() : page.fieldIds();
            if ("scheduling".equalsIgnoreCase(page.type())) {
                // Fillout-like : conserver créneau + champs custom de la page de réservation.
                List<Long> kept = new ArrayList<>();
                boolean hasSlot = false;
                for (Long fieldId : fieldIds) {
                    if (fieldId == null) {
                        continue;
                    }
                    FormField field = fieldsById.get(fieldId);
                    if (field == null) {
                        pagesChanged = true;
                        continue;
                    }
                    kept.add(fieldId);
                    assignedOnPages.add(fieldId);
                    if (isSchedulingSlotField(field)) {
                        hasSlot = true;
                    }
                }
                if (!hasSlot) {
                    FormField slot = form.getFields().stream()
                            .filter(field -> !field.isDeleted() && isSchedulingSlotField(field))
                            .findFirst()
                            .orElse(null);
                    if (slot == null) {
                        slot = createSchedulingSlotField(form);
                        fieldsById.put(slot.getId(), slot);
                        fieldsChanged = true;
                    }
                    if (slot.getId() != null && !kept.contains(slot.getId())) {
                        kept.add(0, slot.getId());
                        assignedOnPages.add(slot.getId());
                        pagesChanged = true;
                    }
                }
                if (!kept.equals(fieldIds)) {
                    pagesChanged = true;
                    page = copyPageWithFieldIds(page, kept);
                }
            } else {
                for (Long fieldId : fieldIds) {
                    if (fieldId != null) {
                        assignedOnPages.add(fieldId);
                    }
                }
            }
            nextPages.add(page);
        }

        Instant now = Instant.now();
        for (FormField field : form.getFields()) {
            if (field.isDeleted()) {
                continue;
            }
            Long fieldId = field.getId();
            if (assignedOnPages.contains(fieldId)) {
                continue;
            }
            field.setDeletedAt(now);
            fieldsChanged = true;
        }

        if (fieldsChanged) {
            formFieldRepository.saveAll(form.getFields());
        }
        if (pagesChanged) {
            form.setPagesJson(formMapper.serializePagesDocument(nextPages, document.progressBar()));
            formRepository.save(form);
        }
    }

    private FormField createSchedulingSlotField(Form form) {
        int nextOrder = form.getFields().stream()
                .filter(field -> !field.isDeleted())
                .mapToInt(FormField::getDisplayOrder)
                .max()
                .orElse(-1) + 1;
        FormField field = FormField.builder()
                .label("Créneau")
                .fieldType(FieldType.TEXT)
                .required(true)
                .displayOrder(nextOrder)
                .uiComponent("scheduling_page")
                .settingsJson("{\"schedulingSlot\":true}")
                .build();
        form.addField(field);
        return formFieldRepository.saveAndFlush(field);
    }

    private FormPageDto copyPageWithFieldIds(FormPageDto page, List<Long> fieldIds) {
        return new FormPageDto(
                page.id(),
                page.type(),
                page.title(),
                page.navLabel(),
                page.description(),
                fieldIds,
                page.buttonText(),
                page.headerImage(),
                page.coverLayoutMedia(),
                page.coverImagePosition(),
                page.customCoverLayout(),
                page.endingConfig(),
                page.reviewConfig(),
                page.loginConfig(),
                page.paymentConfig(),
                page.contentBlocks(),
                page.canvasOrder(),
                page.progressStepId());
    }

    private boolean isSchedulingSlotField(FormField field) {
        if ("scheduling_page".equalsIgnoreCase(field.getUiComponent())) {
            return true;
        }
        String settings = field.getSettingsJson();
        return settings != null && settings.contains("\"schedulingSlot\":true");
    }

    private boolean schedulingSnapshotOutOfDate(PublicFormResponse previousSnapshot, Form form) {
        PublicFormResponse draftSnapshot = formMapper.toPublishedSnapshotResponse(form);
        if (draftSnapshot.pages() == null) {
            return false;
        }
        for (FormPageDto page : draftSnapshot.pages()) {
            if (page == null || !"scheduling".equalsIgnoreCase(page.type())) {
                continue;
            }
            List<Long> draftFieldIds = page.fieldIds() == null ? List.of() : page.fieldIds();
            List<Long> publishedFieldIds = publishedFieldIdsForPage(previousSnapshot, page.id());
            if (!draftFieldIds.equals(publishedFieldIds)) {
                return true;
            }
        }
        long draftSlotCount = countPublicSchedulingSlotFields(draftSnapshot.fields());
        long publishedSlotCount = previousSnapshot == null || previousSnapshot.fields() == null
                ? 0
                : countPublicSchedulingSlotFields(previousSnapshot.fields());
        return draftSlotCount > publishedSlotCount;
    }

    private List<Long> publishedFieldIdsForPage(PublicFormResponse snapshot, String pageId) {
        if (snapshot == null || snapshot.pages() == null || pageId == null) {
            return List.of();
        }
        for (FormPageDto page : snapshot.pages()) {
            if (page != null && pageId.equals(page.id())) {
                return page.fieldIds() == null ? List.of() : page.fieldIds();
            }
        }
        return List.of();
    }

    private long countPublicSchedulingSlotFields(List<PublicFormFieldResponse> fields) {
        if (fields == null) {
            return 0;
        }
        return fields.stream().filter(this::isPublicSchedulingSlotField).count();
    }

    private boolean isPublicSchedulingSlotField(PublicFormFieldResponse field) {
        return field != null && "scheduling_page".equalsIgnoreCase(field.uiComponent());
    }

    private void removeFieldFromPages(Form form, Long fieldId) {
        var document = formMapper.parsePagesDocument(form.getPagesJson());
        List<FormPageDto> pages = document.pages() == null ? List.of() : document.pages();
        String fieldToken = "field:" + fieldId;
        List<FormPageDto> nextPages = pages.stream()
                .map(page -> {
                    List<Long> fieldIds = page.fieldIds() == null
                            ? List.of()
                            : page.fieldIds().stream().filter(id -> !fieldId.equals(id)).toList();
                    List<String> canvasOrder = page.canvasOrder() == null
                            ? null
                            : page.canvasOrder().stream()
                                    .filter(token -> token == null || !token.equals(fieldToken))
                                    .toList();
                    return new FormPageDto(
                            page.id(),
                            page.type(),
                            page.title(),
                            page.navLabel(),
                            page.description(),
                            fieldIds,
                            page.buttonText(),
                            page.headerImage(),
                            page.coverLayoutMedia(),
                            page.coverImagePosition(),
                            page.customCoverLayout(),
                            page.endingConfig(),
                            page.reviewConfig(),
                            page.loginConfig(),
                            page.paymentConfig(),
                            page.contentBlocks(),
                            canvasOrder,
                            page.progressStepId());
                })
                .toList();
        form.setPagesJson(formMapper.serializePagesDocument(nextPages, document.progressBar()));
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
                submission.getRespondentEmail(),
                answers);
    }

    private static String duplicateTitle(String title) {
        String base = title == null || title.isBlank() ? "Formulaire" : title.trim();
        String suffix = " (copie)";
        if (base.length() + suffix.length() <= 255) {
            return base + suffix;
        }
        return base.substring(0, Math.max(0, 255 - suffix.length())) + suffix;
    }

    private FormField cloneField(FormField source) {
        return FormField.builder()
                .label(source.getLabel())
                .fieldType(source.getFieldType())
                .required(source.isRequired())
                .displayOrder(source.getDisplayOrder())
                .optionsJson(source.getOptionsJson())
                .placeholder(source.getPlaceholder())
                .uiComponent(source.getUiComponent())
                .settingsJson(source.getSettingsJson())
                .build();
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

    private void requireFormView(Long workspaceId, Long userId) {
        if (isPlatformAdmin(userId)) {
            authorizationService.requireExistingWorkspace(workspaceId);
            return;
        }
        authorizationService.requireCanView(workspaceId, userId);
    }

    private void requireFormWrite(Long workspaceId, Long userId) {
        if (isPlatformAdmin(userId)) {
            authorizationService.requireExistingWorkspace(workspaceId);
            return;
        }
        authorizationService.requireCanUpdateWorkspace(workspaceId, userId);
    }

    private boolean isPlatformAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> user.getRole() == Role.ROLE_ADMIN)
                .orElse(false);
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
