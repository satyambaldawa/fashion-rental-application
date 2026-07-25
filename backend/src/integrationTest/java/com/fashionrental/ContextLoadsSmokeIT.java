package com.fashionrental;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: if the Spring context fails to start against real PostgreSQL — broken bean
 * wiring, a failed Flyway migration, or a ddl-auto:validate schema/entity mismatch — this
 * test fails on every PR, before anything reaches deploy.
 */
class ContextLoadsSmokeIT extends AbstractIntegrationTest {

    @Test
    void context_starts_and_migrations_apply() {
        // Intentionally empty: @SpringBootTest boots the full context and runs Flyway.
    }
}
