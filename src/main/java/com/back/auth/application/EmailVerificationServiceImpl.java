package com.back.auth.application;

import com.back.auth.AuthProperties;
import com.back.auth.api.AuthDto;
import com.back.auth.mail.AuthMail;
import com.back.auth.support.OneTimeTokenStore;
import com.back.auth.support.OneTimeTokenStore.Purpose;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.user.domain.User;
import com.back.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final UserRepository userRepository;
    private final OneTimeTokenStore tokenStore;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthProperties properties;
    private final Clock clock;

    @Override
    public void send(long userId, String email) {
        String token = tokenStore.issue(Purpose.EMAIL_VERIFICATION, userId, properties.emailVerificationTtl());
        eventPublisher.publishEvent(new AuthMail.EmailVerification(email, token));
    }

    @Override
    @Transactional
    public AuthDto.EmailVerificationRes verify(String token) {
        Instant now = clock.instant();
        User user = tokenStore.consume(Purpose.EMAIL_VERIFICATION, token)
                .flatMap(userRepository::findById)
                .filter(found -> !found.isActive() && !found.isPendingExpired(now, properties.emailVerificationTtl()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        user.verifyEmail(now);
        return new AuthDto.EmailVerificationRes(user.getStatus());
    }

    @Override
    public void resend(String email) {
        Instant now = clock.instant();
        userRepository.findByEmail(User.normalizeEmail(email))
                .filter(user -> !user.isActive() && !user.isPendingExpired(now, properties.emailVerificationTtl()))
                .ifPresent(user -> send(user.getId(), user.getEmail()));
    }
}
