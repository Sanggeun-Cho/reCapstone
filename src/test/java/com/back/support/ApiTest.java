package com.back.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 테스트(레벨 A) 베이스. 보안 필터를 포함한 MockMvc로 인가·직렬화를 검증한다.
 */
public abstract class ApiTest extends IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
}
