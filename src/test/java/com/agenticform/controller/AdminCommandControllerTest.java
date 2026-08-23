package com.agenticform.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.agenticform.dto.AdminFormResponse;
import com.agenticform.dto.AdminUserResponse;
import com.agenticform.exception.GlobalExceptionHandler;
import com.agenticform.service.AdminFormService;
import com.agenticform.service.AdminUserService;

@ExtendWith(MockitoExtension.class)
class AdminCommandControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminFormService adminFormService;

    @Mock
    private AdminUserService adminUserService;

    @InjectMocks
    private AdminFormController adminFormController;

    @InjectMocks
    private AdminUserController adminUserController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminFormController, adminUserController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void adminListsForms() throws Exception {
        given(adminFormService.list()).willReturn(List.of(new AdminFormResponse(
                12L,
                "Sondage",
                "PUBLISHED",
                false,
                4L,
                "Ada",
                2L,
                "ada@example.com",
                "Ada",
                Instant.parse("2026-08-23T10:00:00Z"),
                Instant.parse("2026-08-23T11:00:00Z"))));

        mockMvc.perform(get("/api/v1/admin/forms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Sondage"));
    }

    @Test
    void adminBlocksForm() throws Exception {
        given(adminFormService.setBlocked(12L, true)).willReturn(new AdminFormResponse(
                12L,
                "Sondage",
                "PUBLISHED",
                true,
                4L,
                "Ada",
                2L,
                "ada@example.com",
                "Ada",
                Instant.parse("2026-08-23T10:00:00Z"),
                Instant.parse("2026-08-23T11:00:00Z")));

        mockMvc.perform(patch("/api/v1/admin/forms/12")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                { "blocked": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));
    }

    @Test
    void adminDeletesForm() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/forms/12"))
                .andExpect(status().isNoContent());
        verify(adminFormService).delete(12L);
    }

    @Test
    void adminListsUsers() throws Exception {
        given(adminUserService.list()).willReturn(List.of(new AdminUserResponse(
                2L,
                "ada@example.com",
                "Ada",
                "ROLE_USER",
                false,
                true,
                LocalDateTime.parse("2026-08-23T10:00:00"))));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ada@example.com"));
    }
}
