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
}
