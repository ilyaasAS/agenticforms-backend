package com.agenticform.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.agenticform.dto.ContactMessageResponse;
import com.agenticform.exception.GlobalExceptionHandler;
import com.agenticform.model.document.ContactMessage;
import com.agenticform.service.ContactService;

@ExtendWith(MockitoExtension.class)
class ContactInboxControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContactService contactService;

    @InjectMocks
    private ContactController contactController;

    @InjectMocks
    private AdminContactController adminContactController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(contactController, adminContactController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void publicContactCreatesMessage() throws Exception {
        ContactMessage saved = new ContactMessage("Ada", "ada@example.com", "Aide", "Bonjour");
        saved.setId("mongo-1");
        given(contactService.submit(org.mockito.ArgumentMatchers.any())).willReturn(saved);

        mockMvc.perform(post("/api/v1/public/contact")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ada",
                                  "email": "ada@example.com",
                                  "subject": "Aide",
                                  "message": "Bonjour"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists());

        verify(contactService).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminListReturnsMessages() throws Exception {
        given(contactService.list()).willReturn(List.of(new ContactMessageResponse(
                "mongo-1",
                "Ada",
                "ada@example.com",
                "Aide",
                "Bonjour",
                Instant.parse("2026-08-23T10:00:00Z"),
                "NEW")));

        mockMvc.perform(get("/api/v1/admin/contact-messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("mongo-1"))
                .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    void adminPatchArchivesMessage() throws Exception {
        given(contactService.updateStatus("mongo-1", "ARCHIVED")).willReturn(new ContactMessageResponse(
                "mongo-1",
                "Ada",
                "ada@example.com",
                "Aide",
                "Bonjour",
                Instant.parse("2026-08-23T10:00:00Z"),
                "ARCHIVED"));

        mockMvc.perform(patch("/api/v1/admin/contact-messages/mongo-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                { "status": "ARCHIVED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void adminReplySendsResponse() throws Exception {
        given(contactService.reply("mongo-1", "Voici la réponse.")).willReturn(new ContactMessageResponse(
                "mongo-1",
                "Ada",
                "ada@example.com",
                "Aide",
                "Bonjour",
                Instant.parse("2026-08-23T10:00:00Z"),
                "READ"));

        mockMvc.perform(post("/api/v1/admin/contact-messages/mongo-1/reply")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                { "body": "Voici la réponse." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));
    }
}
