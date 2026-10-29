package com.back.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트(레벨 I) 베이스. PostgreSQL(pgvector)과 Redis를 Testcontainers로 띄운다.
 * <p>
 * {@link ApiTest}와 애플리케이션 컨텍스트를 공유하도록 MockMvc 설정까지 같은 구성으로 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {
}
