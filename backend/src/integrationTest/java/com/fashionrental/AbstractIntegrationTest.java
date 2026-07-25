package com.fashionrental;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests: boots the full Spring context against a real
 * PostgreSQL started by Testcontainers, so Flyway migrations, entity/schema mapping,
 * and every @Query actually execute — the things mock-based unit tests never exercise.
 *
 * <p>Uses the singleton-container pattern: one container is started in a static
 * initializer and shared across every integration-test class (started once per JVM,
 * not per class), which keeps the suite fast and avoids per-class teardown races.
 *
 * <p>CI: GitHub-hosted runners ship with Docker, so this runs with no extra setup.
 * Local (podman): export DOCKER_HOST=&lt;podman socket&gt; and TESTCONTAINERS_RYUK_DISABLED=true
 * (see backend/README).
 */
@SpringBootTest
@ActiveProfiles("integrationtest")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
