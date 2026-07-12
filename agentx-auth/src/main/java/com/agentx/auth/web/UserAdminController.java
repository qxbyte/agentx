package com.agentx.auth.web;

import com.agentx.auth.domain.SysUser;
import com.agentx.auth.domain.SysUserRepository;
import com.agentx.auth.web.dto.AuthDtos.UserView;
import com.agentx.common.api.ApiResponse;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

/** 用户管理（ADMIN）：企业接 SSO 前的基础账号运营面。 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public record CreateUserRequest(@NotBlank String username, @NotBlank String password,
                                    String nickname, String role) {}

    @GetMapping
    public ApiResponse<List<UserView>> list() {
        return ApiResponse.ok(userRepository.findAll().stream()
                .map(u -> new UserView(u.getId(), u.getUsername(), u.getNickname(), u.getRole()))
                .toList());
    }

    @PostMapping
    public ApiResponse<UserView> create(@Valid @RequestBody CreateUserRequest req) {
        userRepository.findByUsername(req.username()).ifPresent(u -> {
            throw new BizException(ErrorCode.CONFLICT, "用户名已存在");
        });
        SysUser user = new SysUser();
        user.setId(UuidV7.next());
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.nickname());
        user.setRole("ADMIN".equals(req.role()) ? "ADMIN" : "USER");
        userRepository.save(user);
        return ApiResponse.ok(new UserView(user.getId(), user.getUsername(),
                user.getNickname(), user.getRole()));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> setStatus(@PathVariable UUID id, @RequestParam String value) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!"ACTIVE".equals(value) && !"DISABLED".equals(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "status 只能是 ACTIVE/DISABLED");
        }
        user.setStatus(value);
        userRepository.save(user);
        return ApiResponse.ok();
    }
}
