package com.agentx.chat.config;

import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;
import java.util.List;

/**
 * 对话安全防护装配（设计文档 §9）：SafeGuardAdvisor 敏感词基线拦截。
 * 敏感词经配置项 {@code agentx.chat.sensitive-words}（逗号分隔）注入；
 * 命中用户输入即返回失败话术、不进模型。留空即无拦截（advisor 成为透明直通）。
 */
@Configuration
public class ChatGuardConfig {

    @Bean
    public SafeGuardAdvisor sensitiveWordSafeGuard(
            @Value("${agentx.chat.sensitive-words:}") String sensitiveWordsCsv) {
        List<String> words = Arrays.stream(sensitiveWordsCsv.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        return SafeGuardAdvisor.builder()
                .sensitiveWords(words)
                .failureResponse("抱歉，您的请求包含不被允许的内容，我无法处理。")
                .build();
    }
}
