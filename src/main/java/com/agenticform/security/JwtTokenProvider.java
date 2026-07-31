package com.agenticform.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.agenticform.model.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

/**
 * JWT d'accès : subject = userId immuable ; claim {@code tv} = tokenVersion (révocation).
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String generateToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User id required for JWT subject");
        }
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        String subject = parseClaims(token).getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid JWT subject (expected userId)", ex);
        }
    }

    /**
     * Version de session portée par le JWT. Absent (tokens legacy) → 0.
     */
    public int getTokenVersionFromToken(String token) {
        Object claim = parseClaims(token).get(CLAIM_TOKEN_VERSION);
        if (claim instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    public boolean validateToken(String token) {
        try {
            getUserIdFromToken(token);
            return true;
        } catch (ExpiredJwtException
                 | MalformedJwtException
                 | UnsupportedJwtException
                 | SignatureException
                 | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
