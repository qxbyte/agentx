package com.agentx.skill.store;

import java.util.List;
import java.util.Optional;

/**
 * 额外 skill 来源 SPI（skill 模块的扩展点，解耦手法同 infra-ai 的 UserPromptTransformer）。
 * <p>
 * plugin 模块据此把启用插件的 skills 以 "plugin:skill" 命名空间挂进
 * 补全菜单与展开链路；skill 对 plugin 零依赖。依赖方向：plugin → skill。
 */
public interface SkillProvider {

    /** 本 provider 的全部可用 skill（name 含命名空间前缀，如 "superpowers:brainstorming"）。 */
    List<SkillFile> list();

    /** 按全限定名查找；不属于本 provider 的名字返回 empty。 */
    Optional<SkillFile> find(String name);
}
