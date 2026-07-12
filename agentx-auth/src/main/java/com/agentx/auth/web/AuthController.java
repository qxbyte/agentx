package com.agentx.auth.web;

import com.agentx.auth.domain.SysUserRepository;
import com.agentx.auth.security.AuthPrincipal;
import com.agentx.auth.security.CurrentUser;
import com.agentx.auth.service.AuthService;
import com.agentx.auth.web.dto.AuthDtos.LoginRequest;
import com.agentx.auth.web.dto.AuthDtos.RefreshRequest;
import com.agentx.auth.web.dto.AuthDtos.TokenResponse;
import com.agentx.auth.web.dto.AuthDtos.UserView;
import com.agentx.common.api.ApiResponse;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final SysUserRepository userRepository;

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req.username(), req.password()));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req.refreshToken()));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(@CurrentUser AuthPrincipal principal) {
        var u = userRepository.findById(principal.id())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在"));
        return ApiResponse.ok(new UserView(u.getId(), u.getUsername(), u.getNickname(), u.getRole()));
    }
}
