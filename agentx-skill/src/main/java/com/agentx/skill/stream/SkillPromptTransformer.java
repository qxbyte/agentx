package com.agentx.skill.stream;

import com.agentx.infra.ai.stream.UserPromptTransformer;
import com.agentx.skill.service.SkillExpansionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 把斜杠命令展开挂入 chat 的用户输入前置变换链（chat 对 skill 零依赖）。 */
@Order(10)
@Component
@RequiredArgsConstructor
public class SkillPromptTransformer implements UserPromptTransformer {

    private final SkillExpansionService expansionService;

    @Override
    public String transform(UserPromptContext context, String content) {
        return expansionService.expand(content);
    }
}
