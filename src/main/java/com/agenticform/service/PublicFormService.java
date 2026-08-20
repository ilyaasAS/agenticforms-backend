package com.agenticform.service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.agenticform.dto.FormGoogleLoginExchangeRequest;
import com.agenticform.dto.FormLoginAbandonProgressRequest;
import com.agenticform.dto.FormLoginPasswordVerifyRequest;
import com.agenticform.dto.FormLoginPasswordVerifyResponse;
import com.agenticform.dto.FormLoginResumeStatusRequest;
import com.agenticform.dto.FormLoginResumeStatusResponse;
import com.agenticform.dto.FormLoginSendCodeRequest;
import com.agenticform.dto.FormLoginVerifyRequest;
import com.agenticform.dto.FormLoginVerifyResponse;
import com.agenticform.dto.FormPageDto;
import com.agenticform.dto.FormSessionResponse;
import com.agenticform.dto.GuestInvite;
import com.agenticform.dto.GoogleCalendarAvailabilityRequest;
import com.agenticform.util.GuestInviteParser;
import com.agenticform.dto.GoogleCalendarAvailabilityResponse;
import com.agenticform.dto.GoogleCalendarBookRequest;
import com.agenticform.dto.GoogleCalendarBookResponse;
import com.agenticform.dto.LoginConfigDto;
import com.agenticform.dto.PagesDocumentDto;
import com.agenticform.dto.PublicFormFieldResponse;
import com.agenticform.dto.PublicFormResponse;
import com.agenticform.dto.PublicPickerFieldValueDto;
import com.agenticform.dto.PublicPickerRecordDto;
import com.agenticform.dto.PublicSchedulingEventResponse;
import com.agenticform.dto.PublicPickerRecordsResponse;
import com.agenticform.dto.SubmissionAnswerRequest;
import com.agenticform.dto.SubmissionResponse;
import com.agenticform.dto.SubmitFormRequest;
import com.agenticform.dto.UpsertFormSessionRequest;
import com.agenticform.dto.FieldSettingsDto;
import com.agenticform.dto.TableColumnDto;
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
import com.agenticform.model.entity.User;
import com.agenticform.repository.FormRepository;
import com.agenticform.repository.FormSessionRepository;
import com.agenticform.repository.FormSubmissionRepository;
import com.agenticform.repository.UserRepository;
import com.agenticform.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PublicFormService {

    private final FormRepository formRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final FormSessionRepository formSessionRepository;
    private final UserRepository userRepository;
    private final FormMapper formMapper;
    private final ObjectMapper objectMapper;
    private final RecaptchaService recaptchaService;
    private final GoogleCalendarIntegrationService googleCalendarIntegrationService;
    private final EmailService emailService;
    private final FormService formService;
    private final FormLoginService formLoginService;
    private final FormGoogleLoginService formGoogleLoginService;
    private final LoginConfigSupport loginConfigSupport;

    public PublicFormService(
            FormRepository formRepository,
            FormSubmissionRepository formSubmissionRepository,
            FormSessionRepository formSessionRepository,
            UserRepository userRepository,
            FormMapper formMapper,
            ObjectMapper objectMapper,
            RecaptchaService recaptchaService,
            GoogleCalendarIntegrationService googleCalendarIntegrationService,
            EmailService emailService,
            FormService formService,
            FormLoginService formLoginService,
            FormGoogleLoginService formGoogleLoginService,
            LoginConfigSupport loginConfigSupport) {
        this.formRepository = formRepository;
        this.formSubmissionRepository = formSubmissionRepository;
        this.formSessionRepository = formSessionRepository;
        this.userRepository = userRepository;
        this.formMapper = formMapper;
        this.objectMapper = objectMapper;
        this.recaptchaService = recaptchaService;
        this.googleCalendarIntegrationService = googleCalendarIntegrationService;
        this.emailService = emailService;
        this.formService = formService;
        this.formLoginService = formLoginService;
        this.formGoogleLoginService = formGoogleLoginService;
        this.loginConfigSupport = loginConfigSupport;
    }

    @Transactional(readOnly = true)
    public void sendLoginCode(Long formId, FormLoginSendCodeRequest request) {
        Form form = requirePublishedForm(formId);
        formLoginService.sendCode(form, request);
    }

    @Transactional(readOnly = true)
    public FormLoginVerifyResponse verifyLoginCode(Long formId, FormLoginVerifyRequest request) {
        Form form = requirePublishedForm(formId);
        return formLoginService.verify(form, request);
    }

    @Transactional(readOnly = true)
    public FormLoginPasswordVerifyResponse verifyLoginPassword(
            Long formId,
            FormLoginPasswordVerifyRequest request) {
        Form form = requirePublishedForm(formId);
        return formLoginService.verifyPassword(form, request);
    }

    @Transactional(readOnly = true)
    public String buildGoogleLoginAuthorizeUrl(Long formId) {
        return formGoogleLoginService.buildAuthorizeUrl(formId);
    }

    @Transactional(readOnly = true)
    public String handleGoogleLoginCallback(String code, String state, String error) {
        return formGoogleLoginService.handleCallback(code, state, error);
    }

    @Transactional(readOnly = true)
    public FormLoginVerifyResponse exchangeGoogleLogin(Long formId, FormGoogleLoginExchangeRequest request) {
        return formGoogleLoginService.exchange(formId, request.code());
    }

    @Transactional(readOnly = true)
    public FormLoginResumeStatusResponse resumeStatus(Long formId, FormLoginResumeStatusRequest request) {
        Form form = requirePublishedForm(formId);
        LoginConfigDto loginConfig = loginConfigForForm(form);
        if (loginConfigSupport.isPasswordMode(loginConfig)) {
            return emptyResumeStatus();
        }
        String email = normalizeRespondentEmail(request.email());
        if (email == null) {
            return emptyResumeStatus();
        }
        if (loginConfig == null) {
            return emptyResumeStatus();
        }

        boolean allowEdit = Boolean.TRUE.equals(loginConfig.allowEditResponses());
        boolean singleLimit = Boolean.TRUE.equals(loginConfig.singleSubmissionLimit());
        String limitTitle = resolveLimitTitle(loginConfig);
        String limitSubtitle = resolveLimitSubtitle(loginConfig);

        FormSession inProgress = formSessionRepository
                .findFirstByForm_IdAndRespondentEmailIgnoreCaseAndStatusOrderByUpdatedAtDesc(
                        formId, email, FormSessionStatus.IN_PROGRESS)
                .orElse(null);

        FormSubmission completed = formSubmissionRepository
                .findFirstByForm_IdAndRespondentEmailIgnoreCaseOrderBySubmittedAtDesc(formId, email)
                .orElse(null);
        if (completed != null) {
            completed = formSubmissionRepository.findByIdWithAnswers(completed.getId()).orElse(completed);
        }

        boolean hasInProgress = inProgress != null && hasMeaningfulSessionAnswers(inProgress);
        boolean hasCompleted = completed != null;
        boolean allowNewSubmission = !singleLimit || !hasCompleted;
        boolean submissionBlocked = singleLimit && hasCompleted && !hasInProgress && !allowEdit;
        boolean showResumePrompt =
                hasInProgress || (allowEdit && hasCompleted && !submissionBlocked);

        Map<Long, String> inProgressAnswers = hasInProgress
                ? formMapper.parseAnswerMap(inProgress.getAnswersJson())
                : Map.of();
        Map<Long, String> completedAnswers = hasCompleted && completed.getAnswers() != null
                ? submissionAnswersToMap(completed)
                : Map.of();

        return new FormLoginResumeStatusResponse(
                showResumePrompt,
                submissionBlocked,
                singleLimit,
                allowEdit,
                allowNewSubmission,
                limitTitle,
                limitSubtitle,
                hasInProgress,
                hasInProgress ? inProgress.getSessionId() : null,
                hasInProgress ? inProgress.getLastFieldId() : null,
                hasInProgress ? inProgress.getUpdatedAt() : null,
                inProgressAnswers,
                hasCompleted,
                hasCompleted ? completed.getId() : null,
                hasCompleted ? completed.getSubmittedAt() : null,
                completedAnswers);
    }

    @Transactional
    public void abandonLoginProgress(Long formId, FormLoginAbandonProgressRequest request) {
        requirePublishedForm(formId);
        String email = normalizeRespondentEmail(request.email());
        if (email == null) {
            return;
        }
        List<FormSession> sessions = formSessionRepository
                .findAllByForm_IdAndRespondentEmailIgnoreCaseAndStatus(
                        formId, email, FormSessionStatus.IN_PROGRESS);
        for (FormSession session : sessions) {
            session.setStatus(FormSessionStatus.ABANDONED);
        }
        formSessionRepository.saveAll(sessions);
    }

    @Transactional
    public PublicFormResponse getPublishedForm(Long formId) {
        Form form = requirePublishedForm(formId);
        formService.pruneOrphanSchedulingSlots(form);
        formRepository.incrementViewCount(formId);
        List<PublicFormFieldResponse> fields = form.getFields().stream()
                .filter(field -> !field.isDeleted())
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
                loginConfigSupport.sanitizePagesForClient(formMapper.parsePages(form.getPagesJson())),
                form.getThemeId() != null && !form.getThemeId().isBlank() ? form.getThemeId() : "dark",
                formMapper.parseProgressBar(form.getPagesJson()),
                form.getUpdatedAt());
    }

    @Transactional
    public GoogleCalendarAvailabilityResponse schedulingAvailability(
            Long formId,
            GoogleCalendarAvailabilityRequest request) {
        Form form = requirePublishedForm(formId);
        Long ownerId = googleCalendarIntegrationService.ownerUserIdForPublishedForm(form);
        return googleCalendarIntegrationService.computeAvailability(ownerId, request);
    }

    @Transactional
    public GoogleCalendarBookResponse schedulingBook(Long formId, GoogleCalendarBookRequest request) {
        Form form = requirePublishedForm(formId);
        Long ownerId = googleCalendarIntegrationService.ownerUserIdForPublishedForm(form);
        return googleCalendarIntegrationService.createBooking(ownerId, request, formId, false);
    }

    @Transactional
    public GoogleCalendarBookResponse schedulingModify(Long formId, String oldEventId, GoogleCalendarBookRequest request) {
        Form form = requirePublishedForm(formId);
        Long ownerId = googleCalendarIntegrationService.ownerUserIdForPublishedForm(form);

        GoogleCalendarBookRequest modifiedRequest = new GoogleCalendarBookRequest(
                request.date(), request.startTime(),
                request.guestEmail(), request.guestName(),
                request.guestInvites(),
                request.title(), request.description(),
                request.location(), request.locationType(),
                request.sendCalendarInvite(), request.reminderMinutes(),
                oldEventId,
                request.availability());
        GoogleCalendarBookResponse response = googleCalendarIntegrationService.createBooking(ownerId, modifiedRequest, formId, false);

        try {
            googleCalendarIntegrationService.sendBookingNotification(
                    formId, ownerId,
                    request.guestEmail(), request.guestName(),
                    request.title(), response.startLabel(),
                    response.htmlLink(), response.eventId(),
                    request.guestInvites());
        } catch (Exception ignored) {
        }

        return response;
    }

    @Transactional(readOnly = true)
    public PublicSchedulingEventResponse schedulingGetEvent(Long formId, String eventId, String calendarId) {
        Form form = requirePublishedForm(formId);
        Long ownerId = googleCalendarIntegrationService.ownerUserIdForPublishedForm(form);
        var event = googleCalendarIntegrationService.getEvent(ownerId, eventId, calendarId);
        return googleCalendarIntegrationService.parsePublicSchedulingEvent(event);
    }

    public void schedulingCancel(Long formId, String eventId, String calendarId) {
        Form form = requirePublishedForm(formId);
        Long ownerId = googleCalendarIntegrationService.ownerUserIdForPublishedForm(form);

        var event = googleCalendarIntegrationService.getEvent(ownerId, eventId, calendarId);
        String title = event != null ? event.path("summary").asText("Rendez-vous") : "Rendez-vous";
        String guestEmail = null;
        String guestName = null;
        if (event != null) {
            var attendees = event.path("attendees");
            if (attendees.isArray() && !attendees.isEmpty()) {
                guestEmail = attendees.get(0).path("email").asText(null);
                guestName = attendees.get(0).path("displayName").asText(null);
            }
        }

        googleCalendarIntegrationService.cancelBooking(ownerId, eventId, calendarId);

        try {
            if (guestEmail != null && !guestEmail.isBlank()) {
                emailService.sendBookingCancellationEmail(guestEmail, guestName, title);
            }
            User owner = userRepository.findById(ownerId).orElse(null);
            if (owner != null && owner.getEmail() != null) {
                emailService.sendCancellationNotificationToOrganizer(
                        owner.getEmail(), owner.getFullName(),
                        guestName, guestEmail, title);
            }
        } catch (Exception ignored) {
        }
    }

    @Transactional(readOnly = true)
    public PublicPickerRecordsResponse listPickerRecords(Long formId, Long fieldId) {
        Form form = requirePublishedForm(formId);
        FormField pickerField = form.getFields().stream()
                .filter(field -> !field.isDeleted() && Objects.equals(field.getId(), fieldId))
                .findFirst()
                .orElseThrow(() -> new FormNotFoundException(formId));
        if (pickerField.getFieldType() != FieldType.SUBMISSION_PICKER) {
            throw new InvalidSubmissionException("Ce champ n’est pas un sélecteur de soumissions.");
        }

        FieldSettingsDto settings = formMapper.parseFieldSettings(pickerField.getSettingsJson());
        List<Long> linkedFormIds = settings != null && settings.linkedFormIds() != null
                ? settings.linkedFormIds().stream().filter(Objects::nonNull).distinct().toList()
                : List.of();
        if (linkedFormIds.isEmpty()) {
            return new PublicPickerRecordsResponse(List.of());
        }

        Long workspaceId = form.getWorkspace().getId();
        Long primaryFieldId = settings != null ? settings.primaryFieldId() : null;
        List<Long> displayFieldIds = settings != null && settings.displayFieldIds() != null
                ? settings.displayFieldIds().stream().filter(Objects::nonNull).distinct().toList()
                : List.of();
        Long sortFieldId = settings != null ? settings.pickerSortFieldId() : null;
        boolean sortDesc = settings != null
                && settings.pickerSortDirection() != null
                && "desc".equalsIgnoreCase(settings.pickerSortDirection().trim());

        record SortablePicker(PublicPickerRecordDto record, String sortKey) {}

        List<SortablePicker> sortable = new ArrayList<>();
        for (Long linkedFormId : linkedFormIds) {
            if (!formRepository.existsByIdAndWorkspaceId(linkedFormId, workspaceId)) {
                continue;
            }
            Form linked = formRepository.findByIdWithFields(linkedFormId).orElse(null);
            if (linked == null) {
                continue;
            }
            Map<Long, FormField> linkedFields = indexFields(linked);
            List<FormSubmission> submissions = formSubmissionRepository
                    .findAllByFormIdWithAnswers(linkedFormId);
            for (FormSubmission submission : submissions) {
                Map<Long, String> answers = new HashMap<>();
                for (FormSubmissionAnswer answer : submission.getAnswers()) {
                    if (answer.getField() == null || answer.getField().getId() == null) {
                        continue;
                    }
                    answers.put(
                            answer.getField().getId(),
                            answer.getValueText() != null ? answer.getValueText() : "");
                }
                String label = resolvePickerLabel(
                        answers, primaryFieldId, linkedFields, submission.getId());
                List<PublicPickerFieldValueDto> extra = new ArrayList<>();
                for (Long displayId : displayFieldIds) {
                    FormField displayField = linkedFields.get(displayId);
                    if (displayField == null) {
                        continue;
                    }
                    String raw = answers.getOrDefault(displayId, "");
                    if (raw == null || raw.isBlank()) {
                        continue;
                    }
                    extra.add(new PublicPickerFieldValueDto(displayField.getLabel(), raw));
                }
                String sortKey = label;
                if (sortFieldId != null) {
                    String fromSort = answers.get(sortFieldId);
                    if (fromSort != null && !fromSort.isBlank()) {
                        sortKey = fromSort.trim();
                    }
                }
                sortable.add(new SortablePicker(
                        new PublicPickerRecordDto(
                                submission.getId(),
                                linkedFormId,
                                label,
                                extra),
                        sortKey != null ? sortKey : ""));
            }
        }

        sortable.sort((a, b) -> {
            int cmp = a.sortKey().compareToIgnoreCase(b.sortKey());
            return sortDesc ? -cmp : cmp;
        });

        return new PublicPickerRecordsResponse(
                sortable.stream().map(SortablePicker::record).toList());
    }

    private String resolvePickerLabel(
            Map<Long, String> answers,
            Long primaryFieldId,
            Map<Long, FormField> linkedFields,
            Long submissionId) {
        if (primaryFieldId != null) {
            String primary = answers.get(primaryFieldId);
            if (primary != null && !primary.isBlank()) {
                return primary.trim();
            }
        }
        for (FormField field : linkedFields.values().stream()
                .sorted(Comparator.comparingInt(FormField::getDisplayOrder))
                .toList()) {
            String value = answers.get(field.getId());
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "Soumission #" + submissionId;
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
        String respondentEmail = normalizeRespondentEmail(request.respondentEmail());
        if (respondentEmail != null) {
            session.setRespondentEmail(respondentEmail);
        }
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
        boolean loginRequired = formHasLoginPage(form);
        LoginConfigDto loginConfig = loginConfigForForm(form);
        boolean passwordLogin = loginConfigSupport.isPasswordMode(loginConfig);
        String respondentEmail = normalizeRespondentEmail(request.respondentEmail());
        if (loginRequired && !passwordLogin && (respondentEmail == null || respondentEmail.isBlank())) {
            throw new InvalidSubmissionException(
                    "Connexion requise : vérifiez votre e-mail avant d’envoyer le formulaire.");
        }

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
            if (field.getFieldType() == FieldType.CAPTCHA) {
                if (normalized == null || normalized.isBlank()) {
                    continue;
                }
                if (!recaptchaService.verify(normalized)) {
                    throw new InvalidSubmissionException(
                            "Captcha invalide pour « " + field.getLabel() + " ».");
                }
                // Ne pas stocker le token Google (à usage unique) — seulement le statut.
                valuesByFieldId.put(field.getId(), "verified");
            } else {
                valuesByFieldId.put(field.getId(), normalized);
            }
        }

        Map<String, String> answersByString = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : valuesByFieldId.entrySet()) {
            answersByString.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : entry.getValue());
        }
        Map<String, String> urlParams = request.urlParams() != null ? request.urlParams() : Map.of();
        Map<String, String> contactParams = resolveContactFromAuth();
        Map<String, String> calcValues = CalculationEvaluator.evaluate(
                formMapper.parseCalculations(form.getCalculationsJson()),
                answersByString,
                urlParams,
                contactParams
        );

        Set<Long> assignedSchedulingFieldIds = assignedSchedulingFieldIds(form);

        for (FormField field : form.getFields()) {
            if (field.isDeleted()) {
                continue;
            }
            if (field.isRequired()) {
                if (isOrphanSchedulingSlot(form, field, assignedSchedulingFieldIds)) {
                    continue;
                }
                FieldSettingsDto settings = formMapper.parseFieldSettings(field.getSettingsJson());
                if (!VisibilityLogicEvaluator.isFieldVisible(settings, answersByString, urlParams, contactParams, calcValues)) {
                    continue;
                }
                String value = valuesByFieldId.get(field.getId());
                if (value == null || value.isBlank() || isEmptyPhoneAnswer(field, value)) {
                    throw new InvalidSubmissionException(
                            "Le champ « " + field.getLabel() + " » est obligatoire.");
                }
            }
        }

        rejectPastSchedulingBookings(valuesByFieldId, fieldsById);

        if (loginRequired && !passwordLogin && respondentEmail != null && loginConfig != null
                && Boolean.TRUE.equals(loginConfig.singleSubmissionLimit())) {
            FormSubmission existing = formSubmissionRepository
                    .findFirstByForm_IdAndRespondentEmailIgnoreCaseOrderBySubmittedAtDesc(
                            formId, respondentEmail)
                    .orElse(null);
            if (existing != null) {
                if (Boolean.TRUE.equals(loginConfig.allowEditResponses())) {
                    existing = formSubmissionRepository.findByIdWithAnswers(existing.getId())
                            .orElse(existing);
                    FormSubmission saved = replaceSubmissionAnswers(
                            existing, fieldsById, valuesByFieldId, respondentEmail);
                    if (request.sessionId() != null && !request.sessionId().isBlank()) {
                        completeSession(form, request.sessionId().trim(), valuesByFieldId, respondentEmail);
                    }
                    sendSchedulingNotificationIfNeeded(form, formId, fieldsById, valuesByFieldId);
                    return new SubmissionResponse(saved.getId(), form.getId(), saved.getSubmittedAt());
                }
                throw new InvalidSubmissionException(resolveLimitTitle(loginConfig));
            }
        }

        FormSubmission submission = FormSubmission.builder()
                .form(form)
                .respondentEmail(respondentEmail)
                .build();
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
            completeSession(form, request.sessionId().trim(), valuesByFieldId, respondentEmail);
        }

        sendSchedulingNotificationIfNeeded(form, formId, fieldsById, valuesByFieldId);

        return new SubmissionResponse(saved.getId(), form.getId(), saved.getSubmittedAt());
    }

    private void sendSchedulingNotificationIfNeeded(Form form, Long formId,
                                                     Map<Long, FormField> fieldsById,
                                                     Map<Long, String> valuesByFieldId) {
        try {
            Long ownerId = googleCalendarIntegrationService.ownerUserIdForPublishedForm(form);
            for (Map.Entry<Long, String> entry : valuesByFieldId.entrySet()) {
                FormField field = fieldsById.get(entry.getKey());
                if (field == null || !isSchedulingSlotField(field)) continue;
                String raw = entry.getValue();
                if (raw == null || raw.isBlank()) continue;
                try {
                    var node = objectMapper.readTree(raw);
                    String guestEmail = node.path("guestEmail").asText(null);
                    String guestName = node.path("guestName").asText(null);
                    String title = node.path("title").asText("Rendez-vous");
                    String startLabel = node.path("startLabel").asText("");
                    String htmlLink = node.path("htmlLink").asText(null);
                    String eventId = node.path("eventId").asText(null);
                    List<GuestInvite> invitedGuests = GuestInviteParser.fromJsonNode(node.path("guestInvites"));
                    if (guestEmail != null && !guestEmail.isBlank()) {
                        googleCalendarIntegrationService.sendBookingNotification(
                                formId, ownerId, guestEmail, guestName,
                                title, startLabel, htmlLink, eventId, invitedGuests);
                    }
                } catch (JsonProcessingException ignored) {
                }
                break;
            }
        } catch (Exception ignored) {
        }
    }

    private void completeSession(
            Form form,
            String sessionId,
            Map<Long, String> answers,
            String respondentEmail) {
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
                .filter(field -> !field.isDeleted())
                .sorted((a, b) -> Integer.compare(b.getDisplayOrder(), a.getDisplayOrder()))
                .map(FormField::getId)
                .findFirst()
                .orElse(null);
        session.setLastFieldId(lastFieldId);
        session.setAnswersJson(serializeAnswers(answers));
        if (respondentEmail != null) {
            session.setRespondentEmail(respondentEmail);
        }
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

    private Set<Long> assignedSchedulingFieldIds(Form form) {
        Set<Long> ids = new HashSet<>();
        for (var page : formMapper.parsePages(form.getPagesJson())) {
            if (page == null || !"scheduling".equalsIgnoreCase(page.type()) || page.fieldIds() == null) {
                continue;
            }
            for (Long fieldId : page.fieldIds()) {
                if (fieldId != null) {
                    ids.add(fieldId);
                }
            }
        }
        return ids;
    }

    private boolean isOrphanSchedulingSlot(Form form, FormField field, Set<Long> assignedSchedulingFieldIds) {
        if (!isSchedulingSlotField(field)) {
            return false;
        }
        return !assignedSchedulingFieldIds.contains(field.getId());
    }

    private boolean isSchedulingSlotField(FormField field) {
        if (field == null) {
            return false;
        }
        if ("scheduling_page".equalsIgnoreCase(field.getUiComponent())) {
            return true;
        }
        String settings = field.getSettingsJson();
        return settings != null && settings.contains("\"schedulingSlot\":true");
    }

    private void rejectPastSchedulingBookings(Map<Long, String> valuesByFieldId, Map<Long, FormField> fieldsById) {
        Instant now = Instant.now();
        for (Map.Entry<Long, String> entry : valuesByFieldId.entrySet()) {
            FormField field = fieldsById.get(entry.getKey());
            if (field == null || !isSchedulingSlotField(field)) {
                continue;
            }
            String raw = entry.getValue();
            if (raw == null || raw.isBlank() || !raw.trim().startsWith("{")) {
                continue;
            }
            try {
                var node = objectMapper.readTree(raw);
                String startAt = node.path("startAt").asText(null);
                if (!StringUtils.hasText(startAt)) {
                    continue;
                }
                Instant start = Instant.parse(startAt.trim());
                if (!start.isAfter(now)) {
                    throw new InvalidSubmissionException(
                            "Ce créneau est passé. Choisissez un nouvel horaire.");
                }
            } catch (InvalidSubmissionException ex) {
                throw ex;
            } catch (Exception ignored) {
                // startAt absent / illisible : le front gère déjà le cas ; ne pas bloquer ici
            }
        }
    }

    private Map<Long, FormField> indexFields(Form form) {
        Map<Long, FormField> fieldsById = new HashMap<>();
        for (FormField field : form.getFields()) {
            if (field.isDeleted()) {
                continue;
            }
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
                field.getUiComponent(),
                formMapper.parseFieldSettings(field.getSettingsJson()));
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
        if (field.getFieldType() == FieldType.PHONE && isEmptyPhoneAnswer(field, trimmed)) {
            return null;
        }
        return trimmed;
    }

    private void validateValue(FormField field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        FieldType type = field.getFieldType();
        if (type == FieldType.CAPTCHA) {
            // Présence du token seulement ici — siteverify au submit (token à usage unique).
            if (value == null || value.isBlank() || value.length() < 20) {
                throw new InvalidSubmissionException(
                        "Captcha invalide pour « " + field.getLabel() + " ».");
            }
            return;
        }
        if (type == FieldType.EMAIL && !value.matches("(?i)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new InvalidSubmissionException(
                    "Adresse e-mail invalide pour « " + field.getLabel() + " ».");
        }
        if (type == FieldType.URL && !isValidHttpUrl(value)) {
            throw new InvalidSubmissionException(
                    "URL invalide pour « " + field.getLabel() + " ».");
        }
        if (type == FieldType.COLOR && !isValidHexColor(value)) {
            throw new InvalidSubmissionException(
                    "Couleur invalide pour « " + field.getLabel() + " ».");
        }
        if (type == FieldType.PASSWORD) {
            var settings = formMapper.parseFieldSettings(field.getSettingsJson());
            if (settings != null) {
                Integer minLen = asPositiveInt(settings.minLength());
                Integer maxLen = asPositiveInt(settings.maxLength());
                if (minLen != null && value.length() < minLen) {
                    throw new InvalidSubmissionException(
                            "Mot de passe trop court pour « " + field.getLabel() + " ».");
                }
                if (maxLen != null && value.length() > maxLen) {
                    throw new InvalidSubmissionException(
                            "Mot de passe trop long pour « " + field.getLabel() + " ».");
                }
            }
        }
        if (type == FieldType.FILE) {
            int count = countUploadedFiles(value);
            var settings = formMapper.parseFieldSettings(field.getSettingsJson());
            int maxFiles = settings != null
                    ? (asPositiveInt(settings.maxFiles()) != null
                            ? asPositiveInt(settings.maxFiles())
                            : 5)
                    : 5;
            Integer minFiles = settings != null ? asPositiveInt(settings.minFiles()) : null;
            if (minFiles != null && minFiles > 0 && count < minFiles) {
                throw new InvalidSubmissionException(
                        "Pas assez de fichiers pour « " + field.getLabel() + " ».");
            }
            if (count > maxFiles) {
                throw new InvalidSubmissionException(
                        "Trop de fichiers pour « " + field.getLabel() + " ».");
            }
            if (count == 0 && !value.isBlank() && !value.trim().startsWith("[")) {
                // Texte libre (URLs) : accepter comme une liste non vide.
            } else if (count == 0 && field.isRequired()) {
                throw new InvalidSubmissionException(
                        "Fichier requis pour « " + field.getLabel() + " ».");
            }
        }
        if (type == FieldType.SIGNATURE) {
            if (!isValidSignatureAnswer(value)) {
                throw new InvalidSubmissionException(
                        "Signature invalide pour « " + field.getLabel() + " ».");
            }
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
            var ratingSettings = formMapper.parseFieldSettings(field.getSettingsJson());
            int maxStars = 5;
            boolean allowHalf = false;
            if (ratingSettings != null) {
                if (ratingSettings.starCount() != null && ratingSettings.starCount() > 0) {
                    maxStars = Math.min(10, Math.max(1, ratingSettings.starCount()));
                }
                allowHalf = Boolean.TRUE.equals(ratingSettings.allowHalfStars());
            }
            try {
                double rating = Double.parseDouble(value.replace(',', '.'));
                double min = allowHalf ? 0.5 : 1.0;
                if (rating < min || rating > maxStars) {
                    throw new InvalidSubmissionException(
                            "La note doit être entre " + min + " et " + maxStars
                                    + " pour « " + field.getLabel() + " ».");
                }
                if (allowHalf) {
                    double doubled = rating * 2.0;
                    if (Math.abs(doubled - Math.round(doubled)) > 1e-9) {
                        throw new InvalidSubmissionException(
                                "Note invalide pour « " + field.getLabel() + " ».");
                    }
                } else if (rating != Math.rint(rating)) {
                    throw new InvalidSubmissionException(
                            "Note invalide pour « " + field.getLabel() + " ».");
                }
            } catch (NumberFormatException ex) {
                throw new InvalidSubmissionException(
                        "Note invalide pour « " + field.getLabel() + " ».");
            }
        }
        if (type == FieldType.SLIDER) {
            var sliderSettings = formMapper.parseFieldSettings(field.getSettingsJson());
            double min = 0;
            double max = 100;
            double step = 1;
            if (sliderSettings != null) {
                if (sliderSettings.sliderMin() != null) {
                    min = sliderSettings.sliderMin();
                }
                if (sliderSettings.sliderMax() != null) {
                    max = sliderSettings.sliderMax();
                }
                if (sliderSettings.sliderStep() != null && sliderSettings.sliderStep() > 0) {
                    step = sliderSettings.sliderStep();
                }
            }
            if (max <= min) {
                max = min + 1;
            }
            try {
                double sliderValue = Double.parseDouble(value.replace(',', '.'));
                if (sliderValue < min || sliderValue > max) {
                    throw new InvalidSubmissionException(
                            "La valeur doit être entre " + min + " et " + max
                                    + " pour « " + field.getLabel() + " ».");
                }
                double steps = Math.round((sliderValue - min) / step);
                double snapped = min + steps * step;
                if (Math.abs(snapped - sliderValue) > 1e-6) {
                    throw new InvalidSubmissionException(
                            "Valeur de curseur invalide pour « " + field.getLabel() + " ».");
                }
            } catch (NumberFormatException ex) {
                throw new InvalidSubmissionException(
                        "Valeur de curseur invalide pour « " + field.getLabel() + " ».");
            }
        }
        if (type == FieldType.OPINION_SCALE) {
            var opinionSettings = formMapper.parseFieldSettings(field.getSettingsJson());
            int start = 1;
            int end = 10;
            if (opinionSettings != null) {
                if (opinionSettings.scaleStart() != null) {
                    start = opinionSettings.scaleStart();
                }
                if (opinionSettings.scaleEnd() != null) {
                    end = opinionSettings.scaleEnd();
                }
            }
            if (end < start) {
                int tmp = start;
                start = end;
                end = tmp;
            }
            if (end - start > 100) {
                end = start + 100;
            }
            try {
                int opinion = Integer.parseInt(value.trim());
                if (opinion < start || opinion > end) {
                    throw new InvalidSubmissionException(
                            "La note doit être entre " + start + " et " + end
                                    + " pour « " + field.getLabel() + " ».");
                }
            } catch (NumberFormatException ex) {
                throw new InvalidSubmissionException(
                        "Note invalide pour « " + field.getLabel() + " ».");
            }
        }
        if (type == FieldType.SUBMISSION_PICKER) {
            validateSubmissionPickerAnswer(field, value);
        }
        if (type == FieldType.SUBFORM) {
            validateSubformAnswer(field, value);
        }
        if (type == FieldType.TABLE) {
            validateTableAnswer(field, value);
        }
        if (formMapper.requiresOptions(type)) {
            if (type == FieldType.CHOICE_MATRIX) {
                // Réponse JSON { ligne: colonne | colonnes[] } — validée côté structure légère.
                if (!value.trim().startsWith("{")) {
                    throw new InvalidSubmissionException(
                            "Réponse de matrice invalide pour « " + field.getLabel() + " ».");
                }
                return;
            }
            if (type == FieldType.RANKING) {
                List<String> options = formMapper.parseOptions(field.getOptionsJson());
                List<String> ranked = parseRankingAnswer(value);
                if (!isValidRankingPermutation(options, ranked)) {
                    throw new InvalidSubmissionException(
                            "Classement invalide pour « " + field.getLabel() + " ».");
                }
                return;
            }
            List<String> options = formMapper.parseOptions(field.getOptionsJson());
            if (type == FieldType.MULTIPLE_CHOICE
                    || type == FieldType.MULTISELECT
                    || (type == FieldType.PICTURE_CHOICE && value.contains(","))) {
                String[] parts = value.split("\\s*,\\s*");
                for (String part : parts) {
                    if (!isAllowedOptionValue(field, options, part)) {
                        throw new InvalidSubmissionException(
                                "Option invalide pour « " + field.getLabel() + " ».");
                    }
                }
            } else if (!isAllowedOptionValue(field, options, value)) {
                throw new InvalidSubmissionException(
                        "Option invalide pour « " + field.getLabel() + " ».");
            }
        }
    }

    private List<String> parseRankingAnswer(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("[")) {
            try {
                var node = objectMapper.readTree(trimmed);
                if (node != null && node.isArray()) {
                    List<String> ranked = new ArrayList<>();
                    for (var item : node) {
                        if (item != null && !item.isNull()) {
                            String text = item.asText();
                            if (text != null && !text.isBlank()) {
                                ranked.add(text.trim());
                            }
                        }
                    }
                    return ranked;
                }
            } catch (Exception ignored) {
                // fallback virgules
            }
        }
        if (trimmed.isBlank()) {
            return List.of();
        }
        return Arrays.stream(trimmed.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private void validateSubformAnswer(FormField field, String value) {
        FieldSettingsDto settings = formMapper.parseFieldSettings(field.getSettingsJson());
        boolean minEnabled = settings != null && Boolean.TRUE.equals(settings.pickerMinEnabled());
        boolean maxEnabled = settings != null && Boolean.TRUE.equals(settings.pickerMaxEnabled());
        int minCount = settings != null && settings.pickerMinCount() != null && settings.pickerMinCount() >= 1
                ? Math.min(50, settings.pickerMinCount())
                : 1;
        int maxCount = settings != null && settings.pickerMaxCount() != null && settings.pickerMaxCount() >= 1
                ? Math.min(50, settings.pickerMaxCount())
                : 10;

        List<Map<String, Object>> items;
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parsed = objectMapper.readValue(value, List.class);
            items = parsed != null ? parsed : List.of();
        } catch (Exception ex) {
            throw new InvalidSubmissionException(
                    "Sous-formulaire invalide pour « " + field.getLabel() + " ».");
        }

        int completed = 0;
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            Object status = item.get("status");
            if (status != null && "completed".equalsIgnoreCase(String.valueOf(status).trim())) {
                completed += 1;
            }
        }

        if (field.isRequired() && completed < 1) {
            throw new InvalidSubmissionException(
                    "Au moins une soumission est requise pour « " + field.getLabel() + " ».");
        }
        if (minEnabled && completed < minCount) {
            throw new InvalidSubmissionException(
                    "Créez au moins " + minCount + " soumission(s) pour « "
                            + field.getLabel() + " ».");
        }
        if (maxEnabled && completed > maxCount) {
            throw new InvalidSubmissionException(
                    "Au plus " + maxCount + " soumission(s) pour « "
                            + field.getLabel() + " ».");
        }
    }

    private void validateTableAnswer(FormField field, String value) {
        FieldSettingsDto settings = formMapper.parseFieldSettings(field.getSettingsJson());
        List<TableColumnDto> columns = settings != null && settings.tableColumns() != null
                ? settings.tableColumns()
                : List.of();
        if (columns.isEmpty()) {
            if (field.isRequired()) {
                throw new InvalidSubmissionException(
                        "Tableau invalide pour « " + field.getLabel() + " ».");
            }
            return;
        }

        List<Map<String, Object>> rows;
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parsed = objectMapper.readValue(value, List.class);
            rows = parsed != null ? parsed : List.of();
        } catch (Exception ex) {
            throw new InvalidSubmissionException(
                    "Tableau invalide pour « " + field.getLabel() + " ».");
        }

        int nonEmptyRows = 0;
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Map<String, Object> values = extractTableRowValues(row);
            boolean rowEmpty = true;
            for (TableColumnDto column : columns) {
                if (column == null || column.id() == null || column.id().isBlank()) {
                    continue;
                }
                String cell = stringValue(values.get(column.id())).trim();
                if (!cell.isEmpty()) {
                    rowEmpty = false;
                }
            }
            if (rowEmpty) {
                continue;
            }
            nonEmptyRows += 1;
            for (TableColumnDto column : columns) {
                if (column == null || column.id() == null || column.id().isBlank()) {
                    continue;
                }
                String cell = stringValue(values.get(column.id())).trim();
                if (cell.isEmpty()) {
                    if (field.isRequired()) {
                        throw new InvalidSubmissionException(
                                "Veuillez remplir le tableau « " + field.getLabel() + " ».");
                    }
                    continue;
                }
                String columnType = column.columnType() == null
                        ? "TEXT"
                        : column.columnType().trim().toUpperCase();
                if ("EMAIL".equals(columnType) && !cell.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    throw new InvalidSubmissionException(
                            "E-mail invalide dans le tableau « " + field.getLabel() + " ».");
                }
                if ("NUMBER".equals(columnType)) {
                    try {
                        Double.parseDouble(cell.replace(',', '.'));
                    } catch (NumberFormatException ex) {
                        throw new InvalidSubmissionException(
                                "Nombre invalide dans le tableau « " + field.getLabel() + " ».");
                    }
                }
            }
        }

        if (field.isRequired() && nonEmptyRows < 1) {
            throw new InvalidSubmissionException(
                    "Veuillez remplir le tableau « " + field.getLabel() + " ».");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractTableRowValues(Map<String, Object> row) {
        Object values = row.get("values");
        if (values instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return row;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void validateSubmissionPickerAnswer(FormField field, String value) {
        FieldSettingsDto settings = formMapper.parseFieldSettings(field.getSettingsJson());
        List<Long> linkedFormIds = settings != null && settings.linkedFormIds() != null
                ? settings.linkedFormIds().stream().filter(Objects::nonNull).distinct().toList()
                : List.of();
        boolean allowMultiple = settings != null && Boolean.TRUE.equals(settings.allowMultiple());
        boolean minEnabled = settings != null && Boolean.TRUE.equals(settings.pickerMinEnabled());
        boolean maxEnabled = settings != null && Boolean.TRUE.equals(settings.pickerMaxEnabled());
        int minCount = settings != null && settings.pickerMinCount() != null && settings.pickerMinCount() >= 1
                ? Math.min(100, settings.pickerMinCount())
                : 1;
        int maxCount = settings != null && settings.pickerMaxCount() != null && settings.pickerMaxCount() >= 1
                ? Math.min(100, settings.pickerMaxCount())
                : 10;

        List<Map<String, Object>> items;
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parsed = objectMapper.readValue(value, List.class);
            items = parsed != null ? parsed : List.of();
        } catch (Exception ex) {
            throw new InvalidSubmissionException(
                    "Sélection invalide pour « " + field.getLabel() + " ».");
        }
        if (items.isEmpty()) {
            throw new InvalidSubmissionException(
                    "Sélection invalide pour « " + field.getLabel() + " ».");
        }
        if (!allowMultiple && items.size() > 1) {
            throw new InvalidSubmissionException(
                    "Une seule soumission est autorisée pour « " + field.getLabel() + " ».");
        }
        if (minEnabled && items.size() < minCount) {
            throw new InvalidSubmissionException(
                    "Sélectionnez au moins " + minCount + " soumission(s) pour « "
                            + field.getLabel() + " ».");
        }
        if (maxEnabled && items.size() > maxCount) {
            throw new InvalidSubmissionException(
                    "Sélectionnez au plus " + maxCount + " soumission(s) pour « "
                            + field.getLabel() + " ».");
        }

        Set<Long> linked = new HashSet<>(linkedFormIds);
        Set<Long> seen = new HashSet<>();
        for (Map<String, Object> item : items) {
            if (item == null) {
                throw new InvalidSubmissionException(
                        "Sélection invalide pour « " + field.getLabel() + " ».");
            }
            Long submissionId = toLong(item.get("submissionId"));
            Long formId = toLong(item.get("formId"));
            if (submissionId == null || formId == null) {
                throw new InvalidSubmissionException(
                        "Sélection invalide pour « " + field.getLabel() + " ».");
            }
            if (!linked.isEmpty() && !linked.contains(formId)) {
                throw new InvalidSubmissionException(
                        "Soumission hors formulaire source pour « " + field.getLabel() + " ».");
            }
            if (!seen.add(submissionId)) {
                throw new InvalidSubmissionException(
                        "Soumission en double pour « " + field.getLabel() + " ».");
            }
            FormSubmission submission = formSubmissionRepository.findById(submissionId).orElse(null);
            if (submission == null
                    || submission.getForm() == null
                    || !Objects.equals(submission.getForm().getId(), formId)) {
                throw new InvalidSubmissionException(
                        "Soumission introuvable pour « " + field.getLabel() + " ».");
            }
        }
    }

    private Long toLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isValidRankingPermutation(List<String> options, List<String> ranked) {
        if (options == null) {
            options = List.of();
        }
        if (ranked == null || ranked.size() != options.size()) {
            return false;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String option : options) {
            counts.merge(option, 1, Integer::sum);
        }
        for (String item : ranked) {
            Integer remaining = counts.get(item);
            if (remaining == null || remaining <= 0) {
                return false;
            }
            counts.put(item, remaining - 1);
        }
        return counts.values().stream().allMatch(n -> n == 0);
    }

    private boolean isAllowedOptionValue(FormField field, List<String> options, String value) {
        if (options.contains(value)) {
            return true;
        }
        if ("Autre".equals(value)
                || value.startsWith("Autre:")
                || value.startsWith("__other__:")) {
            return true;
        }
        var settings = formMapper.parseFieldSettings(field.getSettingsJson());
        if (settings != null && settings.optionValues() != null) {
            for (String custom : settings.optionValues()) {
                if (custom != null && custom.trim().equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Réponse téléphone JSON vide (`{"country":"US","number":""}`) = pas de numéro. */
    private boolean isEmptyPhoneAnswer(FormField field, String value) {
        if (field.getFieldType() != FieldType.PHONE) {
            return false;
        }
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("{")) {
            return trimmed.replaceAll("\\D", "").isEmpty();
        }
        try {
            var node = objectMapper.readTree(trimmed);
            String number = node.path("number").asText("");
            return number.replaceAll("\\D", "").isEmpty();
        } catch (JsonProcessingException ex) {
            return trimmed.replaceAll("\\D", "").isEmpty();
        }
    }

    private boolean isValidHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        String candidate = trimmed.matches("(?i)^[a-z][a-z0-9+.-]*:.*")
                ? trimmed
                : "https://" + trimmed;
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isValidHexColor(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        String withHash = trimmed.startsWith("#") ? trimmed : "#" + trimmed;
        return withHash.matches("(?i)^#([0-9a-f]{3}|[0-9a-f]{6})$");
    }

    /** Compte les fichiers dans une réponse JSON `[{name,url}]` (ou une URL seule). */
    private int countUploadedFiles(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("[")) {
            return trimmed.isEmpty() ? 0 : 1;
        }
        try {
            var node = objectMapper.readTree(trimmed);
            if (!node.isArray()) {
                return 0;
            }
            int count = 0;
            for (var item : node) {
                if (item != null && item.hasNonNull("url") && !item.get("url").asText("").isBlank()) {
                    count += 1;
                }
            }
            return count;
        } catch (JsonProcessingException ex) {
            return 0;
        }
    }

    private boolean isValidSignatureAnswer(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("data:image")) {
            return true;
        }
        if (!trimmed.startsWith("{")) {
            return false;
        }
        try {
            var node = objectMapper.readTree(trimmed);
            String dataUrl = node.path("dataUrl").asText("");
            return dataUrl.startsWith("data:image");
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    /** Enrichissement A — contact connu si le répondant a une session AgenticForms valide. */
    private Map<String, String> resolveContactFromAuth() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Map.of();
        }
        return userRepository.findById(principal.getId())
                .map(PublicFormService::contactParamsFromUser)
                .orElse(Map.of());
    }

    private static Map<String, String> contactParamsFromUser(User user) {
        Map<String, String> contact = new LinkedHashMap<>();
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            contact.put("name", user.getFullName().trim());
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            contact.put("email", user.getEmail().trim());
        }
        return contact;
    }

    /** minLength / maxLength peuvent être un nombre ou une référence texte ({{…}}). */
    private static Integer asPositiveInt(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number number) {
            int value = number.intValue();
            return value > 0 ? value : null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || text.contains("{{")) return null;
        try {
            int value = Integer.parseInt(text);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean formHasLoginPage(Form form) {
        PagesDocumentDto document = formMapper.parsePagesDocument(form.getPagesJson());
        if (document == null || document.pages() == null) {
            return false;
        }
        for (FormPageDto page : document.pages()) {
            if (page != null && "login".equalsIgnoreCase(page.type())) {
                return true;
            }
        }
        return false;
    }

    private LoginConfigDto loginConfigForForm(Form form) {
        PagesDocumentDto document = formMapper.parsePagesDocument(form.getPagesJson());
        if (document == null || document.pages() == null) {
            return null;
        }
        for (FormPageDto page : document.pages()) {
            if (page != null && "login".equalsIgnoreCase(page.type())) {
                return page.loginConfig();
            }
        }
        return null;
    }

    private FormLoginResumeStatusResponse emptyResumeStatus() {
        return new FormLoginResumeStatusResponse(
                false, false, false, false, true,
                null, null,
                false, null, null, null, Map.of(),
                false, null, null, Map.of());
    }

    private String resolveLimitTitle(LoginConfigDto loginConfig) {
        if (loginConfig != null && StringUtils.hasText(loginConfig.limitTitle())) {
            return loginConfig.limitTitle().trim();
        }
        return "Vous avez déjà soumis ce formulaire";
    }

    private String resolveLimitSubtitle(LoginConfigDto loginConfig) {
        if (loginConfig != null && StringUtils.hasText(loginConfig.limitSubtitle())) {
            return loginConfig.limitSubtitle().trim();
        }
        return "Contactez le propriétaire du formulaire.";
    }

    private FormSubmission replaceSubmissionAnswers(
            FormSubmission existing,
            Map<Long, FormField> fieldsById,
            Map<Long, String> valuesByFieldId,
            String respondentEmail) {
        existing.setRespondentEmail(respondentEmail);
        existing.getAnswers().clear();
        for (Map.Entry<Long, String> entry : valuesByFieldId.entrySet()) {
            FormField field = fieldsById.get(entry.getKey());
            String value = entry.getValue();
            if (field == null || value == null || value.isBlank()) {
                continue;
            }
            existing.addAnswer(FormSubmissionAnswer.builder()
                    .field(field)
                    .valueText(value)
                    .build());
        }
        return formSubmissionRepository.save(existing);
    }

    private boolean hasMeaningfulSessionAnswers(FormSession session) {
        Map<Long, String> answers = formMapper.parseAnswerMap(session.getAnswersJson());
        return answers.values().stream().anyMatch(value -> value != null && !value.isBlank());
    }

    private Map<Long, String> submissionAnswersToMap(FormSubmission submission) {
        Map<Long, String> answers = new LinkedHashMap<>();
        if (submission.getAnswers() == null) {
            return answers;
        }
        for (FormSubmissionAnswer answer : submission.getAnswers()) {
            if (answer.getField() == null || answer.getField().getId() == null) {
                continue;
            }
            String value = answer.getValueText();
            if (value == null || value.isBlank()) {
                continue;
            }
            answers.put(answer.getField().getId(), value);
        }
        return answers;
    }

    private static String normalizeRespondentEmail(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toLowerCase();
    }
}
