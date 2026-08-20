package com.agenticform.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.ConfirmPaymentRequest;
import com.agenticform.dto.ConfirmPaymentResponse;
import com.agenticform.dto.CreatePaymentIntentRequest;
import com.agenticform.dto.FormGoogleLoginExchangeRequest;
import com.agenticform.dto.FormLoginAbandonProgressRequest;
import com.agenticform.dto.FormLoginPasswordVerifyRequest;
import com.agenticform.dto.FormLoginPasswordVerifyResponse;
import com.agenticform.dto.FormLoginResumeStatusRequest;
import com.agenticform.dto.FormLoginResumeStatusResponse;
import com.agenticform.dto.FormLoginSendCodeRequest;
import com.agenticform.dto.FormLoginVerifyRequest;
import com.agenticform.dto.FormLoginVerifyResponse;
import com.agenticform.dto.FormSessionResponse;
import com.agenticform.dto.GoogleCalendarAvailabilityRequest;
import com.agenticform.dto.GoogleCalendarAvailabilityResponse;
import com.agenticform.dto.GoogleCalendarBookRequest;
import com.agenticform.dto.GoogleCalendarBookResponse;
import com.agenticform.dto.PublicFormResponse;
import com.agenticform.dto.PublicPickerRecordsResponse;
import com.agenticform.dto.PublicSchedulingEventResponse;
import com.agenticform.dto.StripePaymentIntentResponse;
import com.agenticform.dto.SubmissionResponse;
import com.agenticform.dto.SubmitFormRequest;
import com.agenticform.dto.UpsertFormSessionRequest;
import com.agenticform.service.PublicFormService;
import com.agenticform.service.StripeIntegrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/public/forms")
public class PublicFormController {

    private final PublicFormService publicFormService;
    private final StripeIntegrationService stripeIntegrationService;

    public PublicFormController(
            PublicFormService publicFormService,
            StripeIntegrationService stripeIntegrationService) {
        this.publicFormService = publicFormService;
        this.stripeIntegrationService = stripeIntegrationService;
    }

    @GetMapping("/{formId}")
    public ResponseEntity<PublicFormResponse> getForm(@PathVariable Long formId) {
        return ResponseEntity.ok(publicFormService.getPublishedForm(formId));
    }

    @GetMapping("/{formId}/fields/{fieldId}/records")
    public ResponseEntity<PublicPickerRecordsResponse> listPickerRecords(
            @PathVariable Long formId,
            @PathVariable Long fieldId) {
        return ResponseEntity.ok(publicFormService.listPickerRecords(formId, fieldId));
    }

