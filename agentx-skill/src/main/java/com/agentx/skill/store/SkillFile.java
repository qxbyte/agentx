package com.agentx.skill.store;

import java.time.Instant;

/**
 * 一个已解析的 skill 文件（SKILL.md frontmatter + body）。
 * name 以文件名为准（frontmatter 里的 name 忽略），/name 即命令名。
 * <p>
 * 调用路径双开关（frontmatter）：
 * <li>userInvocable（user-invocable，默认 true）：false 时不进 / 菜单、用户敲命令不展开，
 *     仅供模型自动触发（M2）——典型如 specode 的 intake 这类被其它 skill 编排调用的内部件；
 * <li>modelInvocable（!disable-model-invocation，默认 true）：false 时模型不可自动触发，
 *     仅用户显式调用——典型如有副作用的 /commit /deploy。M1 仅存储，M2 生效。
 */
public record SkillFile(
        String name,
        String description,
        String argumentHint,
        String content,
        boolean enabled,
        boolean userInvocable,
        boolean modelInvocable,
        Instant updatedAt
) {
    /** 双通默认值的便捷构造。 */
    public static SkillFile of(String name, String description, String argumentHint,
                               String content, boolean enabled, Instant updatedAt) {
        return new SkillFile(name, description, argumentHint, content, enabled, true, true, updatedAt);
    }
}
