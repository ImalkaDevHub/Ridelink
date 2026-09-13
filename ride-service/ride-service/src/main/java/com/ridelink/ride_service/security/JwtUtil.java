package com.ridelink.ride_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

// This service never issues tokens - only Account Service does. jwt.secret
// must be kept identical to account-service's so tokens it signs validate
// here too.
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // Returns the parsed claims if the token is well-formed, unexpired and
    // correctly signed; null otherwise.
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isValid(String token) {
        return parseClaims(token) != null;
    }

    public Long getAccountId(Claims claims) {
        return claims.get("accountId", Long.class);
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