    @PostMapping("/{formId}/session")
    public ResponseEntity<FormSessionResponse> upsertSession(
            @PathVariable Long formId,
            @Valid @RequestBody UpsertFormSessionRequest request) {
        FormSessionResponse response = publicFormService.upsertSession(formId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{formId}/payments/intent")
    public ResponseEntity<StripePaymentIntentResponse> createPaymentIntent(
            @PathVariable Long formId,
            @RequestBody(required = false) @Valid CreatePaymentIntentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stripeIntegrationService.createPublicPaymentIntent(
                        formId,
                        request == null ? new CreatePaymentIntentRequest(null, null, null, null) : request));
    }

    @PostMapping("/{formId}/payments/confirm")
    public ResponseEntity<ConfirmPaymentResponse> confirmPayment(
            @PathVariable Long formId,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        return ResponseEntity.ok(stripeIntegrationService.confirmPublicPayment(formId, request));
    }

    @PostMapping("/{formId}/scheduling/availability")
    public ResponseEntity<GoogleCalendarAvailabilityResponse> schedulingAvailability(
            @PathVariable Long formId,
            @Valid @RequestBody GoogleCalendarAvailabilityRequest request) {
        return ResponseEntity.ok(publicFormService.schedulingAvailability(formId, request));
    }

    @PostMapping("/{formId}/scheduling/book")
    public ResponseEntity<GoogleCalendarBookResponse> schedulingBook(
            @PathVariable Long formId,
            @Valid @RequestBody GoogleCalendarBookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(publicFormService.schedulingBook(formId, request));
    }

    @PostMapping("/{formId}/scheduling/events/{eventId}/modify")
    public ResponseEntity<GoogleCalendarBookResponse> schedulingModify(
            @PathVariable Long formId,
            @PathVariable String eventId,
            @Valid @RequestBody GoogleCalendarBookRequest request) {
        return ResponseEntity.ok(publicFormService.schedulingModify(formId, eventId, request));
    }

    @GetMapping("/{formId}/scheduling/events/{eventId}")
    public ResponseEntity<PublicSchedulingEventResponse> schedulingGetEvent(
            @PathVariable Long formId,
            @PathVariable String eventId,
            @RequestParam(required = false) String calendarId) {
        return ResponseEntity.ok(publicFormService.schedulingGetEvent(formId, eventId, calendarId));
    }

    @DeleteMapping("/{formId}/scheduling/events/{eventId}")
    public ResponseEntity<Void> schedulingCancel(
            @PathVariable Long formId,
            @PathVariable String eventId,
            @RequestParam(required = false) String calendarId) {
        publicFormService.schedulingCancel(formId, eventId, calendarId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{formId}/login/send-code")
    public ResponseEntity<Void> sendLoginCode(
            @PathVariable Long formId,
            @Valid @RequestBody FormLoginSendCodeRequest request) {
        publicFormService.sendLoginCode(formId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{formId}/login/verify")
    public ResponseEntity<FormLoginVerifyResponse> verifyLoginCode(
            @PathVariable Long formId,
            @Valid @RequestBody FormLoginVerifyRequest request) {
        return ResponseEntity.ok(publicFormService.verifyLoginCode(formId, request));
    }

    @PostMapping("/{formId}/login/verify-password")
    public ResponseEntity<FormLoginPasswordVerifyResponse> verifyLoginPassword(
            @PathVariable Long formId,
            @Valid @RequestBody FormLoginPasswordVerifyRequest request) {
        return ResponseEntity.ok(publicFormService.verifyLoginPassword(formId, request));
    }

    @GetMapping("/{formId}/login/google/authorize")
    public ResponseEntity<Void> googleLoginAuthorize(@PathVariable Long formId) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(publicFormService.buildGoogleLoginAuthorizeUrl(formId)))
                .build();
    }

    @GetMapping("/login/google/callback")
    public ResponseEntity<Void> googleLoginCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(publicFormService.handleGoogleLoginCallback(code, state, error)))
                .build();
    }

    @PostMapping("/{formId}/login/google/exchange")
    public ResponseEntity<FormLoginVerifyResponse> googleLoginExchange(
            @PathVariable Long formId,
            @Valid @RequestBody FormGoogleLoginExchangeRequest request) {
        return ResponseEntity.ok(publicFormService.exchangeGoogleLogin(formId, request));
    }

    @PostMapping("/{formId}/login/resume-status")
    public ResponseEntity<FormLoginResumeStatusResponse> loginResumeStatus(
            @PathVariable Long formId,
            @Valid @RequestBody FormLoginResumeStatusRequest request) {
        return ResponseEntity.ok(publicFormService.resumeStatus(formId, request));
    }

    @PostMapping("/{formId}/login/abandon-progress")
    public ResponseEntity<Void> abandonLoginProgress(
            @PathVariable Long formId,
            @Valid @RequestBody FormLoginAbandonProgressRequest request) {
        publicFormService.abandonLoginProgress(formId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{formId}/submissions")
    public ResponseEntity<SubmissionResponse> submit(
            @PathVariable Long formId,
            @Valid @RequestBody SubmitFormRequest request) {
        SubmissionResponse response = publicFormService.submit(formId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
