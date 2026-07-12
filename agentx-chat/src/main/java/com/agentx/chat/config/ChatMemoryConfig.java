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

    /** 模型上下文窗口：完整对话轮驱逐，保留 system 消息（Spring AI 默认语义）。 */
    private static final int MEMORY_WINDOW = 20;

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
