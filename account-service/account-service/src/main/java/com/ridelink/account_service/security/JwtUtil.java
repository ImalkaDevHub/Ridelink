package com.ridelink.account_service.security;

import com.ridelink.account_service.model.Account;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(Account account) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        // sub is the account id. "accountId" is kept as well because the
        // other three services read the id from that claim.
        return Jwts.builder()
                .subject(account.getId())
                .claim("accountId", account.getId())
                .claim("email", account.getEmail())
                .claim("role", account.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    // Returns the parsed claims if the token is well-formed, unexpired and
    // correctly signed; empty otherwise.
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

    public String getAccountId(Claims claims) {
        String accountId = claims.get("accountId", String.class);
        return accountId != null ? accountId : claims.getSubject();
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
