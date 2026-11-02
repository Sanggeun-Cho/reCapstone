package com.back.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * 테스트에서 움직일 수 있는 시계. 테스트마다 현재 시각(초 단위)으로 초기화하고 그 자리에 멈춰 둔다.
 */
public class MutableClock extends Clock {

    private volatile Instant instant = now();

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }

    public void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    public void reset() {
        instant = now();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
