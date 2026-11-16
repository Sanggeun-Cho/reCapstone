package com.back.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 테스트에서 실행된 SQL 수를 센다 (NFR-04: 목록 조회의 쿼리 수가 건수와 관계없이 일정한지).
 * JPA와 MyBatis가 모두 같은 {@link DataSource}를 쓰므로 둘 다 센다. 배치로 묶인 SQL은 실행 1회로 센다.
 * <p>
 * {@link #reset()}을 부른 스레드에서 실행된 SQL만 센다. MockMvc 요청은 테스트 스레드에서 처리되므로 요청의 SQL은 모두 잡히고,
 * 스케줄 작업이나 비동기 리스너처럼 다른 스레드에서 같은 때에 실행된 SQL은 측정에 섞이지 않는다.
 */
public class QueryCounter implements QueryExecutionListener {

    private final AtomicInteger count = new AtomicInteger();

    /** 측정하는 스레드. {@link #reset()} 전에는 아무것도 세지 않는다. */
    private volatile Thread owner;

    /** 측정 구간의 SQL 수. 측정 전에 {@link #reset()}을 부른다. */
    public int count() {
        return count.get();
    }

    /** 카운트를 0으로 만들고, 이 메서드를 부른 스레드를 측정 대상으로 정한다. */
    public void reset() {
        owner = Thread.currentThread();
        count.set(0);
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        if (Thread.currentThread() == owner) {
            count.incrementAndGet();
        }
    }

    /** 애플리케이션의 {@link DataSource}를 감싸 이 카운터에 실행을 알린다. */
    public BeanPostProcessor dataSourceWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
                    return ProxyDataSourceBuilder.create(dataSource).name(beanName).listener(QueryCounter.this).build();
                }
                return bean;
            }
        };
    }
}
