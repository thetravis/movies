package com.example.movies;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test database: a throwaway PostgreSQL container (same version as docker-compose.yml) instead of
 * the local development database. Docker must be running.
 * <p>
 * Use with {@code @Import(TestcontainersConfiguration.class)}. {@code @ServiceConnection} points
 * the datasource to the container, so no connection properties are needed. The container is
 * started once and shared by all test classes; Liquibase creates the schema in it as usual.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
