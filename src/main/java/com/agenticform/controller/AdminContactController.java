package com.agenticform.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.ContactMessageResponse;
import com.agenticform.dto.ContactReplyRequest;
import com.agenticform.dto.ContactStatusUpdateRequest;
import com.agenticform.service.ContactService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/contact-messages")
public class AdminContactController {

    private final ContactService contactService;

    public AdminContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public ResponseEntity<List<ContactMessageResponse>> list() {
        return ResponseEntity.ok(contactService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContactMessageResponse> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(contactService.get(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ContactMessageResponse> updateStatus(
            @PathVariable("id") String id,
            @Valid @RequestBody ContactStatusUpdateRequest request) {
        return ResponseEntity.ok(contactService.updateStatus(id, request.status()));
    }

    @PostMapping("/{id}/reply")
    public ResponseEntity<ContactMessageResponse> reply(
            @PathVariable("id") String id,
            @Valid @RequestBody ContactReplyRequest request) {
        return ResponseEntity.ok(contactService.reply(id, request.body()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
