package com.agenticform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.FormSessionResponse;
import com.agenticform.dto.PublicFormResponse;
import com.agenticform.dto.SubmissionResponse;
import com.agenticform.dto.SubmitFormRequest;
import com.agenticform.dto.UpsertFormSessionRequest;
import com.agenticform.service.PublicFormService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/public/forms")
public class PublicFormController {

    private final PublicFormService publicFormService;

    public PublicFormController(PublicFormService publicFormService) {
        this.publicFormService = publicFormService;
    }

    @GetMapping("/{formId}")
    public ResponseEntity<PublicFormResponse> getForm(@PathVariable Long formId) {
        return ResponseEntity.ok(publicFormService.getPublishedForm(formId));
    }

    @PostMapping("/{formId}/session")
    public ResponseEntity<FormSessionResponse> upsertSession(
            @PathVariable Long formId,
            @Valid @RequestBody UpsertFormSessionRequest request) {
        FormSessionResponse response = publicFormService.upsertSession(formId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{formId}/submissions")
    public ResponseEntity<SubmissionResponse> submit(
            @PathVariable Long formId,
            @Valid @RequestBody SubmitFormRequest request) {
        SubmissionResponse response = publicFormService.submit(formId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
