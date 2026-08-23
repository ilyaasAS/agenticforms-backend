package com.agenticform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.agenticform.dto.ContactRequest;
import com.agenticform.exception.ContactMessageNotFoundException;
import com.agenticform.model.document.ContactMessage;
import com.agenticform.repository.ContactMessageRepository;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactMessageRepository contactMessageRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ContactService contactService;

    @Test
    void submitPersistsMongoDocumentAndNotifies() {
        ContactRequest request = new ContactRequest("Ada", "ada@example.com", "Sujet", "Bonjour");
        ContactMessage saved = new ContactMessage("Ada", "ada@example.com", "Sujet", "Bonjour");
        saved.setId("mongo-1");
        given(contactMessageRepository.save(any(ContactMessage.class))).willReturn(saved);

        ContactMessage result = contactService.submit(request);

        assertEquals("mongo-1", result.getId());
        assertEquals("NEW", result.getStatus());
        verify(emailService).notifyContactInbox("Ada", "ada@example.com", "Sujet", "Bonjour");
    }

    @Test
    void listReturnsMessagesNewestFirst() {
        ContactMessage first = new ContactMessage("A", "a@example.com", "Un", "Msg");
        first.setId("1");
        first.setCreatedAt(Instant.parse("2026-08-23T10:00:00Z"));
        given(contactMessageRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(first));

        assertEquals(1, contactService.list().size());
        assertEquals("1", contactService.list().get(0).id());
    }

    @Test
    void updateStatusArchivesMessage() {
        ContactMessage existing = new ContactMessage("A", "a@example.com", "Un", "Msg");
        existing.setId("abc");
        given(contactMessageRepository.findById("abc")).willReturn(Optional.of(existing));
        given(contactMessageRepository.save(existing)).willReturn(existing);

        assertEquals("ARCHIVED", contactService.updateStatus("abc", "ARCHIVED").status());
    }

    @Test
    void replySendsEmailAndMarksRead() {
        ContactMessage existing = new ContactMessage("Ada", "ada@example.com", "Aide", "Bonjour");
        existing.setId("abc");
        given(contactMessageRepository.findById("abc")).willReturn(Optional.of(existing));
        given(contactMessageRepository.save(existing)).willReturn(existing);

        assertEquals("READ", contactService.reply("abc", "Voici la réponse.").status());
        verify(emailService).sendContactReply(
                "ada@example.com",
                "Ada",
                "Aide",
                "Bonjour",
                "Voici la réponse.");
    }

    @Test
    void getUnknownIdThrows() {
        given(contactMessageRepository.findById("missing")).willReturn(Optional.empty());
        assertThrows(ContactMessageNotFoundException.class, () -> contactService.get("missing"));
    }
}
