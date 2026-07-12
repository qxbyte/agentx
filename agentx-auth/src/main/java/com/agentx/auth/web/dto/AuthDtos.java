package com.agentx.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record UserView(UUID id, String username, String nickname, String role) {}
    public record TokenResponse(String accessToken, String refreshToken, UserView user) {}
}
