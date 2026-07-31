package com.agenticform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.agenticform.model.entity.AuthProvider;

class OAuth2UserAttributesTest {

    @Test
    void azureTreatsMissingEmailVerifiedAsTrustedWhenEmailPresent() {
        OAuth2User user = oauthUser(Map.of(
                "sub", "msa-subject-1",
                "email", "ilyaas.95.jv@gmail.com",
                "name", "Ilyaas"));

        String email = OAuth2UserAttributes.extractEmail(user);
        assertEquals("ilyaas.95.jv@gmail.com", email);
        assertTrue(OAuth2UserAttributes.isEmailVerified(user, AuthProvider.AZURE, email));
    }

    @Test
    void azureHonorsExplicitFalseEmailVerified() {
        OAuth2User user = oauthUser(Map.of(
                "sub", "msa-subject-2",
                "email", "user@example.com",
                "email_verified", false));

        String email = OAuth2UserAttributes.extractEmail(user);
        assertFalse(OAuth2UserAttributes.isEmailVerified(user, AuthProvider.AZURE, email));
    }

    @Test
    void googleRequiresExplicitEmailVerified() {
        OAuth2User user = oauthUser(Map.of(
                "sub", "google-sub",
                "email", "user@gmail.com"));

        String email = OAuth2UserAttributes.extractEmail(user);
        assertFalse(OAuth2UserAttributes.isEmailVerified(user, AuthProvider.GOOGLE, email));

        OAuth2User verified = oauthUser(Map.of(
                "sub", "google-sub",
                "email", "user@gmail.com",
                "email_verified", true));
        assertTrue(OAuth2UserAttributes.isEmailVerified(
                verified, AuthProvider.GOOGLE, "user@gmail.com"));
    }

    @Test
    void extractEmailFallsBackToMicrosoftClaims() {
        assertEquals(
                "msa@outlook.com",
                OAuth2UserAttributes.extractEmail(oauthUser(Map.of(
                        "sub", "s1",
                        "preferred_username", "msa@outlook.com"))));

        assertEquals(
                "work@contoso.com",
                OAuth2UserAttributes.extractEmail(oauthUser(Map.of(
                        "sub", "s2",
                        "userPrincipalName", "work@contoso.com"))));

        assertEquals(
                "mail@contoso.com",
                OAuth2UserAttributes.extractEmail(oauthUser(Map.of(
                        "sub", "s3",
                        "mail", "mail@contoso.com"))));
    }

    @Test
    void extractEmailReturnsNullWhenNoValidAddress() {
        assertNull(OAuth2UserAttributes.extractEmail(oauthUser(Map.of(
                "sub", "s4",
                "preferred_username", "not-an-email"))));
    }

    private static OAuth2User oauthUser(Map<String, Object> attributes) {
        Map<String, Object> attrs = new HashMap<>(attributes);
        return new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                attrs,
                "sub");
    }
}
