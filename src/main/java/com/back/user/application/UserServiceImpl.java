package com.back.user.application;

import com.back.auth.AuthProperties;
import com.back.auth.application.EmailVerificationService;
import com.back.auth.application.LoginAttemptService;
import com.back.auth.application.ReauthService;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.user.api.UserDto;
import com.back.user.domain.User;
import com.back.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final ReauthService reauthService;
    private final LoginAttemptService loginAttempts;
    private final List<WithdrawalGuard> withdrawalGuards;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public UserDto.SignupRes signup(UserDto.SignupReq req) {
        String email = User.normalizeEmail(req.email());
        Instant now = clock.instant();
        userRepository.findByEmail(email).ifPresent(existing -> {
            if (existing.isActive()) {
                throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
            }
            if (!existing.isPendingExpired(now, properties.emailVerificationTtl())) {
                throw new BusinessException(ErrorCode.EMAIL_PENDING_VERIFICATION);
            }
            existing.discardExpiredPending(now);
            // 부분 유니크 인덱스가 새 행과 충돌하지 않도록 soft delete를 먼저 반영한다.
            userRepository.flush();
        });

        User user = userRepository.save(User.pending(
                email, passwordEncoder.encode(req.password()), req.name().strip(), blankToNull(req.workPhone())));
        emailVerificationService.send(user.getId(), user.getEmail());
        return UserDto.SignupRes.from(user);
    }

    @Override
    public UserDto.DetailRes getMe(long userId) {
        return UserDto.DetailRes.from(getUser(userId));
    }

    @Override
    @Transactional
    public UserDto.DetailRes updateMe(long userId, UserDto.UpdateReq req) {
        User user = getUser(userId);
        user.updateProfile(req.name().strip(), blankToNull(req.workPhone()));
        return UserDto.DetailRes.from(user);
    }

    @Override
    @Transactional
    public void changePassword(long userId, UserDto.ChangePasswordReq req, String currentSessionId) {
        loginAttempts.assertNotLocked(userId);
        User user = getUser(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw loginAttempts.recordFailure(userId, ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        loginAttempts.reset(userId);
        user.changePasswordHash(passwordEncoder.encode(req.newPassword()));
        eventPublisher.publishEvent(new UserEvents.PasswordChanged(userId, currentSessionId));
    }

    @Override
    @Transactional
    public void withdraw(long userId, String password, String code) {
        reauthService.verify(userId, password, code);
        withdrawalGuards.forEach(guard -> guard.check(userId));
        getUser(userId).withdraw(clock.instant());
        eventPublisher.publishEvent(new UserEvents.Withdrawn(userId));
    }

    private User getUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
