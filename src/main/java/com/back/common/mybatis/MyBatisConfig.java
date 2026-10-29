package com.back.common.mybatis;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis는 목록·화면 조회 전용이다. 매퍼는 기능 패키지의 {@code query} 하위에 둔다.
 */
@Configuration(proxyBeanMethods = false)
public class MyBatisConfig {

    @Bean
    public ReadOnlyInterceptor readOnlyInterceptor() {
        return new ReadOnlyInterceptor();
    }

    /**
     * INSERT·UPDATE·DELETE 매핑 실행을 거부한다. 쓰기는 JPA가 맡는다.
     */
    @Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
    public static class ReadOnlyInterceptor implements Interceptor {

        @Override
        public Object intercept(Invocation invocation) {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            throw new UnsupportedOperationException(
                    "MyBatis is read-only; " + statement.getSqlCommandType() + " is not allowed: " + statement.getId());
        }
    }
}
