package com.agentx.tools.registry;

import org.springframework.stereotype.Component;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 平台工具类标记注解（L1 代码级注册入口）。
 * <p>
 * 企业新增业务工具的方式：类上打 @AgentTool，方法上用 Spring AI 的
 * {@code @Tool} 描述能力，启动即自动进入 {@link ToolRegistry}。
 * 用显式标记而非全容器扫描 @Tool 方法，避免把普通 bean 误注册为工具。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AgentTool {
    /** 工具分组（管理端展示用），默认 general。 */
    String group() default "general";
}
