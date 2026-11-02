package com.back.auth.application;

import com.back.auth.api.TwoFactorDto;
import com.back.user.domain.User;

/**
 * 2단계 인증 (정책 7.3).
 */
public interface TwoFactorService {

    /** 등록을 시작한다. 비밀키를 만들어 암호화해 두고, 확인하기 전까지는 꺼진 상태다. */
    TwoFactorDto.SetupRes setup(long userId);

    /** 인증 앱의 코드로 등록을 확인해 켜고, 복구 코드를 발급한다. */
    TwoFactorDto.RecoveryCodesRes enable(long userId, String code);

    /** 비밀번호와 코드(일회용 코드 또는 복구 코드)를 함께 확인해 끈다. */
    void disable(long userId, String password, String code);

    /** 일회용 코드를 확인해 복구 코드를 다시 발급한다. 이전 코드는 모두 무효가 된다. */
    TwoFactorDto.RecoveryCodesRes regenerateRecoveryCodes(long userId, String code);

    /**
     * 일회용 코드나 복구 코드를 확인하고, 맞으면 사용한 것으로 기록한다. 쓰기 트랜잭션 안에서 부른다.
     * 실패 횟수 기록은 호출자가 한다.
     */
    boolean verifySecondFactor(User user, String code);
}
