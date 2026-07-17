package com.agentx.skill.web.dto;

import com.agentx.skill.store.SkillFile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** skill 以文件为事实源，name 即标识（DTO 的 id 字段 = name，前端沿用）。 */
public final class SkillDtos {
    private SkillDtos() {}

    /** 元数据视图（渐进式披露 L1）：不含 content，供 / 补全菜单。 */
    public record Meta(String id, String name, String description, String argumentHint) {
        public static Meta of(SkillFile s) {
            return new Meta(s.name(), s.name(), s.description(), s.argumentHint());
        }
    }

    /** 管理列表视图：含启停与调用开关状态，不含 content。 */
    public record View(String id, String name, String description, String argumentHint,
                       boolean enabled, boolean userInvocable, boolean modelInvocable,
                       Instant createdAt, Instant updatedAt) {
        public static View of(SkillFile s) {
            return new View(s.name(), s.name(), s.description(), s.argumentHint(),
                    s.enabled(), s.userInvocable(), s.modelInvocable(),
                    s.updatedAt(), s.updatedAt());
        }
    }

    /** 详情视图（渐进式披露 L2）：含 content，编辑用。 */
    public record Detail(String id, String name, String description, String argumentHint,
                         String content, boolean enabled,
                         boolean userInvocable, boolean modelInvocable) {
        public static Detail of(SkillFile s) {
            return new Detail(s.name(), s.name(), s.description(), s.argumentHint(),
                    s.content(), s.enabled(), s.userInvocable(), s.modelInvocable());
        }
    }

    /** userInvocable/modelInvocable 传 null 表示不改（创建时按默认 true）。 */
    public record UpsertRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9-]{1,64}$",
                    message = "名称仅允许小写字母、数字、连字符，长度 1-64")
            String name,
            @NotBlank @Size(max = 1024) String description,
            @Size(max = 255) String argumentHint,
            @NotBlank @Size(max = 8000) String content,
            Boolean userInvocable,
            Boolean modelInvocable) {}

    public record EnabledRequest(boolean enabled) {}
}
