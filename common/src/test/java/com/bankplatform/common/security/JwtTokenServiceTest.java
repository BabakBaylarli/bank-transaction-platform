package com.bankplatform.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final JwtProperties properties = new JwtProperties(SECRET, "test-issuer", Duration.ofMinutes(30));
    private final JwtTokenService service = new JwtTokenService(properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void issuedTokenCanBeParsedBack() {
        var user = new AuthenticatedUser(UUID.randomUUID(), "alice", Role.CUSTOMER);

        var issued = service.issue(user);

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(service.parse(issued.token())).contains(user);
    }

    @Test
    void rejectsExpiredToken() {
        var token = service.issue(new AuthenticatedUser(UUID.randomUUID(), "bob", Role.ADMIN)).token();
        var later = new JwtTokenService(properties, Clock.fixed(NOW.plus(Duration.ofHours(2)), ZoneOffset.UTC));

        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        var other = new JwtTokenService(
                new JwtProperties("another-secret-another-secret-another-1", "test-issuer", Duration.ofMinutes(5)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var token = other.issue(new AuthenticatedUser(UUID.randomUUID(), "eve", Role.ADMIN)).token();

        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(service.parse("not-a-jwt")).isEmpty();
    }
}
