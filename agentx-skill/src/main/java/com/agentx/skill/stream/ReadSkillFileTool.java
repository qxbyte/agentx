package com.agentx.skill.stream;

import com.agentx.skill.service.SkillResourceService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * readSkillFile 工具（L3 资源按需读取）：读取某技能目录内的资源文件
 * （references/ scripts/ assets/…）。等价于 Claude Code 里模型用 Read 读
 * skill 基准目录——但路径沙箱锁死在技能自己的目录内。
 */
class ReadSkillFileTool implements ToolCallback {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
            "skill":{"type":"string","description":"技能名(与加载时一致,如 superpowers:brainstorming)"},
            "path":{"type":"string","description":"资源文件相对路径(如 references/patterns.md)"}},
            "required":["skill","path"]}""";

    private static final ToolDefinition DEFINITION = DefaultToolDefinition.builder()
            .name("readSkillFile")
            .description("""
                    读取某个技能目录内的资源文件。技能指令(skill_instructions)中引用了 \
                    references/、assets/ 等相对路径文件时,用本工具按需读取其内容;\
                    可读文件以技能加载时附带的资源清单为准。""")
            .inputSchema(INPUT_SCHEMA)
            .build();

    private final SkillResourceService resources;
    private final ObjectMapper objectMapper;

    ReadSkillFileTool(SkillResourceService resources, ObjectMapper objectMapper) {
        this.resources = resources;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return DEFINITION;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String skill;
        String path;
        try {
            JsonNode node = objectMapper.readTree(toolInput);
            skill = node.path("skill").asString("").strip();
            path = node.path("path").asString("").strip();
        } catch (RuntimeException e) {
            return "参数解析失败,请以 {\"skill\":\"技能名\",\"path\":\"相对路径\"} 调用。";
        }
        if (skill.isEmpty() || path.isEmpty()) {
            return "skill 与 path 均不能为空。";
        }
        return resources.read(skill, path);
    }
}
