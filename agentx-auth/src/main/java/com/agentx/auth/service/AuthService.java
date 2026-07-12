package com.agentx.auth.service;

import com.agentx.auth.domain.SysUser;
import com.agentx.auth.domain.SysUserRepository;
import com.agentx.auth.jwt.JwtService;
import com.agentx.auth.web.dto.AuthDtos.TokenResponse;
import com.agentx.auth.web.dto.AuthDtos.UserView;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public TokenResponse login(String username, String rawPassword) {
        SysUser user = userRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已禁用");
        }
        return tokens(user);
    }

    public TokenResponse refresh(String refreshToken) {
        JwtService.DecodedJwt d = jwtService.verify(refreshToken);
        if (!"refresh".equals(d.tokenType())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "需要 refresh token");
        }
        SysUser user = userRepository.findById(d.userId())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户不存在"));
        return tokens(user);
    }

    private TokenResponse tokens(SysUser u) {
        return new TokenResponse(jwtService.issueAccess(u), jwtService.issueRefresh(u),
                new UserView(u.getId(), u.getUsername(), u.getNickname(), u.getRole()));
    }
}
