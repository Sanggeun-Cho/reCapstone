package com.back.auth.application;

import com.back.auth.AuthProperties;
import com.back.auth.api.AuthDto;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.user.domain.User;
import com.back.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final TwoFactorService twoFactorService;
    private final AuthProperties properties;
    private final Clock clock;

    // 없는 이메일에도 해시 비교를 한 번 해서, 응답 시간으로 가입 여부가 드러나지 않게 한다.
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttempts, TwoFactorService twoFactorService,
            AuthProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.twoFactorService = twoFactorService;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer");
    }

    @Override
    public LoginResult login(String email, String password) {
        Instant now = clock.instant();
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(email))
                .filter(user -> !user.isPendingExpired(now, properties.emailVerificationTtl()));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = found.get();
        loginAttempts.assertNotLocked(user.getId());
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw loginAttempts.recordFailure(user.getId(), ErrorCode.INVALID_CREDENTIALS);
        }
        // 비밀번호가 맞은 뒤에만 인증 대기 상태를 알려 준다.
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (user.isTwoFactorEnabled()) {
            return new LoginResult(user.getId(), true, null);
        }
        loginAttempts.reset(user.getId());
        return new LoginResult(user.getId(), false, AuthDto.UserSummary.from(user));
    }

    @Override
    @Transactional
    public AuthDto.UserSummary completeMfa(long userId, String code) {
        loginAttempts.assertNotLocked(userId);
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.MFA_SESSION_EXPIRED));
        if (!twoFactorService.verifySecondFactor(user, code)) {
            throw loginAttempts.recordFailure(userId, ErrorCode.INVALID_MFA_CODE);
        }
        loginAttempts.reset(userId);
        return AuthDto.UserSummary.from(user);
    }
}
