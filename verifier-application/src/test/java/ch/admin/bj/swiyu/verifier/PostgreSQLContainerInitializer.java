package ch.admin.bj.swiyu.verifier;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class PostgreSQLContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final String POSTGRES_IMAGE_NAME = "postgres:15-alpine";
    private static PostgreSQLContainer<?> database;

    private static PostgreSQLContainer<?> getDatabase() {
        if (database == null) {
            try {
                database = new PostgreSQLContainer<>(
                        DockerImageName.parse(POSTGRES_IMAGE_NAME)
                );
                database.start();
            } catch (ContainerLaunchException e) {
                log.warn("Failed to start default PostgreSQL container. Attempting alternative source", e);
                database = new PostgreSQLContainer<>(DockerImageName.parse("docker-hub.nexus.bit.admin.ch/%s".formatted(POSTGRES_IMAGE_NAME))
                        .asCompatibleSubstituteFor(POSTGRES_IMAGE_NAME));
                database.start();
            }
            log.info("PostgreSQL container started at: {}", database.getJdbcUrl());
        }
        return database;
    }

    public void initialize(@NotNull ConfigurableApplicationContext configurableApplicationContext) {
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                configurableApplicationContext,
                "spring.datasource.url=" + getDatabase().getJdbcUrl(),
                "spring.datasource.username=" + getDatabase().getUsername(),
                "spring.datasource.password=" + getDatabase().getPassword()
        );
    }

}
