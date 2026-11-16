package com.back.group;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 그룹·스페이스 설정 (정책 제2장, 제3장). 값을 비워 두면 정책에 정한 기본값을 쓴다.
 *
 * @param invitationTtl      초대 유효 기간 (정책 2.4 제4항)
 * @param deletionRequestTtl 스페이스 삭제 요청 유효 기간 (정책 3.4 제4항)
 * @param retention          삭제 후 보관 기간 (정책 2.6 제1항)
 */
@ConfigurationProperties("app.group")
public record GroupProperties(Duration invitationTtl, Duration deletionRequestTtl, Duration retention) {

    public GroupProperties {
        invitationTtl = invitationTtl == null ? Duration.ofDays(7) : invitationTtl;
        deletionRequestTtl = deletionRequestTtl == null ? Duration.ofDays(7) : deletionRequestTtl;
        retention = retention == null ? Duration.ofDays(30) : retention;
    }
}
