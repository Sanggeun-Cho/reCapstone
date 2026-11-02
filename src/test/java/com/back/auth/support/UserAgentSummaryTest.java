package com.back.auth.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class UserAgentSummaryTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36 | Chrome / Windows",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36 Edg/130.0 | Edge / Windows",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15 | Safari / macOS",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1 | Safari / iOS",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36 | Chrome / Android",
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0 | Firefox / Linux",
            "curl/8.5.0 | 기타 / 기타"
    })
    void summarizes(String userAgent, String expected) {
        assertThat(UserAgentSummary.of(userAgent)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void unknownWhenMissing(String userAgent) {
        assertThat(UserAgentSummary.of(userAgent)).isEqualTo("기타 / 기타");
    }
}
