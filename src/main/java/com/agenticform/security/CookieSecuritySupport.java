package com.agenticform.security;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Détermine si les cookies doivent porter le flag Secure.
 * {@code X-Forwarded-Proto} n'est honoré que si {@code remoteAddr} appartient
 * à un proxy de confiance (réseaux privés Docker / localhost), jamais depuis un client direct.
 */
@Component
public class CookieSecuritySupport {

    private final boolean forceSecure;
    private final List<String> trustedProxyCidrs;

    public CookieSecuritySupport(
            @Value("${app.cookies.force-secure:false}") boolean forceSecure,
            @Value("${app.cookies.trusted-proxies:127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
            String trustedProxies) {
        this.forceSecure = forceSecure;
        this.trustedProxyCidrs = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean isSecureRequest(HttpServletRequest request) {
        if (forceSecure) {
            return true;
        }

        if (isFromTrustedProxy(request.getRemoteAddr())) {
            String forwarded = request.getHeader("X-Forwarded-Proto");
            if (forwarded != null && !forwarded.isBlank()) {
                String proto = forwarded.split(",")[0].trim();
                if ("https".equalsIgnoreCase(proto)) {
                    return true;
                }
                if ("http".equalsIgnoreCase(proto)) {
                    return false;
                }
            }
        }

        return request.isSecure();
    }

    boolean isFromTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remoteAddr.trim());
            for (String cidr : trustedProxyCidrs) {
                if (matchesCidr(address, cidr)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private static boolean matchesCidr(InetAddress address, String cidr) throws Exception {
        if (!cidr.contains("/")) {
            return address.equals(InetAddress.getByName(cidr))
                    || (address.isLoopbackAddress() && "127.0.0.1".equals(cidr));
        }
        String[] parts = cidr.split("/", 2);
        InetAddress network = InetAddress.getByName(parts[0]);
        int prefix = Integer.parseInt(parts[1]);
        byte[] addr = address.getAddress();
        byte[] net = network.getAddress();
        if (addr.length != net.length) {
            return false;
        }
        int fullBytes = prefix / 8;
        int remBits = prefix % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (addr[i] != net[i]) {
                return false;
            }
        }
        if (remBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remBits);
        return (addr[fullBytes] & mask) == (net[fullBytes] & mask);
    }
}
