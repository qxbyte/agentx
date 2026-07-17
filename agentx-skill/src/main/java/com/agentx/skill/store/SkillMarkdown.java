package com.agentx.skill.store;

import java.time.Instant;

/**
 * SKILL.md 文本编解码（frontmatter + body），供本地 SkillFileStore 与插件侧 provider 共用。
 * frontmatter 仅识别平铺的 description / argument-hint / enabled;name 一律以外部传入为准。
 */
public final class SkillMarkdown {

    private SkillMarkdown() {}

    public static SkillFile parse(String name, String raw, Instant mtime) {
        String description = "";
        String argumentHint = null;
        boolean enabled = true;
        boolean userInvocable = true;
        boolean modelInvocable = true;
        String body = raw;
        if (raw.startsWith("---")) {
            int end = raw.indexOf("\n---", 3);
            if (end > 0) {
                String header = raw.substring(3, end);
                body = raw.substring(raw.indexOf('\n', end + 1) + 1);
                for (String line : header.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon <= 0) continue;
                    String key = line.substring(0, colon).strip();
                    String value = unquote(line.substring(colon + 1).strip());
                    switch (key) {
                        case "description" -> description = value;
                        case "argument-hint" -> argumentHint = value.isEmpty() ? null : value;
                        case "enabled" -> enabled = !"false".equalsIgnoreCase(value);
                        case "user-invocable" -> userInvocable = !"false".equalsIgnoreCase(value);
                        case "disable-model-invocation" -> modelInvocable = !"true".equalsIgnoreCase(value);
                        default -> { /* name/model 等：忽略，name 以文件名/目录名为准 */ }
                    }
                }
            }
        }
        return new SkillFile(name, description, argumentHint, body.strip(),
                enabled, userInvocable, modelInvocable, mtime);
    }

    public static String serialize(SkillFile skill) {
        StringBuilder sb = new StringBuilder("---\n");
        sb.append("description: ").append(oneLine(skill.description())).append('\n');
        if (skill.argumentHint() != null && !skill.argumentHint().isBlank()) {
            sb.append("argument-hint: ").append(oneLine(skill.argumentHint())).append('\n');
        }
        if (!skill.enabled()) {
            sb.append("enabled: false\n");
        }
        if (!skill.userInvocable()) {
            sb.append("user-invocable: false\n");
        }
        if (!skill.modelInvocable()) {
            sb.append("disable-model-invocation: true\n");
        }
        sb.append("---\n\n").append(skill.content()).append('\n');
        return sb.toString();
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String oneLine(String value) {
        return value.contains("\n") ? '"' + value.replace("\n", " ") + '"' : value;
    }
}
