package com.agentx.auth.security;

import java.util.UUID;

public record AuthPrincipal(UUID id, String username, String role) {}
