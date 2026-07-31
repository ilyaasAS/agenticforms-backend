package com.agenticform.security;

/**
 * Intent OAuth (login vs signup) — cookie + session pour survivre au round-trip Google.
 */
public final class OAuthIntent {

    public static final String SESSION_ATTRIBUTE = "OAUTH2_AUTH_INTENT";
    public static final String COOKIE_NAME = "AF_OAUTH_INTENT";
    public static final String SIGNUP = "signup";
    public static final String LOGIN = "login";

    private OAuthIntent() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return LOGIN;
        }
        String value = raw.trim().toLowerCase();
        return SIGNUP.equals(value) ? SIGNUP : LOGIN;
    }
}
