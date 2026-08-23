package com.agenticform.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.agenticform.dto.ContactMessageResponse;
import com.agenticform.dto.ContactRequest;
import com.agenticform.exception.ContactMessageNotFoundException;
import com.agenticform.model.document.ContactMessage;
import com.agenticform.repository.ContactMessageRepository;

@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final ContactMessageRepository contactMessageRepository;
    private final EmailService emailService;

    public ContactService(
            ContactMessageRepository contactMessageRepository,
            EmailService emailService) {
        this.contactMessageRepository = contactMessageRepository;
        this.emailService = emailService;
    }

    public ContactMessage submit(ContactRequest request) {
        String name = request.name().trim();
        String email = request.email().trim().toLowerCase();
        String subject = request.subject().trim();
        String message = request.message().trim();

        ContactMessage doc = new ContactMessage(name, email, subject, message);
        ContactMessage saved = contactMessageRepository.save(doc);
        log.info("Contact message persisted in Mongo id={} email={}", saved.getId(), email);

        emailService.notifyContactInbox(name, email, subject, message);

        return saved;
    }

    public List<ContactMessageResponse> list() {
        return contactMessageRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public ContactMessageResponse get(String id) {
        return toResponse(requireMessage(id));
    }

    public ContactMessageResponse updateStatus(String id, String status) {
        ContactMessage doc = requireMessage(id);
        doc.setStatus(status.trim().toUpperCase());
        return toResponse(contactMessageRepository.save(doc));
    }

    public ContactMessageResponse reply(String id, String body) {
        ContactMessage doc = requireMessage(id);
        emailService.sendContactReply(
                doc.getEmail(),
                doc.getName(),
                doc.getSubject(),
                doc.getMessage(),
                body);
        doc.setStatus("READ");
        log.info("Contact reply sent id={} to={}", id, doc.getEmail());
        return toResponse(contactMessageRepository.save(doc));
    }

    private ContactMessage requireMessage(String id) {
        return contactMessageRepository.findById(id)
                .orElseThrow(() -> new ContactMessageNotFoundException(id));
    }

    private ContactMessageResponse toResponse(ContactMessage doc) {
        return new ContactMessageResponse(
                doc.getId(),
                doc.getName(),
                doc.getEmail(),
                doc.getSubject(),
                doc.getMessage(),
                doc.getCreatedAt(),
                doc.getStatus());
    }
}
