package com.back.user.domain;

import com.back.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "users")
@SQLRestriction(BaseEntity.NOT_DELETED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    private String workPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    private Instant emailVerifiedAt;

    private String totpSecret;

    private Instant totpEnabledAt;

    private Long totpLastStep;

    private User(String email, String passwordHash, String name, String workPhone) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.workPhone = workPhone;
        this.status = UserStatus.PENDING;
    }

    /**
     * 인증 대기 상태의 계정을 만든다. 이메일은 {@link #normalizeEmail(String)}로 정규화한 값이어야 한다.
     */
    public static User pending(String email, String passwordHash, String name, String workPhone) {
        return new User(email, passwordHash, name, workPhone);
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * 인증 대기 계정이 인증 기한을 넘겼는지. 넘긴 계정은 없는 것으로 취급한다 (정책 7.1).
     */
    public boolean isPendingExpired(Instant now, Duration verificationTtl) {
        return status == UserStatus.PENDING && !now.isBefore(getCreatedAt().plus(verificationTtl));
    }

    public void verifyEmail(Instant at) {
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = at;
    }

    /**
     * 기한이 지난 인증 대기 계정을 치워 같은 이메일로 다시 가입할 수 있게 한다.
     */
    public void discardExpiredPending(Instant at) {
        softDelete(at);
    }

    public void updateProfile(String name, String workPhone) {
        this.name = name;
        this.workPhone = workPhone;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isTwoFactorEnabled() {
        return totpEnabledAt != null;
    }

    public boolean hasTwoFactorSecret() {
        return totpSecret != null;
    }

    /**
     * 2단계 인증 등록을 시작한다. 확인({@link #enableTwoFactor})하기 전까지는 꺼진 상태다.
     */
    public void startTwoFactorSetup(String encryptedSecret) {
        this.totpSecret = encryptedSecret;
        this.totpEnabledAt = null;
        this.totpLastStep = null;
    }

    public void enableTwoFactor(Instant at, long usedStep) {
        this.totpEnabledAt = at;
        this.totpLastStep = usedStep;
    }

    public void disableTwoFactor() {
        this.totpSecret = null;
        this.totpEnabledAt = null;
        this.totpLastStep = null;
    }

    public void markTotpStepUsed(long step) {
        this.totpLastStep = step;
    }

    /**
     * 탈퇴. 이름과 이메일은 증빙으로 남긴다 (정책 7.8, 7.9).
     */
    public void withdraw(Instant at) {
        softDelete(at);
    }
}
