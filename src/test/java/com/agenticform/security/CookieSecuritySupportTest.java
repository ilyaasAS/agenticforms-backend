package com.agenticform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CookieSecuritySupportTest {

    @Test
    void trustsLoopbackAndPrivateCidrs() {
        CookieSecuritySupport support = new CookieSecuritySupport(false,
                "127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16");

        assertTrue(support.isFromTrustedProxy("127.0.0.1"));
        assertTrue(support.isFromTrustedProxy("10.0.0.5"));
        assertTrue(support.isFromTrustedProxy("172.18.0.3"));
        assertTrue(support.isFromTrustedProxy("192.168.1.10"));
        assertFalse(support.isFromTrustedProxy("8.8.8.8"));
        assertFalse(support.isFromTrustedProxy(null));
    }
}
