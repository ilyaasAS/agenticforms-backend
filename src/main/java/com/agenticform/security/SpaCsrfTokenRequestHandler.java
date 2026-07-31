package com.agenticform.security;

import java.util.function.Supplier;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handler CSRF adapté SPA : privilégie le header X-XSRF-TOKEN.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler xorHandler = new XorCsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestAttributeHandler plainHandler = new CsrfTokenRequestAttributeHandler();

    public SpaCsrfTokenRequestHandler() {
        this.plainHandler.setCsrfRequestAttributeName("_csrf");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            Supplier<CsrfToken> csrfToken) {
        this.xorHandler.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return this.plainHandler.resolveCsrfTokenValue(request, csrfToken);
        }
        return this.xorHandler.resolveCsrfTokenValue(request, csrfToken);
    }
}
