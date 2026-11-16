package com.back.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** 테스트 전용 고정 키 (비밀값 아님) */
    static final String STORAGE_ACCESS_KEY = "test-access-key";
    static final String STORAGE_SECRET_KEY = "test-secret-key";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17")
                .asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    }

    /** 파일 저장소 (S3 호환, DESIGN_REVIEW.md 6.8). */
    @Bean
    GenericContainer<?> rustfs() {
        return new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:1.0.6"))
                .withEnv("RUSTFS_ACCESS_KEY", STORAGE_ACCESS_KEY)
                .withEnv("RUSTFS_SECRET_KEY", STORAGE_SECRET_KEY)
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/").forPort(9000).forStatusCodeMatching(status -> status < 500));
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> rustfs) {
        return registry -> {
            registry.add("app.storage.endpoint",
                    () -> "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000));
            registry.add("app.storage.access-key", () -> STORAGE_ACCESS_KEY);
            registry.add("app.storage.secret-key", () -> STORAGE_SECRET_KEY);
        };
    }
}
