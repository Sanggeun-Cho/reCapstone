package com.back.auth.application;

import com.back.auth.AuthProperties;
import com.back.auth.mail.AuthMail;
import com.back.auth.support.OneTimeTokenStore;
import com.back.auth.support.OneTimeTokenStore.Purpose;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.user.application.UserEvents;
import com.back.user.domain.User;
import com.back.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final OneTimeTokenStore tokenStore;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthProperties properties;

    @Override
    public void request(String email) {
        userRepository.findByEmail(User.normalizeEmail(email))
                .filter(User::isActive)
                .ifPresent(user -> {
                    String token = tokenStore.issue(Purpose.PASSWORD_RESET, user.getId(), properties.passwordResetTtl());
                    eventPublisher.publishEvent(new AuthMail.PasswordReset(user.getEmail(), token));
                });
    }

    @Override
    @Transactional
    public void confirm(String token, String newPassword) {
        User user = tokenStore.consume(Purpose.PASSWORD_RESET, token)
                .flatMap(userRepository::findById)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        loginAttempts.reset(user.getId());
        eventPublisher.publishEvent(new UserEvents.PasswordChanged(user.getId(), null));
    }
}
