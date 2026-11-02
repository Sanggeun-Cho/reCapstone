package com.back.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.back.common.security.AuthUser;
import com.back.group.api.GroupDto;
import com.back.group.api.InvitationDto;
import com.back.group.application.GroupService;
import com.back.group.application.InvitationService;
import com.back.group.application.PurgeService;
import com.back.space.api.SpaceDto;
import com.back.space.application.SpaceService;
import com.back.user.domain.User;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 그룹·스페이스 API 테스트 베이스. {@code TEST_SPEC.md} 0장의 공통 픽스처를 만든다.
 * <ul>
 *   <li>alice: 그룹 G의 OWNER</li>
 *   <li>bob: G 멤버, 스페이스 S의 담당자</li>
 *   <li>carol, dave: G 멤버</li>
 *   <li>mallory: G에 속하지 않은 가입 사용자</li>
 *   <li>S2: G의 담당자 없는 스페이스</li>
 * </ul>
 * 픽스처는 서비스로 만든다(초대 → 수락, 최초 지정). 픽스처가 보낸 초대 메일은 비운다.
 */
public abstract class GroupTestSupport extends AuthApiTest {

    protected static final String ALICE = "alice@example.com";
    protected static final String BOB = "bob@example.com";
    protected static final String CAROL = "carol@example.com";
    protected static final String DAVE = "dave@example.com";
    protected static final String MALLORY = "mallory@example.com";
    protected static final String ANON = null;

    /** BCrypt는 느리므로 픽스처 사용자의 비밀번호 해시는 한 번만 만든다. */
    private static String passwordHash;

    @Autowired
    protected GroupService groupService;

    @Autowired
    protected InvitationService invitationService;

    @Autowired
    protected SpaceService spaceService;

    @Autowired
    protected PurgeService purgeService;

    protected long alice;
    protected long bob;
    protected long carol;
    protected long dave;
    protected long mallory;
    protected long g;
    protected long s;
    protected long s2;

    private final Map<String, Cookie> sessions = new HashMap<>();

    @BeforeEach
    void setUpGroupFixture() throws Exception {
        sessions.clear();
        alice = createUser(ALICE, "앨리스", null);
        bob = createUser(BOB, "밥", "010-1234-5678");
        carol = createUser(CAROL, "캐럴", null);
        dave = createUser(DAVE, "데이브", null);
        mallory = createUser(MALLORY, "맬러리", null);

        g = createGroup(alice, "G");
        join(g, alice, bob);
        join(g, alice, carol);
        join(g, alice, dave);
        s = createSpace(alice, g, "S");
        assign(alice, s, bob);
        s2 = createSpace(alice, g, "S2");
        greenMail.purgeEmailFromAllMailboxes();
    }

    // --- 픽스처 ---

    protected long createUser(String email, String name, String workPhone) {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        User user = User.pending(User.normalizeEmail(email), passwordHash, name, workPhone);
        user.verifyEmail(clock.instant());
        return userRepository.saveAndFlush(user).getId();
    }

    protected long createGroup(long ownerId, String name) {
        return as(ownerId, () -> groupService.create(ownerId, new GroupDto.NameReq(name)).id());
    }

    /** OWNER가 초대하고 사용자가 수락해 멤버가 된다. */
    protected void join(long groupId, long ownerId, long userId) {
        String email = userRepository.findById(userId).orElseThrow().getEmail();
        long invitationId = as(ownerId,
                () -> invitationService.invite(ownerId, groupId, new InvitationDto.CreateReq(email)).id());
        as(userId, () -> invitationService.accept(userId, invitationId));
    }

    protected long createSpace(long ownerId, long groupId, String name) {
        return as(ownerId, () -> spaceService.create(ownerId, groupId, new SpaceDto.NameReq(name)).id());
    }

    protected void assign(long ownerId, long spaceId, long userId) {
        as(ownerId, () -> spaceService.assign(ownerId, spaceId, new SpaceDto.AssigneeReq(userId)));
    }

    /** 서비스를 그 사용자로 로그인한 상태에서 부른다. 감사 필드(created_by 등)가 API로 부른 것과 같게 남는다. */
    protected <T> T as(long userId, Supplier<T> action) {
        // 공유 컨텍스트 객체를 바꾸면 MockMvc가 쓰는 TestSecurityContextHolder에 인증이 남으므로 새 컨텍스트를 쓴다
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
            TestSecurityContextHolder.clearContext();
        }
    }

    // --- 요청 ---

    /** 사용자(이메일)로 로그인한 세션으로 요청한다. {@code email}이 null이면 비로그인. 상태 변경 요청에는 CSRF 토큰을 붙인다. */
    protected ResultActions call(HttpMethod method, String url, String email, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, url).with(csrf());
        if (email != null) {
            builder.cookie(session(email));
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions get(String url, String email) throws Exception {
        return call(HttpMethod.GET, url, email, null);
    }

    protected ResultActions post(String url, String email, String body) throws Exception {
        return call(HttpMethod.POST, url, email, body);
    }

    protected ResultActions patch(String url, String email, String body) throws Exception {
        return call(HttpMethod.PATCH, url, email, body);
    }

    protected ResultActions put(String url, String email, String body) throws Exception {
        return call(HttpMethod.PUT, url, email, body);
    }

    protected ResultActions delete(String url, String email) throws Exception {
        return call(HttpMethod.DELETE, url, email, null);
    }

    protected Cookie session(String email) throws Exception {
        Cookie cookie = sessions.get(email);
        if (cookie == null) {
            cookie = loginCookie(email);
            sessions.put(email, cookie);
        }
        return cookie;
    }

    /** 비밀번호 본인 재확인 값. 예: {@code "{" + REAUTH + "}"} */
    protected static final String REAUTH = "\"reauth\":{\"password\":\"" + PASSWORD + "\"}";

    protected static String body(String fields) {
        return "{" + fields + "}";
    }

    protected Long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
