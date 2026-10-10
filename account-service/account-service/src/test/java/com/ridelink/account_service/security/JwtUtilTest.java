package com.ridelink.account_service.security;

import com.ridelink.account_service.model.Account;
import com.ridelink.account_service.model.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-only-secret-test-only-secret-test-only-secret-0123456789");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 60_000L);
    }

    private Account account() {
        Account account = new Account();
        account.setId("acc-1");
        account.setEmail("nimal@example.com");
        account.setRole(Role.DRIVER);
        return account;
    }

    @Test
    void tokenCarriesAccountIdAsSubjectPlusEmailAndRole() {
        Claims claims = jwtUtil.parseClaims(jwtUtil.generateToken(account()));

        assertThat(claims.getSubject()).isEqualTo("acc-1");
        assertThat(claims.get("accountId", String.class)).isEqualTo("acc-1");
        assertThat(claims.get("email", String.class)).isEqualTo("nimal@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("DRIVER");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void tokenIsSignedWithHs256() {
        String header = jwtUtil.generateToken(account()).split("\\.")[0];
        String json = new String(Base64.getUrlDecoder().decode(header), StandardCharsets.UTF_8);

        assertThat(json).contains("\"alg\":\"HS256\"");
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1_000L);

        assertThat(jwtUtil.isValid(jwtUtil.generateToken(account()))).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtUtil.generateToken(account());

        String forged = token.substring(0, token.lastIndexOf('.') + 1) + "not-the-real-signature";

        assertThat(jwtUtil.isValid(forged)).isFalse();
    }
}
