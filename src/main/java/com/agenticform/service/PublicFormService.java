package com.agenticform.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.FormSessionResponse;
import com.agenticform.dto.PublicFormFieldResponse;
import com.agenticform.dto.PublicFormResponse;
import com.agenticform.dto.SubmissionAnswerRequest;
import com.agenticform.dto.SubmissionResponse;
import com.agenticform.dto.SubmitFormRequest;
import com.agenticform.dto.UpsertFormSessionRequest;
import com.agenticform.exception.FormNotAvailableException;
import com.agenticform.exception.FormNotFoundException;
import com.agenticform.exception.InvalidSubmissionException;
import com.agenticform.model.entity.FieldType;
import com.agenticform.model.entity.Form;
import com.agenticform.model.entity.FormField;
import com.agenticform.model.entity.FormSession;
import com.agenticform.model.entity.FormSessionStatus;
import com.agenticform.model.entity.FormStatus;
import com.agenticform.model.entity.FormSubmission;
import com.agenticform.model.entity.FormSubmissionAnswer;
import com.agenticform.repository.FormRepository;
import com.agenticform.repository.FormSessionRepository;
import com.agenticform.repository.FormSubmissionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PublicFormService {

    private final FormRepository formRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final FormSessionRepository formSessionRepository;
    private final FormMapper formMapper;
    private final ObjectMapper objectMapper;

    public PublicFormService(
            FormRepository formRepository,
            FormSubmissionRepository formSubmissionRepository,
            FormSessionRepository formSessionRepository,
            FormMapper formMapper,
            ObjectMapper objectMapper) {
        this.formRepository = formRepository;
        this.formSubmissionRepository = formSubmissionRepository;
        this.formSessionRepository = formSessionRepository;
        this.formMapper = formMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PublicFormResponse getPublishedForm(Long formId) {
        Form form = requirePublishedForm(formId);
        formRepository.incrementViewCount(formId);
        List<PublicFormFieldResponse> fields = form.getFields().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(this::toPublicField)
                .toList();
        return new PublicFormResponse(
                form.getId(),
                form.getTitle(),
                form.getDescription(),
                form.getStatus().name(),
                fields,
                formMapper.parseLogicRules(form.getLogicRulesJson()),
                formMapper.parseCalculations(form.getCalculationsJson()),
                formMapper.parsePages(form.getPagesJson()),
                form.getUpdatedAt());
    }

    @Transactional
    public FormSessionResponse upsertSession(Long formId, UpsertFormSessionRequest request) {
        Form form = requirePublishedForm(formId);
        String sessionId = request.sessionId().trim();
        if (sessionId.isEmpty()) {
            throw new InvalidSubmissionException("Identifiant de session invalide.");
        }

        Map<Long, FormField> fieldsById = indexFields(form);
        FormSession session = formSessionRepository.findById(sessionId).orElse(null);

        if (session != null && session.getForm().getId() != null
                && !session.getForm().getId().equals(formId)) {
            throw new InvalidSubmissionException("Cette session appartient à un autre formulaire.");
        }

        FormSessionStatus requestedStatus = parseSessionStatus(request.status());
        if (session != null && session.getStatus() == FormSessionStatus.COMPLETED
                && requestedStatus != FormSessionStatus.COMPLETED) {
            return toSessionResponse(session);
        }

        Map<Long, String> answers = new LinkedHashMap<>();
        if (session != null) {
            answers.putAll(formMapper.parseAnswerMap(session.getAnswersJson()));
        }
        // Appliquer le snapshot client (null / vide = suppression de la réponse partielle).
        if (request.answers() != null) {
            for (SubmissionAnswerRequest answer : request.answers()) {
                if (answer == null || answer.fieldId() == null) {
                    continue;
                }
                FormField field = fieldsById.get(answer.fieldId());
                if (field == null) {
                    continue;
                }
                String normalized = normalizeValue(field, answer.value());
                if (normalized == null || normalized.isBlank()) {
                    answers.remove(field.getId());
                    continue;
                }
                try {
                    validateValue(field, normalized);
                    answers.put(field.getId(), normalized);
                } catch (InvalidSubmissionException ignored) {
                    // conserve la valeur précédente valide pour ce champ
                }
            }
        }

        Long lastFieldId = resolveLastFieldId(fieldsById, request.lastFieldId());

        if (session == null) {
            session = FormSession.builder()
                    .sessionId(sessionId)
                    .form(form)
                    .build();
        }

        session.setLastFieldId(lastFieldId);
        session.setAnswersJson(serializeAnswers(answers));
        if (requestedStatus != null) {
            session.setStatus(requestedStatus);
        } else if (session.getStatus() == null
                || session.getStatus() == FormSessionStatus.ABANDONED) {
            session.setStatus(FormSessionStatus.IN_PROGRESS);
        }

        FormSession saved = formSessionRepository.save(session);
        return toSessionResponse(saved);
    }

    @Transactional
    public SubmissionResponse submit(Long formId, SubmitFormRequest request) {
        Form form = requirePublishedForm(formId);
        Map<Long, FormField> fieldsById = indexFields(form);

        Map<Long, String> valuesByFieldId = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (SubmissionAnswerRequest answer : request.answers()) {
            if (answer.fieldId() == null) {
                throw new InvalidSubmissionException("Chaque réponse doit référencer un champ.");
            }
            if (!seen.add(answer.fieldId())) {
                throw new InvalidSubmissionException("Réponse en double pour un même champ.");
            }
            FormField field = fieldsById.get(answer.fieldId());
            if (field == null) {
                throw new InvalidSubmissionException("Champ inconnu pour ce formulaire.");
            }
            String normalized = normalizeValue(field, answer.value());
            validateValue(field, normalized);
            valuesByFieldId.put(field.getId(), normalized);
        }

        for (FormField field : form.getFields()) {
            if (field.isRequired()) {
                String value = valuesByFieldId.get(field.getId());
                if (value == null || value.isBlank()) {
                    throw new InvalidSubmissionException(
                            "Le champ « " + field.getLabel() + " » est obligatoire.");
                }
            }
        }

        FormSubmission submission = FormSubmission.builder().form(form).build();
        for (Map.Entry<Long, String> entry : valuesByFieldId.entrySet()) {
            FormField field = fieldsById.get(entry.getKey());
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            submission.addAnswer(FormSubmissionAnswer.builder()
                    .field(field)
                    .valueText(value)
                    .build());
        }

        FormSubmission saved = formSubmissionRepository.save(submission);

        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            completeSession(form, request.sessionId().trim(), valuesByFieldId);
        }

        return new SubmissionResponse(saved.getId(), form.getId(), saved.getSubmittedAt());
    }

    private void completeSession(Form form, String sessionId, Map<Long, String> answers) {
        FormSession session = formSessionRepository.findById(sessionId).orElseGet(() ->
                FormSession.builder()
                        .sessionId(sessionId)
                        .form(form)
                        .build());
        if (session.getForm() == null) {
            session.setForm(form);
        }
        if (session.getForm().getId() != null && !session.getForm().getId().equals(form.getId())) {
            return;
        }
        Long lastFieldId = form.getFields().stream()
                .sorted((a, b) -> Integer.compare(b.getDisplayOrder(), a.getDisplayOrder()))
                .map(FormField::getId)
                .findFirst()
                .orElse(null);
        session.setLastFieldId(lastFieldId);
        session.setAnswersJson(serializeAnswers(answers));
        session.setStatus(FormSessionStatus.COMPLETED);
        formSessionRepository.save(session);
    }

    private Form requirePublishedForm(Long formId) {
        Form form = formRepository.findByIdWithFields(formId)
                .orElseThrow(() -> new FormNotFoundException(formId));
        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new FormNotAvailableException(formId);
        }
        return form;
    }

    private Map<Long, FormField> indexFields(Form form) {
        Map<Long, FormField> fieldsById = new HashMap<>();
        for (FormField field : form.getFields()) {
            fieldsById.put(field.getId(), field);
        }
        return fieldsById;
    }

    private Long resolveLastFieldId(Map<Long, FormField> fieldsById, Long lastFieldId) {
        if (lastFieldId == null) {
            return null;
        }
        return fieldsById.containsKey(lastFieldId) ? lastFieldId : null;
    }

    private FormSessionStatus parseSessionStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FormSessionStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidSubmissionException("Statut de session invalide.");
        }
    }

    private String serializeAnswers(Map<Long, String> answers) {
        Map<String, String> asStrings = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : answers.entrySet()) {
            asStrings.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        try {
            return objectMapper.writeValueAsString(asStrings);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Impossible de sérialiser les réponses partielles.", ex);
        }
    }

    private FormSessionResponse toSessionResponse(FormSession session) {
        return new FormSessionResponse(
                session.getSessionId(),
                session.getForm().getId(),
                session.getLastFieldId(),
                session.getStatus().name(),
                formMapper.parseAnswerMap(session.getAnswersJson()),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private PublicFormFieldResponse toPublicField(FormField field) {
        return new PublicFormFieldResponse(
                field.getId(),
                field.getLabel(),
                field.getFieldType().name(),
                field.isRequired(),
                field.getDisplayOrder(),
                formMapper.parseOptions(field.getOptionsJson()),
                field.getPlaceholder(),
                field.getUiComponent());
    }

    private String normalizeValue(FormField field, String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (field.getFieldType() == FieldType.EMAIL) {
            return trimmed.toLowerCase();
        }
        return trimmed;
    }

    private void validateValue(FormField field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        FieldType type = field.getFieldType();
        if (type == FieldType.EMAIL && !value.matches("(?i)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new InvalidSubmissionException(
                    "Adresse e-mail invalide pour « " + field.getLabel() + " ».");
        }
        if (type == FieldType.NUMBER) {
            try {
                Double.parseDouble(value.replace(',', '.'));
            } catch (NumberFormatException ex) {
                throw new InvalidSubmissionException(
                        "Valeur numérique invalide pour « " + field.getLabel() + " ».");
            }
        }
        if (type == FieldType.RATING) {
            try {
                int rating = Integer.parseInt(value);
                if (rating < 1 || rating > 5) {
                    throw new InvalidSubmissionException(
                            "La note doit être entre 1 et 5 pour « " + field.getLabel() + " ».");
                }
            } catch (NumberFormatException ex) {
                throw new InvalidSubmissionException(
                        "Note invalide pour « " + field.getLabel() + " ».");
            }
        }
        if (formMapper.requiresOptions(type)) {
            List<String> options = formMapper.parseOptions(field.getOptionsJson());
            if (type == FieldType.MULTIPLE_CHOICE || type == FieldType.CHECKBOX) {
                String[] parts = value.split("\\s*,\\s*");
                for (String part : parts) {
                    if (!options.contains(part)) {
                        throw new InvalidSubmissionException(
                                "Option invalide pour « " + field.getLabel() + " ».");
                    }
                }
            } else if (!options.contains(value)) {
                throw new InvalidSubmissionException(
                        "Option invalide pour « " + field.getLabel() + " ».");
            }
        }
    }
}
