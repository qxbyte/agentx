package com.agentx.auth.bootstrap;

import com.agentx.auth.domain.SysUser;
import com.agentx.auth.domain.SysUserRepository;
import com.agentx.common.util.UuidV7;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {
    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setId(UuidV7.next());
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode(
                System.getenv().getOrDefault("AGENTX_ADMIN_PASSWORD", "admin123")));
        admin.setNickname("管理员");
        admin.setRole("ADMIN");
        userRepository.save(admin);
        log.info("seeded admin user");
    }
}
