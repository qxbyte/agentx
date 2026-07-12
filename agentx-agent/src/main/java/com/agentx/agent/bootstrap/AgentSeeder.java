package com.agentx.agent.bootstrap;

import com.agentx.agent.domain.AgentDefinition;
import com.agentx.agent.domain.AgentDefinitionRepository;
import com.agentx.agent.domain.WorkflowType;
import com.agentx.common.util.UuidV7;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 种子一个可直接体验的 ReAct 示例 Agent（幂等）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSeeder implements ApplicationRunner {

    private final AgentDefinitionRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        if (repository.findByName("生活助手").isPresent()) {
            return;
        }
        AgentDefinition agent = new AgentDefinition();
        agent.setId(UuidV7.next());
        agent.setName("生活助手");
        agent.setDescription("演示 ReAct 工具循环：时间、日期计算与天气查询");
        agent.setSystemPrompt("""
                你是 AgentX 平台的生活助手。你可以使用提供的工具查询当前时间、计算日期差和查询天气。
                回答前先判断是否需要调用工具；需要多个信息时可以连续调用多个工具。
                用简体中文、简洁友好地回答。""");
        agent.setWorkflowType(WorkflowType.REACT);
        agent.setToolNames("[\"currentDateTime\",\"daysBetween\",\"currentWeather\"]");
        agent.setMaxIterations(6);
        repository.save(agent);
        log.info("seeded demo agent: 生活助手");
    }
}
