package com.agenticform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TokenHashUtilsTest {

    @Test
    void sha256IsDeterministic() {
        String a = TokenHashUtils.sha256Hex("token-abc");
        String b = TokenHashUtils.sha256Hex("token-abc");
        assertEquals(a, b);
        assertEquals(64, a.length());
    }
}
