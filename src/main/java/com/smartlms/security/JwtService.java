package com.smartlms.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Handles JWT token creation, parsing, and validation.
 * <p>
 * The signing key and expiration are read from application.yml
 * (which delegates to environment variables in production).
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    // ── Public API ──────────────────────────────────────────

    /**
     * Generate a JWT token for the given user.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generate a JWT token with extra claims.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Extract the username (email) from a token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Validate token against a UserDetails instance.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // ── Internal helpers ────────────────────────────────────

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private SecretKey getSigningKey() {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT secret key must not be null or empty. Please configure 'jwt.secret' as a 64-character (256-bit) hexadecimal string.");
        }

        byte[] keyBytes;
        try {
            keyBytes = HexFormat.of().parseHex(secretKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Failed to decode JWT secret: 'jwt.secret' must be a valid hexadecimal string (0-9, a-f, A-F). " + e.getMessage(), e);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "Invalid JWT secret length: decoded key is " + (keyBytes.length * 8) +
                    " bits (" + keyBytes.length + " bytes), but HMAC-SHA256 requires at least 256 bits (32 bytes). " +
                    "Please configure 'jwt.secret' as a 64-character (or longer) hexadecimal string."
            );
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}
