package com.back.auth.application;

import com.back.auth.api.TwoFactorDto;
import com.back.auth.domain.RecoveryCode;
import com.back.auth.domain.RecoveryCodeRepository;
import com.back.auth.support.Hashing;
import com.back.auth.support.RecoveryCodes;
import com.back.auth.support.SecretCipher;
import com.back.auth.support.Totp;
import com.back.common.error.BusinessException;
import com.back.common.error.ErrorCode;
import com.back.user.domain.User;
import com.back.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TwoFactorServiceImpl implements TwoFactorService {

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final SecretCipher secretCipher;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final Clock clock;

    @Override
    @Transactional
    public TwoFactorDto.SetupRes setup(long userId) {
        User user = getUser(userId);
        if (user.isTwoFactorEnabled()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_ALREADY_ENABLED);
        }
        byte[] secret = Totp.newSecret();
        user.startTwoFactorSetup(secretCipher.encrypt(secret));
        return new TwoFactorDto.SetupRes(Totp.base32(secret), Totp.otpauthUri(user.getEmail(), secret));
    }

    @Override
    @Transactional
    public TwoFactorDto.RecoveryCodesRes enable(long userId, String code) {
        User user = getUser(userId);
        if (user.isTwoFactorEnabled()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_ALREADY_ENABLED);
        }
        if (!user.hasTwoFactorSecret()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_SETUP_REQUIRED);
        }
        Instant now = clock.instant();
        OptionalLong step = Totp.verify(secretCipher.decrypt(user.getTotpSecret()), code, now, null);
        if (step.isEmpty()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_CODE_MISMATCH);
        }
        user.enableTwoFactor(now, step.getAsLong());
        return new TwoFactorDto.RecoveryCodesRes(issueRecoveryCodes(userId, now));
    }

    @Override
    @Transactional
    public void disable(long userId, String password, String code) {
        loginAttempts.assertNotLocked(userId);
        User user = getUser(userId);
        if (!user.isTwoFactorEnabled()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_NOT_ENABLED);
        }
        // 비밀번호가 틀리면 코드를 확인하지 않는다. 복구 코드를 헛되이 소모하지 않기 위함이다.
        if (!passwordEncoder.matches(password, user.getPasswordHash()) || !verifySecondFactor(user, code)) {
            throw loginAttempts.recordFailure(userId, ErrorCode.REAUTH_FAILED);
        }
        loginAttempts.reset(userId);
        user.disableTwoFactor();
        discardRecoveryCodes(userId, clock.instant());
    }

    @Override
    @Transactional
    public TwoFactorDto.RecoveryCodesRes regenerateRecoveryCodes(long userId, String code) {
        loginAttempts.assertNotLocked(userId);
        User user = getUser(userId);
        if (!user.isTwoFactorEnabled()) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_NOT_ENABLED);
        }
        // 복구 코드로 복구 코드를 다시 받을 수는 없다. 인증 앱의 코드만 받는다.
        if (!verifyTotp(user, code)) {
            throw loginAttempts.recordFailure(userId, ErrorCode.REAUTH_FAILED);
        }
        loginAttempts.reset(userId);
        return new TwoFactorDto.RecoveryCodesRes(issueRecoveryCodes(userId, clock.instant()));
    }

    @Override
    public boolean verifySecondFactor(User user, String code) {
        if (!user.isTwoFactorEnabled() || code == null) {
            return false;
        }
        String trimmed = code.strip();
        return trimmed.matches("\\d{" + Totp.DIGITS + "}") ? verifyTotp(user, trimmed) : useRecoveryCode(user, trimmed);
    }

    private boolean verifyTotp(User user, String code) {
        if (code == null) {
            return false;
        }
        OptionalLong step = Totp.verify(
                secretCipher.decrypt(user.getTotpSecret()), code.strip(), clock.instant(), user.getTotpLastStep());
        step.ifPresent(user::markTotpStepUsed);
        return step.isPresent();
    }

    private boolean useRecoveryCode(User user, String code) {
        String hash = RecoveryCodes.hash(code);
        for (RecoveryCode candidate : recoveryCodeRepository.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (Hashing.constantTimeEquals(candidate.getCodeHash(), hash)) {
                candidate.use(clock.instant());
                return true;
            }
        }
        return false;
    }

    private List<String> issueRecoveryCodes(long userId, Instant now) {
        discardRecoveryCodes(userId, now);
        List<String> codes = RecoveryCodes.generate();
        recoveryCodeRepository.saveAll(codes.stream()
                .map(code -> new RecoveryCode(userId, RecoveryCodes.hash(code)))
                .toList());
        return codes;
    }

    private void discardRecoveryCodes(long userId, Instant now) {
        recoveryCodeRepository.findByUserId(userId).forEach(code -> code.discard(now));
    }

    private User getUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }
}
