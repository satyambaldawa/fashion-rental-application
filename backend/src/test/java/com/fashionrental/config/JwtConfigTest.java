package com.fashionrental.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    private final JwtConfig jwtConfig = new JwtConfig(
            "test-signing-secret-key-must-be-at-least-256-bits-long-for-hs256", 24
    );

    @Test
    void should_extract_original_username_from_generated_token() {
        String token = jwtConfig.generateToken("owner1", "OWNER");

        assertThat(jwtConfig.extractUsername(token)).isEqualTo("owner1");
    }

    @Test
    void should_extract_original_role_from_generated_token() {
        String token = jwtConfig.generateToken("staff1", "EXECUTIVE");

        assertThat(jwtConfig.extractRole(token)).isEqualTo("EXECUTIVE");
    }

    @Test
    void should_validate_a_freshly_generated_token_as_true() {
        String token = jwtConfig.generateToken("owner1", "OWNER");

        assertThat(jwtConfig.validateToken(token)).isTrue();
    }

    @Test
    void should_invalidate_a_malformed_token() {
        assertThat(jwtConfig.validateToken("not-a-real-jwt")).isFalse();
    }

    @Test
    void should_invalidate_a_token_signed_with_a_different_secret() {
        JwtConfig otherIssuer = new JwtConfig("a-completely-different-signing-secret-key-256-bits-minimum", 24);
        String token = otherIssuer.generateToken("owner1", "OWNER");

        assertThat(jwtConfig.validateToken(token)).isFalse();
    }

    @Test
    void should_invalidate_an_expired_token() {
        JwtConfig alreadyExpired = new JwtConfig("test-signing-secret-key-must-be-at-least-256-bits-long-for-hs256", 0);
        String token = alreadyExpired.generateToken("owner1", "OWNER");

        assertThat(jwtConfig.validateToken(token)).isFalse();
    }

    @Test
    void should_throw_when_extracting_username_from_a_tampered_token() {
        String token = jwtConfig.generateToken("owner1", "OWNER");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtConfig.extractUsername(tampered)).isInstanceOf(RuntimeException.class);
    }
}
