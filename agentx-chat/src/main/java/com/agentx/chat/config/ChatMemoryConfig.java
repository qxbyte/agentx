package com.agentx.chat.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 模型轨记忆装配（设计文档 §4.4 双轨制）。
 * 显式 bean 而非 starter 自动配置：schema 由 Flyway V2 统一管理（不开 auto-init），
 * 窗口大小集中在此一处调整。
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 消息数硬上限（兜底护栏，非主策略）：上下文实际由 ModelMemoryService 按
     * token 预算裁剪 + 滚动压缩管理，此处放大到极端兜底值——若仍触发（约百轮
     * 无压缩），说明压缩链路故障，按整轮驱逐止损。
     */
    private static final int MEMORY_WINDOW = 200;

    @Bean
    public ChatMemory chatMemory(JdbcTemplate jdbcTemplate) {
        JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new PostgresChatMemoryRepositoryDialect())
                .build();
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MEMORY_WINDOW)
                .build();
    }
}
