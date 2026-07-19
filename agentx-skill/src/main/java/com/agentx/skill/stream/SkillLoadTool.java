package com.agentx.skill.stream;

import com.agentx.skill.service.SkillResourceService;
import com.agentx.skill.service.SkillService;
import com.agentx.skill.store.SkillFile;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 动态 skill 工具（每请求构建）：description 即技能目录（渐进式披露 L1）,
 * call 按 name 加载技能全文（L2）返回给模型。
 */
class SkillLoadTool implements ToolCallback {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{"name":{"type":"string",
            "description":"技能名,含命名空间前缀时原样传入(如 superpowers:brainstorming)"}},
            "required":["name"]}""";
    /** 目录里单条 description 的截断长度(工具描述总量有限,细节留给 L2 全文)。 */
    private static final int DESC_LIMIT = 120;
    private static final int CATALOG_LIMIT = 60;

    private final SkillService skillService;
    private final SkillResourceService resourceService;
    private final ObjectMapper objectMapper;
    private final ToolDefinition definition;

    SkillLoadTool(SkillService skillService, SkillResourceService resourceService,
                  ObjectMapper objectMapper, List<SkillFile> invocable) {
        this.skillService = skillService;
        this.resourceService = resourceService;
        this.objectMapper = objectMapper;
        this.definition = DefaultToolDefinition.builder()
                .name("skill")
                .description(buildDescription(invocable))
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    private static String buildDescription(List<SkillFile> invocable) {
        StringBuilder sb = new StringBuilder("""
                加载一个技能（skill）的完整指令并遵照执行。技能是预置的任务指令模板；\
                当用户的请求与下列某个技能的适用场景匹配时,应调用本工具加载该技能后按其指令完成任务;\
                无匹配时不要调用。加载后指令即刻生效,无需征求用户同意。可用技能目录:
                """);
        int count = 0;
        for (SkillFile skill : invocable) {
            if (++count > CATALOG_LIMIT) {
                sb.append("- …(其余 ").append(invocable.size() - CATALOG_LIMIT).append(" 项省略)\n");
                break;
            }
            String desc = skill.description() == null ? "" : skill.description();
            if (desc.length() > DESC_LIMIT) {
                desc = desc.substring(0, DESC_LIMIT) + "…";
            }
            sb.append("- ").append(skill.name()).append(": ").append(desc).append('\n');
        }
        return sb.toString();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String name;
        try {
            name = objectMapper.readTree(toolInput).path("name").asString("").strip();
        } catch (RuntimeException e) {
            return "参数解析失败,请以 {\"name\":\"技能名\"} 调用。";
        }
        SkillFile skill = skillService.findModelInvocable(name).orElse(null);
        if (skill == null) {
            return "技能不存在或不可自动触发: " + name + "。可用技能见本工具描述中的目录。";
        }
        return "<skill_instructions name=\"%s\">\n%s\n</skill_instructions>%s\n请立即遵循以上技能指令继续完成用户的任务。"
                .formatted(skill.name(), skill.content(), resourceService.resourcesNote(skill.name()));
    }
}
