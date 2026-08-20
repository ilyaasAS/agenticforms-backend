package com.agenticform.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.CalendlyEventTypesResponse;
import com.agenticform.dto.CalendlyStatusResponse;
import com.agenticform.exception.CalendlyIntegrationException;
import com.agenticform.exception.CalendlyNotConfiguredException;
import com.agenticform.security.UserPrincipal;
import com.agenticform.service.CalendlyIntegrationService;

@RestController
@RequestMapping("/api/v1/integrations/calendly")
public class CalendlyIntegrationController {

    private final CalendlyIntegrationService calendlyIntegrationService;

    public CalendlyIntegrationController(CalendlyIntegrationService calendlyIntegrationService) {
        this.calendlyIntegrationService = calendlyIntegrationService;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "switch", defaultValue = "false") boolean switchAccount) {
        Long userId = requireUserId(principal);
        if (!calendlyIntegrationService.isConfigured()) {
            return redirect(calendlyIntegrationService.frontendCallbackUrl(false, "not_configured"));
        }
        return redirect(calendlyIntegrationService.buildAuthorizeUrl(userId, switchAccount));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null && !error.isBlank()) {
            return redirect(calendlyIntegrationService.frontendCallbackUrl(false, error));
        }
        try {
            calendlyIntegrationService.handleCallback(code, state);
            return redirect(calendlyIntegrationService.frontendCallbackUrl(true, null));
        } catch (CalendlyNotConfiguredException ex) {
            return redirect(calendlyIntegrationService.frontendCallbackUrl(false, "not_configured"));
        } catch (CalendlyIntegrationException ex) {
            return redirect(calendlyIntegrationService.frontendCallbackUrl(false, "oauth_failed"));
        } catch (RuntimeException ex) {
            return redirect(calendlyIntegrationService.frontendCallbackUrl(false, "oauth_failed"));
        }
    }

    @GetMapping
    public ResponseEntity<CalendlyStatusResponse> status(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(calendlyIntegrationService.status(requireUserId(principal)));
    }

    @GetMapping("/event-types")
    public ResponseEntity<CalendlyEventTypesResponse> eventTypes(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(calendlyIntegrationService.listEventTypes(requireUserId(principal)));
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal UserPrincipal principal) {
        calendlyIntegrationService.disconnect(requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentification requise.");
        }
        return principal.getId();
    }

    private static ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
