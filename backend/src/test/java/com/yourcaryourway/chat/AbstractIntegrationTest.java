package com.yourcaryourway.chat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base of the integration tests: full application on a random port, against a real PostgreSQL 18
 * started by Testcontainers (same engine as in production, schema created by Flyway).
 * Docker must be running to execute these tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.seed-demo-users=true")
abstract class AbstractIntegrationTest {

    // Container shared by all test classes (the Spring context is cached and reused):
    // it is started once and stopped by Testcontainers at the end of the JVM.
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    static {
        postgres.start();
    }

    @LocalServerPort
    protected int port;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanConversations() {
        jdbc.update("DELETE FROM message");
        jdbc.update("DELETE FROM conversation");
    }
}
