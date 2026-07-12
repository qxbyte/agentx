package com.agentx.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SysUserRepository extends JpaRepository<SysUser, UUID> {
    Optional<SysUser> findByUsername(String username);
}
