package com.agenticform.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class RecaptchaServiceTest {

    private RecaptchaService service;
    private HttpClient httpClient;
    @SuppressWarnings("unchecked")
    private final HttpResponse<String> httpResponse = mock(HttpResponse.class);

    @BeforeEach
    void setUp() throws Exception {
        service = new RecaptchaService("test-secret", new ObjectMapper());
        httpClient = mock(HttpClient.class);
        Field field = RecaptchaService.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(service, httpClient);
    }

    @Test
    void blankTokenIsRejectedWithoutCallingGoogle() {
        assertFalse(service.verify(null));
        assertFalse(service.verify(""));
        assertFalse(service.verify("   "));
    }

    @Test
    void verifyReturnsTrueWhenGoogleReportsSuccess() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"success\":true}");
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        assertTrue(service.verify("valid-token-from-widget"));
    }

    @Test
    void verifyReturnsFalseWhenGoogleReportsFailure() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}");
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        assertFalse(service.verify("bogus-token"));
    }

    @Test
    void verifyReturnsFalseOnHttpError() throws Exception {
        when(httpResponse.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        assertFalse(service.verify("any-token"));
    }
}
