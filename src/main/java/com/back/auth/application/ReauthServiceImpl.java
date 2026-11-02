package com.back.auth.application;

import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.user.domain.User;
import com.back.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReauthServiceImpl implements ReauthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorService twoFactorService;
    private final LoginAttemptService loginAttempts;

    @Override
    @Transactional
    public void verify(long userId, String password, String code) {
        // 값을 아예 보내지 않은 요청은 추측 시도가 아니라 입력 누락이므로 로그인 실패 횟수에 넣지 않는다.
        // 넣으면 화면 버그나 잘못 만든 요청만으로 계정이 잠길 수 있다.
        if (isBlank(password) && isBlank(code)) {
            throw new BusinessException(ErrorCode.REAUTH_FAILED);
        }
        loginAttempts.assertNotLocked(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        boolean verified = user.isTwoFactorEnabled()
                ? twoFactorService.verifySecondFactor(user, code)
                : password != null && passwordEncoder.matches(password, user.getPasswordHash());
        if (!verified) {
            throw loginAttempts.recordFailure(userId, ErrorCode.REAUTH_FAILED);
        }
        loginAttempts.reset(userId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
