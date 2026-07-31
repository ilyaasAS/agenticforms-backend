package com.agenticform.security;

import java.util.Map;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.agenticform.model.entity.AuthProvider;

/**
 * Extraction / normalisation des attributs IdP (Google, Microsoft Entra / Live).
 * <p>
 * Microsoft (MSA / Entra) omet souvent le claim {@code email_verified}, surtout pour
 * les comptes personnels liés à une adresse externe (ex. {@code @gmail.com}). Dans
 * ce cas, une authentification OIDC réussie + e-mail issu de claims de confiance
 * suffit à considérer l'adresse comme vérifiée côté IdP.
 */
public final class OAuth2UserAttributes {

    private OAuth2UserAttributes() {
    }

    /**
     * Google : exige {@code email_verified=true}.
     * Azure / Microsoft : honore le claim s'il est présent ; s'il est absent,
     * fait confiance à l'identité Microsoft authentifiée dès qu'un e-mail valide
     * a été extrait des claims IdP.
     */
    public static boolean isEmailVerified(
            OAuth2User user,
            AuthProvider provider,
            String extractedEmail) {
        if (extractedEmail == null || extractedEmail.isBlank()) {
            return false;
        }

        Boolean explicit = readExplicitEmailVerified(user);
        if (Boolean.TRUE.equals(explicit)) {
            return true;
        }
        if (Boolean.FALSE.equals(explicit)) {
            return false;
        }

        // Claim absent
        if (provider == AuthProvider.AZURE) {
            return true;
        }
        return false;
    }

    public static String extractEmail(OAuth2User user) {
        if (user instanceof OidcUser oidcUser) {
            String oidcEmail = oidcUser.getEmail();
            if (oidcEmail != null && !oidcEmail.isBlank()) {
                return normalizeEmail(oidcEmail);
            }
            String preferred = oidcUser.getPreferredUsername();
            if (isValidEmailAddress(preferred)) {
                return normalizeEmail(preferred);
            }
        }

        Map<String, Object> attributes = user.getAttributes();
        for (String key : new String[] {
                "email",
                "preferred_username",
                "upn",
                "userPrincipalName",
                "mail"
        }) {
            Object value = attributes.get(key);
            if (value instanceof String str && isValidEmailAddress(str)) {
                return normalizeEmail(str);
            }
        }
        return null;
    }

    public static String extractSubject(OAuth2User user) {
        if (user instanceof OidcUser oidcUser) {
            String sub = oidcUser.getSubject();
            if (sub != null && !sub.isBlank()) {
                return sub.trim();
            }
        }
        Object sub = user.getAttributes().get("sub");
        if (sub instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        String name = user.getName();
        return name == null || name.isBlank() ? null : name.trim();
    }

    public static String extractFullName(OAuth2User user) {
        if (user instanceof OidcUser oidcUser) {
            String fullName = oidcUser.getFullName();
            if (fullName != null && !fullName.isBlank()) {
                return fullName.trim();
            }
        }

        Map<String, Object> attributes = user.getAttributes();
        Object name = attributes.get("name");
        if (name instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        String given = attributes.get("given_name") instanceof String g ? g : "";
        String family = attributes.get("family_name") instanceof String f ? f : "";
        String combined = (given + " " + family).trim();
        return combined.isEmpty() ? null : combined;
    }

    private static Boolean readExplicitEmailVerified(OAuth2User user) {
        if (user instanceof OidcUser oidcUser) {
            Boolean verified = oidcUser.getEmailVerified();
            if (verified != null) {
                return verified;
            }
        }

        Object claim = user.getAttributes().get("email_verified");
        if (claim instanceof Boolean bool) {
            return bool;
        }
        if (claim instanceof String str) {
            if ("true".equalsIgnoreCase(str)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(str)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    static boolean isValidEmailAddress(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String email = value.trim();
        int at = email.indexOf('@');
        return at > 0 && at < email.length() - 1 && email.indexOf('@', at + 1) < 0;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
