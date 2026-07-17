package com.agentx.skill.service;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.skill.store.SkillFile;
import com.agentx.skill.store.SkillFileStore;
import com.agentx.skill.store.SkillProvider;
import com.agentx.skill.web.dto.SkillDtos.UpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

/**
 * Skill 管理：文件即唯一事实源（~/.agentx/skills/，本机共享、不随账号走），name 即标识。
 * 手动放进目录的 SKILL.md 与 API 创建的完全等价。
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    private static final int MAX_SKILLS_PER_USER = 100;

    private final SkillFileStore store;
    /** 额外 skill 来源（如 plugin 模块的命名空间 skills）；无实现时为空列表。 */
    private final List<SkillProvider> providers;

    /**
     * 启用中的列表（/ 补全菜单）：本地 skills + 各 provider（插件）合并。
     * user-invocable: false 的 skill 不进菜单（仅供 M2 模型自动触发）。
     */
    public List<SkillFile> listEnabled() {
        List<SkillFile> merged = new java.util.ArrayList<>(
                store.scan().stream()
                        .filter(SkillFile::enabled)
                        .filter(SkillFile::userInvocable)
                        .toList());
        for (SkillProvider provider : providers) {
            provider.list().stream().filter(SkillFile::userInvocable).forEach(merged::add);
        }
        return merged;
    }

    /** 管理列表（含停用）。 */
    public List<SkillFile> listAll() {
        return store.scan();
    }

    /**
     * 可被模型自动触发的技能（M2）：enabled 且 modelInvocable,
     * 与 user-invocable 无关——仅模型可用的内部技能（user-invocable: false）正是走这条路。
     */
    public List<SkillFile> listModelInvocable() {
        List<SkillFile> merged = new java.util.ArrayList<>(
                store.scan().stream()
                        .filter(SkillFile::enabled)
                        .filter(SkillFile::modelInvocable)
                        .toList());
        for (SkillProvider provider : providers) {
            provider.list().stream().filter(SkillFile::modelInvocable).forEach(merged::add);
        }
        return merged;
    }

    /** 按名查找可自动触发的技能（skill 工具的 L2 加载入口）。 */
    public java.util.Optional<SkillFile> findModelInvocable(String name) {
        if (name.contains(":")) {
            for (SkillProvider provider : providers) {
                var found = provider.find(name).filter(SkillFile::modelInvocable);
                if (found.isPresent()) {
                    return found;
                }
            }
            return java.util.Optional.empty();
        }
        return store.find(name)
                .filter(SkillFile::enabled)
                .filter(SkillFile::modelInvocable);
    }

    public SkillFile getOwned(String name) {
        return store.find(name)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "技能不存在"));
    }

    public SkillFile create(UpsertRequest req) {
        if (store.count() >= MAX_SKILLS_PER_USER) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "技能数量已达上限（" + MAX_SKILLS_PER_USER + "），请删除不用的技能后再创建");
        }
        if (store.exists(req.name())) {
            throw new BizException(ErrorCode.CONFLICT, "斜杠命令 /" + req.name() + " 已存在");
        }
        store.write(toFile(req, true, true, true));
        return getOwned(req.name());
    }

    /** 更新；改名 = 冲突检查 + 删旧文件写新文件。请求未携带的开关沿用现值（不静默重置）。 */
    public SkillFile update(String name, UpsertRequest req) {
        SkillFile existing = getOwned(name);
        if (!name.equals(req.name()) && store.exists(req.name())) {
            throw new BizException(ErrorCode.CONFLICT, "斜杠命令 /" + req.name() + " 已存在");
        }
        store.write(toFile(req, existing.enabled(),
                existing.userInvocable(), existing.modelInvocable()));
        if (!name.equals(req.name())) {
            store.delete(name);
        }
        return getOwned(req.name());
    }

    /** 启停：回写 frontmatter 的 enabled 字段（其余标志原样保留）。 */
    public SkillFile setEnabled(String name, boolean enabled) {
        SkillFile skill = getOwned(name);
        store.write(new SkillFile(skill.name(), skill.description(), skill.argumentHint(),
                skill.content(), enabled, skill.userInvocable(), skill.modelInvocable(),
                Instant.now()));
        return getOwned(name);
    }

    public void delete(String name) {
        getOwned(name);
        store.delete(name);
    }

    private SkillFile toFile(UpsertRequest req, boolean enabled,
                             boolean defaultUserInvocable, boolean defaultModelInvocable) {
        return new SkillFile(req.name(), req.description(),
                req.argumentHint() == null || req.argumentHint().isBlank() ? null : req.argumentHint(),
                req.content(), enabled,
                req.userInvocable() != null ? req.userInvocable() : defaultUserInvocable,
                req.modelInvocable() != null ? req.modelInvocable() : defaultModelInvocable,
                Instant.now());
    }
}
