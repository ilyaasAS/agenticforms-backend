package com.agenticform.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.util.StringUtils;

/**
 * Enregistre Google / Azure uniquement si les Client ID/Secret sont fournis.
 * Permet de démarrer l'app sans clés OAuth (flux email/mot de passe intact).
 */
@Configuration
public class OAuth2ClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
            @Value("${AZURE_CLIENT_ID:}") String azureClientId,
            @Value("${AZURE_CLIENT_SECRET:}") String azureClientSecret,
            @Value("${AZURE_TENANT_ID:common}") String azureTenantId) {

        List<ClientRegistration> registrations = new ArrayList<>();

        if (StringUtils.hasText(googleClientId) && StringUtils.hasText(googleClientSecret)) {
            registrations.add(googleRegistration(googleClientId, googleClientSecret));
        }

        if (StringUtils.hasText(azureClientId) && StringUtils.hasText(azureClientSecret)) {
            registrations.add(azureRegistration(azureClientId, azureClientSecret, azureTenantId));
        }

        if (registrations.isEmpty()) {
            // Repository non vide requis par Spring ; registration factice jamais exposée
            // tant que oauth2Login n'est activé que si hasRealOAuthClients().
            registrations.add(placeholderRegistration());
        }

        return new InMemoryClientRegistrationRepository(registrations);
    }

    @Bean
    public OAuth2ClientsAvailability oauth2ClientsAvailability(
            @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
            @Value("${AZURE_CLIENT_ID:}") String azureClientId,
            @Value("${AZURE_CLIENT_SECRET:}") String azureClientSecret) {
        boolean google = StringUtils.hasText(googleClientId) && StringUtils.hasText(googleClientSecret);
        boolean azure = StringUtils.hasText(azureClientId) && StringUtils.hasText(azureClientSecret);
        return new OAuth2ClientsAvailability(google, azure);
    }

    private ClientRegistration googleRegistration(String clientId, String clientSecret) {
        // CommonOAuth2Provider inclut jwkSetUri (obligatoire pour valider l'ID Token OIDC).
        return CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .build();
    }

    private ClientRegistration azureRegistration(String clientId, String clientSecret, String tenantId) {
        String tenant = StringUtils.hasText(tenantId) ? tenantId : "common";
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId("azure")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize")
                .tokenUri("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token")
                .jwkSetUri("https://login.microsoftonline.com/" + tenant + "/discovery/v2.0/keys")
                .userInfoUri("https://graph.microsoft.com/oidc/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Microsoft");

        // Multi-tenant (common/organizations/consumers) : l'issuer du ID token est
        // tenant-spécifique — ne pas figer issuerUri sur .../common/v2.0.
        if (!isMultiTenantAzure(tenant)) {
            builder.issuerUri("https://login.microsoftonline.com/" + tenant + "/v2.0");
        }

        return builder.build();
    }

    private boolean isMultiTenantAzure(String tenant) {
        return "common".equalsIgnoreCase(tenant)
                || "organizations".equalsIgnoreCase(tenant)
                || "consumers".equalsIgnoreCase(tenant);
    }

    private ClientRegistration placeholderRegistration() {
        return ClientRegistration.withRegistrationId("disabled")
                .clientId("disabled")
                .clientSecret("disabled")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.com/oauth2/authorize")
                .tokenUri("https://example.com/oauth2/token")
                .userNameAttributeName("sub")
                .clientName("Disabled")
                .build();
    }

    public record OAuth2ClientsAvailability(boolean googleEnabled, boolean azureEnabled) {
        public boolean anyEnabled() {
            return googleEnabled || azureEnabled;
        }
    }
}
