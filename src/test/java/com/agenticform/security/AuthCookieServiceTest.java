package com.agenticform.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieServiceTest {

    @Test
    void setAccessTokenWritesHttpOnlyCookie() {
        CookieSecuritySupport support = mock(CookieSecuritySupport.class);
        given(support.isSecureRequest(org.mockito.ArgumentMatchers.any())).willReturn(true);
        AuthCookieService service = new AuthCookieService(3_600_000L, support);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setAccessToken(request, response, "jwt-value", 3_600_000L);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains(AuthCookieService.ACCESS_COOKIE + "=jwt-value"));
        assertTrue(setCookie.toLowerCase().contains("httponly"));
        assertTrue(setCookie.toLowerCase().contains("secure"));
        verify(support).isSecureRequest(request);
    }

    @Test
    void clearAccessTokenExpiresCookie() {
        CookieSecuritySupport support = mock(CookieSecuritySupport.class);
        given(support.isSecureRequest(org.mockito.ArgumentMatchers.any())).willReturn(false);
        AuthCookieService service = new AuthCookieService(3_600_000L, support);

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.clearAccessToken(new MockHttpServletRequest(), response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains(AuthCookieService.ACCESS_COOKIE + "="));
        assertTrue(setCookie.contains("Max-Age=0") || setCookie.toLowerCase().contains("max-age=0"));
        assertTrue(setCookie.toLowerCase().contains("httponly"));
    }
}
