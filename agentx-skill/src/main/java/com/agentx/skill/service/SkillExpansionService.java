package com.agentx.skill.service;

import com.agentx.skill.store.SkillFile;
import com.agentx.skill.store.SkillFileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 斜杠命令展开（对标 Claude Code Slash Commands）：
 * 整条消息形如 "/name args" 时读取用户 skill 目录展开为 body（$ARGUMENTS / $1..$9 参数替换），
 * 以 skill_instructions 标签包裹（数据边界防注入，同附件 documents XML 思路）。
 * 未命中一律原样透传，零打扰。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillExpansionService {

    /** /name 或 /plugin:name（plugin 命名空间预留），后随可选参数串；须整条消息匹配。 */
    private static final Pattern COMMAND =
            Pattern.compile("^/([a-z0-9-]+(?::[a-z0-9-]+)?)(?:\\s+([\\s\\S]*))?$");

    /** 单次扫描替换，避免参数值里的占位符被二次展开。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$(ARGUMENTS|[1-9])");

    private final SkillFileStore store;
    /** 额外 skill 来源（plugin 命名空间）；无实现时为空列表。 */
    private final java.util.List<com.agentx.skill.store.SkillProvider> providers;
    private final SkillResourceService resourceService;

    public String expand(String content) {
        if (content == null) {
            return content;
        }
        String trimmed = content.strip();
        Matcher command = COMMAND.matcher(trimmed);
        if (!command.matches()) {
            return content;
        }
        SkillFile skill = resolve(command.group(1));
        if (skill == null || skill.content().isBlank()) {
            return content;
        }
        String args = command.group(2) == null ? "" : command.group(2).strip();
        String body = substitute(skill.content(), args);
        log.debug("skill 展开 /{} args.len={}", skill.name(), args.length());
        return "<skill_instructions name=\"%s\" invoked_as=\"%s\">\n%s\n</skill_instructions>%s"
                .formatted(skill.name(), escapeAttr(trimmed), body,
                        resourceService.resourcesNote(skill.name()));
    }

    /**
     * 无冒号 → 本地 skills 目录;含冒号（plugin:name）→ 依序问各 provider。
     * 展开是用户显式触发路径:user-invocable: false 的 skill 一律不响应（原样透传）。
     */
    private SkillFile resolve(String name) {
        SkillFile skill = null;
        if (!name.contains(":")) {
            skill = store.find(name).filter(SkillFile::enabled).orElse(null);
        } else {
            for (com.agentx.skill.store.SkillProvider provider : providers) {
                var found = provider.find(name);
                if (found.isPresent()) {
                    skill = found.get();
                    break;
                }
            }
        }
        return skill != null && skill.userInvocable() ? skill : null;
    }

    private String substitute(String body, String args) {
        String[] positional = args.isEmpty() ? new String[0] : args.split("\\s+");
        Matcher m = PLACEHOLDER.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value;
            if ("ARGUMENTS".equals(key)) {
                value = args;
            } else {
                int index = key.charAt(0) - '0';
                value = index <= positional.length ? positional[index - 1] : "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String escapeAttr(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
