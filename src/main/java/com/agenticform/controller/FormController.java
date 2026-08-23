package com.agenticform.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.CreateFormFieldRequest;
import com.agenticform.dto.CreateFormRequest;
import com.agenticform.dto.FormFieldResponse;
import com.agenticform.dto.FormResponse;
import com.agenticform.dto.FormResultsResponse;
import com.agenticform.dto.FormSummaryResponse;
import com.agenticform.dto.InProgressSessionResponse;
import com.agenticform.dto.ReorderFormFieldsRequest;
import com.agenticform.dto.SetFormLoginPasswordRequest;
import com.agenticform.dto.UpdateFormFieldRequest;
import com.agenticform.dto.UpdateFormRequest;
import com.agenticform.security.UserPrincipal;
import com.agenticform.service.FormService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/forms")
public class FormController {

    private final FormService formService;

    public FormController(FormService formService) {
        this.formService = formService;
    }

    @GetMapping
    public ResponseEntity<List<FormSummaryResponse>> listForms(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(formService.listForms(workspaceId, requireUserId(principal)));
    }

    @PostMapping
    public ResponseEntity<FormResponse> createForm(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateFormRequest request) {
        FormResponse response = formService.createForm(workspaceId, requireUserId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{formId}")
    public ResponseEntity<FormResponse> getForm(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId) {
        return ResponseEntity.ok(formService.getForm(workspaceId, formId, requireUserId(principal)));
    }

    @PatchMapping("/{formId}")
    public ResponseEntity<FormResponse> updateForm(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId,
            @Valid @RequestBody UpdateFormRequest request) {
        return ResponseEntity.ok(
                formService.updateForm(workspaceId, formId, requireUserId(principal), request));
    }

    @PostMapping("/{formId}/publish")
    public ResponseEntity<FormResponse> publishForm(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId) {
        return ResponseEntity.ok(
                formService.publishForm(workspaceId, formId, requireUserId(principal)));
    }

    @GetMapping("/{formId}/draft-preview")
    public ResponseEntity<com.agenticform.dto.PublicFormResponse> draftPreview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId) {
        return ResponseEntity.ok(
                formService.getDraftAsPublic(workspaceId, formId, requireUserId(principal)));
    }

    @PostMapping("/{formId}/login-password")
    public ResponseEntity<FormResponse> setLoginPassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId,
            @Valid @RequestBody SetFormLoginPasswordRequest request) {
        return ResponseEntity.ok(
                formService.setLoginPassword(workspaceId, formId, requireUserId(principal), request));
    }

    @DeleteMapping("/{formId}")
    public ResponseEntity<Void> deleteForm(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId) {
        formService.deleteForm(workspaceId, formId, requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{formId}/submissions")
    public ResponseEntity<FormResultsResponse> listSubmissions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId) {
        return ResponseEntity.ok(
                formService.getFormResults(workspaceId, formId, requireUserId(principal)));
    }

    @GetMapping("/{formId}/in-progress")
    public ResponseEntity<List<InProgressSessionResponse>> listInProgress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId) {
        return ResponseEntity.ok(
                formService.listInProgressSessions(workspaceId, formId, requireUserId(principal)));
    }

    @GetMapping("/{formId}/fields")
    public ResponseEntity<List<FormFieldResponse>> listFields(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId) {
        return ResponseEntity.ok(
                formService.listFields(workspaceId, formId, requireUserId(principal)));
    }

    @PostMapping("/{formId}/fields")
    public ResponseEntity<FormFieldResponse> createField(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId,
            @Valid @RequestBody CreateFormFieldRequest request) {
        FormFieldResponse response = formService.createField(
                workspaceId, formId, requireUserId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{formId}/fields/{fieldId}")
    public ResponseEntity<FormFieldResponse> updateField(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId,
            @PathVariable Long fieldId,
            @Valid @RequestBody UpdateFormFieldRequest request) {
        return ResponseEntity.ok(
                formService.updateField(workspaceId, formId, fieldId, requireUserId(principal), request));
    }

    @DeleteMapping("/{formId}/fields/{fieldId}")
    public ResponseEntity<Void> deleteField(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId,
            @PathVariable Long fieldId) {
        formService.deleteField(workspaceId, formId, fieldId, requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{formId}/fields/reorder")
    public ResponseEntity<List<FormFieldResponse>> reorderFields(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long workspaceId,
            @PathVariable Long formId,
            @Valid @RequestBody ReorderFormFieldsRequest request) {
        return ResponseEntity.ok(
                formService.reorderFields(workspaceId, formId, requireUserId(principal), request));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentification requise.");
        }
        return principal.getId();
    }
}
