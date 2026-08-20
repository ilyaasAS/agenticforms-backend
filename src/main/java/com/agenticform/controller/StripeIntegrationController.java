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

import com.agenticform.dto.StripeStatusResponse;
import com.agenticform.exception.StripeIntegrationException;
import com.agenticform.exception.StripeNotConfiguredException;
import com.agenticform.security.UserPrincipal;
import com.agenticform.service.StripeIntegrationService;

@RestController
@RequestMapping("/api/v1/integrations/stripe")
public class StripeIntegrationController {

    private final StripeIntegrationService stripeIntegrationService;

    public StripeIntegrationController(StripeIntegrationService stripeIntegrationService) {
        this.stripeIntegrationService = stripeIntegrationService;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        if (!stripeIntegrationService.isConfigured()) {
            return redirect(stripeIntegrationService.frontendCallbackUrl(false, "not_configured"));
        }
        if (!stripeIntegrationService.isConnectEnabled()) {
            return redirect(stripeIntegrationService.frontendCallbackUrl(true, null));
        }
        return redirect(stripeIntegrationService.buildAuthorizeUrl(userId));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null && !error.isBlank()) {
            return redirect(stripeIntegrationService.frontendCallbackUrl(false, error));
        }
        try {
            stripeIntegrationService.handleCallback(code, state);
            return redirect(stripeIntegrationService.frontendCallbackUrl(true, null));
        } catch (StripeNotConfiguredException ex) {
            return redirect(stripeIntegrationService.frontendCallbackUrl(false, "not_configured"));
        } catch (StripeIntegrationException ex) {
            return redirect(stripeIntegrationService.frontendCallbackUrl(false, "oauth_failed"));
        } catch (RuntimeException ex) {
            return redirect(stripeIntegrationService.frontendCallbackUrl(false, "oauth_failed"));
        }
    }

    @GetMapping
    public ResponseEntity<StripeStatusResponse> status(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(stripeIntegrationService.status(requireUserId(principal)));
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal UserPrincipal principal) {
        stripeIntegrationService.disconnect(requireUserId(principal));
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
