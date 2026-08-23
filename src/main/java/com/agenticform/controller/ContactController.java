package com.agenticform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.ContactRequest;
import com.agenticform.dto.MessageResponse;
import com.agenticform.service.ContactService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/public/contact")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> submit(@Valid @RequestBody ContactRequest request) {
        contactService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponse(
                "Votre message a bien été envoyé. Nous vous répondrons rapidement."));
    }
}
