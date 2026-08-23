package com.agenticform.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.agenticform.dto.SubmissionResponse;
import com.agenticform.exception.GlobalExceptionHandler;
import com.agenticform.service.PublicFormService;
import com.agenticform.service.StripeIntegrationService;

@ExtendWith(MockitoExtension.class)
class PublicFormSubmitControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PublicFormService publicFormService;

    @Mock
    private StripeIntegrationService stripeIntegrationService;

    @InjectMocks
    private PublicFormController publicFormController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(publicFormController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void publicSubmitReturnsCreated() throws Exception {
        given(publicFormService.submit(eq(42L), org.mockito.ArgumentMatchers.any()))
                .willReturn(new SubmissionResponse(7L, 42L, Instant.parse("2026-08-23T12:00:00Z")));

        mockMvc.perform(post("/api/v1/public/forms/42/submissions")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    { "fieldId": 1, "value": "Ada Lovelace" }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.formId").value(42));
    }
}
