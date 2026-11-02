package com.back.user.api;

import com.back.auth.api.AuthDto;
import com.back.auth.web.SessionLogin;
import com.back.common.security.AuthUser;
import com.back.user.application.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 계정. 다른 사용자를 검색하거나 목록으로 보는 API는 두지 않는다 (정책 7.7).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SessionLogin sessionLogin;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto.SignupRes signup(@Valid @RequestBody UserDto.SignupReq req) {
        return userService.signup(req);
    }

    @GetMapping("/me")
    public UserDto.DetailRes getMe(@AuthenticationPrincipal AuthUser user) {
        return userService.getMe(user.userId());
    }

    @PutMapping("/me")
    public UserDto.DetailRes updateMe(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody UserDto.UpdateReq req) {
        return userService.updateMe(user.userId(), req);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody UserDto.ChangePasswordReq req, HttpServletRequest request) {
        userService.changePassword(user.userId(), req, sessionLogin.currentSessionId(request));
    }

    @PostMapping("/me/withdrawal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthUser user, @RequestBody AuthDto.ReauthReq req,
            HttpServletRequest request) {
        userService.withdraw(user.userId(), req.password(), req.code());
        sessionLogin.logout(request);
    }
}
