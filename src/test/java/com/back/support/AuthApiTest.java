package com.back.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.auth.support.Totp;
import com.back.user.domain.User;
import com.back.user.domain.UserRepository;
import com.icegreen.greenmail.util.GreenMail;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 회원·인증 API 테스트 베이스. 사용자 생성, 로그인, 메일 토큰 추출, TOTP 코드 계산을 돕는다.
 * 테스트마다 사용자 테이블, Redis(세션·토큰·잠금), 받은 메일을 비운다.
 */
public abstract class AuthApiTest extends ApiTest {

    protected static final String PASSWORD = "correct-horse-1";
    protected static final String SESSION_COOKIE = "SESSION";
    protected static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    protected static final String SAFARI_IPHONE =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected GreenMail greenMail;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected ToggleableWithdrawalGuard withdrawalGuard;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @AfterEach
    void cleanUpAuthState() throws Exception {
        jdbcTemplate.execute("TRUNCATE recovery_code, users RESTART IDENTITY CASCADE");
        redis.execute(connection -> {
            connection.serverCommands().flushDb();
            return null;
        }, true);
        greenMail.purgeEmailFromAllMailboxes();
        withdrawalGuard.reset();
    }

    // --- 사용자와 로그인 ---

    /** 이메일 인증을 마친 활성 사용자를 바로 만든다. */
    protected long createActiveUser(String email) {
        User user = User.pending(User.normalizeEmail(email), passwordEncoder.encode(PASSWORD), "사용자", null);
        user.verifyEmail(clock.instant());
        return userRepository.saveAndFlush(user).getId();
    }

    protected ResultActions signup(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/users").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password, "name", "가입자")));
    }

    protected ResultActions login(String email, String password, String userAgent, Cookie... cookies) throws Exception {
        var request = post("/api/v1/auth/login").with(csrf())
                .header(HttpHeaders.USER_AGENT, userAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password));
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions login(String email, String password) throws Exception {
        return login(email, password, CHROME_WINDOWS);
    }

    /** 2단계 인증이 꺼진 사용자로 로그인하고 세션 쿠키를 돌려준다. */
    protected Cookie loginCookie(String email, String userAgent) throws Exception {
        MvcResult result = login(email, PASSWORD, userAgent).andExpect(status().isOk()).andReturn();
        return sessionCookie(result);
    }

    protected Cookie loginCookie(String email) throws Exception {
        return loginCookie(email, CHROME_WINDOWS);
    }

    protected ResultActions me(Cookie session) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").cookie(session));
    }

    protected static Cookie sessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).as("SESSION 쿠키").isNotNull();
        assertThat(cookie.getMaxAge()).as("만료 쿠키가 아님").isNotZero();
        return cookie;
    }

    /** 세션 쿠키 값(Base64)을 세션 ID로 되돌린다. */
    protected static String sessionId(Cookie cookie) {
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }

    /**
     * Spring Security Test의 {@code csrf()}는 공유 {@link CsrfFilter}의 토큰 저장소를 테스트용(세션 기반)으로 바꿔 둔다.
     * 실제 쿠키 흐름을 검증하는 테스트에서만 운영 설정(쿠키 저장소)으로 되돌린다. 다음 {@code csrf()} 호출은 다시 감싼다.
     */
    protected void useRealCsrfRepository() {
        CsrfFilter csrfFilter = springSecurityFilterChain.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(CsrfFilter.class::isInstance)
                .map(CsrfFilter.class::cast)
                .findFirst()
                .orElseThrow();
        ReflectionTestUtils.setField(csrfFilter, "tokenRepository", CookieCsrfTokenRepository.withHttpOnlyFalse());
    }

    // --- 2단계 인증 ---

    protected record TwoFactor(byte[] secret, List<String> recoveryCodes) {
    }

    /** 로그인한 세션으로 2단계 인증을 등록하고 켠다. */
    protected TwoFactor enableTwoFactor(Cookie session) throws Exception {
        String setup = mockMvc.perform(post("/api/v1/users/me/2fa/setup").with(csrf()).cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        byte[] secret = base32Decode(JsonPath.read(setup, "$.secret"));

        String enabled = mockMvc.perform(post("/api/v1/users/me/2fa/enable").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("code", currentCode(secret))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> codes = JsonPath.read(enabled, "$.recoveryCodes");
        return new TwoFactor(secret, codes);
    }

    protected String currentCode(byte[] secret) {
        return Totp.codeAt(secret, Totp.stepAt(clock.instant()));
    }

    /**
     * 다음 시간 구간으로 시계를 옮기고 그 구간의 코드를 돌려준다. 같은 구간의 코드는 한 번만 쓸 수 있기 때문이다.
     */
    protected String nextCode(byte[] secret) {
        clock.advance(Duration.ofSeconds(Totp.PERIOD_SECONDS));
        return currentCode(secret);
    }

    // --- 메일 ---

    protected List<MimeMessage> mailsTo(String email) {
        return Arrays.stream(greenMail.getReceivedMessages())
                .filter(message -> recipient(message).equalsIgnoreCase(email))
                .toList();
    }

    protected String mailBody(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String text) {
            return text;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    /** 그 주소로 온 마지막 메일의 링크 토큰. */
    protected String lastMailToken(String email) throws Exception {
        List<MimeMessage> mails = mailsTo(email);
        assertThat(mails).as(email + " 메일").isNotEmpty();
        Matcher matcher = TOKEN.matcher(mailBody(mails.getLast()));
        assertThat(matcher.find()).as("메일 본문의 토큰 링크").isTrue();
        return matcher.group(1);
    }

    private static String recipient(MimeMessage message) {
        try {
            return message.getRecipients(Message.RecipientType.TO)[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // --- JSON ---

    /** 키·값 쌍으로 JSON 객체를 만든다. 값은 문자열, 숫자, 불리언, null. */
    protected static String json(Object... keyValues) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(keyValues[i]).append("\":");
            Object value = keyValues[i + 1];
            if (value == null || value instanceof Number || value instanceof Boolean) {
                out.append(value);
            } else {
                out.append('"').append(value.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        return out.append('}').toString();
    }

    private static byte[] base32Decode(String encoded) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : encoded.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
