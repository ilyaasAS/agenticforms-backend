package com.agenticform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.agenticform.dto.AuthResponse;
import com.agenticform.dto.AuthSessionResult;
import com.agenticform.dto.LocalLoginResponse;
import com.agenticform.dto.OAuth2LoginResponse;
import com.agenticform.dto.SignupResponse;
import com.agenticform.exception.GlobalExceptionHandler;
import com.agenticform.security.AuthCookieService;
import com.agenticform.service.AuthService;
import com.agenticform.service.EmailVerificationService;
import com.agenticform.service.PasswordResetService;

@ExtendWith(MockitoExtension.class)
class AuthControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private AuthCookieService authCookieService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signupReturnsCreatedWithMessage() throws Exception {
        SignupResponse response = new SignupResponse(
                "Si cet e-mail est disponible, un message de confirmation vous a été envoyé.");
        given(authService.signup(any())).willReturn(response);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new.user@example.com",
                                  "password": "StrongP@ss1",
                                  "fullName": "New User"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(response.message()));
    }

    @Test
    void loginLocalSetsCookieAndReturnsUser() throws Exception {
        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                10L, "local.user@example.com", "ROLE_USER", "Local User");
        AuthSessionResult session = new AuthSessionResult("jwt-local-token", new AuthResponse(userInfo));
        LocalLoginResponse response = new LocalLoginResponse(userInfo);

        given(authService.loginLocal(any())).willReturn(session);
        given(authService.toLocalLoginResponse(session)).willReturn(response);

        mockMvc.perform(post("/api/auth/login/local")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "local.user@example.com",
                                  "password": "StrongP@ss1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(10))
                .andExpect(jsonPath("$.user.email").value("local.user@example.com"))
                .andExpect(jsonPath("$.user.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.user.fullName").value("Local User"));

        verify(authCookieService).setAccessToken(any(), any(), eq("jwt-local-token"));
    }

    @Test
    void loginOAuth2SetsCookieAndReturnsUser() throws Exception {
        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                20L, "oauth.user@example.com", "ROLE_USER", "OAuth User");
        AuthSessionResult session = new AuthSessionResult("jwt-oauth-token", new AuthResponse(userInfo));
        OAuth2LoginResponse response = new OAuth2LoginResponse(userInfo);

        given(authService.loginOAuth2(any())).willReturn(session);
        given(authService.toOAuth2LoginResponse(session)).willReturn(response);

        mockMvc.perform(post("/api/auth/login/oauth2")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "oauth-code-123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(20))
                .andExpect(jsonPath("$.user.email").value("oauth.user@example.com"))
                .andExpect(jsonPath("$.user.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.user.fullName").value("OAuth User"));

        verify(authCookieService).setAccessToken(any(), any(), eq("jwt-oauth-token"));
    }

    @Test
    void loginLocalRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/login/local")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }
}
