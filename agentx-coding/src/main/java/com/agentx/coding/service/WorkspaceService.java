package com.agentx.coding.service;

import com.agentx.coding.domain.CodingWorkspace;
import com.agentx.coding.domain.CodingWorkspaceRepository;
import com.agentx.coding.web.dto.CodingDtos.WorkspaceUpsertRequest;
import com.agentx.coding.web.dto.CodingDtos.WorkspaceValidation;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 工作区管理（设计文档 §3/§8）。root_path 创建/更新时校验：目录存在、可读写、
 * 不落在敏感系统路径。工作区按用户隔离，越权表现为 404。
 */
@Slf4j
@Service
public class WorkspaceService {

    /** 禁止作为工作区根的敏感路径前缀。 */
    private static final Set<String> FORBIDDEN_ROOTS =
            Set.of("/", "/etc", "/usr", "/bin", "/sbin", "/var", "/System", "/Library", "/boot");

    private final CodingWorkspaceRepository repository;
    /** 「新建空白项目」的落盘根目录。 */
    private final Path projectsRoot;

    public WorkspaceService(CodingWorkspaceRepository repository,
                            @Value("${agentx.coding.projects-root}") String projectsRoot) {
        this.repository = repository;
        this.projectsRoot = Path.of(projectsRoot).toAbsolutePath().normalize();
    }

    public List<CodingWorkspace> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public CodingWorkspace getOwned(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "工作区不存在"));
    }

    @Transactional
    public CodingWorkspace create(UUID userId, WorkspaceUpsertRequest req) {
        validateRootPath(req.rootPath());
        CodingWorkspace ws = new CodingWorkspace();
        ws.setId(UuidV7.next());
        ws.setUserId(userId);
        apply(ws, req);
        return repository.save(ws);
    }

    @Transactional
    public CodingWorkspace update(UUID id, UUID userId, WorkspaceUpsertRequest req) {
        validateRootPath(req.rootPath());
        CodingWorkspace ws = getOwned(id, userId);
        apply(ws, req);
        return repository.save(ws);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        repository.delete(getOwned(id, userId));
    }

    /**
     * 新建空白项目：在受控根 projectsRoot 下建目录并 git init。
     * 目录名即项目名——只允许安全字符，物理上杜绝路径逃逸。
     */
    @Transactional
    public CodingWorkspace createBlank(UUID userId, String name, UUID kbId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 60 || !trimmed.matches("[\\w\\u4e00-\\u9fa5. -]+")
                || trimmed.contains("..")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "项目名仅允许中英文、数字、空格、. _ -");
        }
        Path dir = projectsRoot.resolve(trimmed).normalize();
        if (!dir.startsWith(projectsRoot)) {
            throw new BizException(ErrorCode.FORBIDDEN, "非法项目名");
        }
        if (Files.exists(dir)) {
            throw new BizException(ErrorCode.CONFLICT, "同名项目目录已存在: " + dir);
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "创建项目目录失败: " + e.getMessage());
        }
        gitInit(dir);

        CodingWorkspace ws = new CodingWorkspace();
        ws.setId(UuidV7.next());
        ws.setUserId(userId);
        ws.setName(trimmed);
        ws.setRootPath(dir.toString());
        ws.setKbId(kbId);
        return repository.save(ws);
    }

    /** git init 失败不致命（无 git 环境时项目仍可用，gitStatus/gitDiff 工具会如实报错）。 */
    private void gitInit(Path dir) {
        try {
            Process p = new ProcessBuilder("git", "init", "-q")
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.warn("git init 超时: {}", dir);
            }
        } catch (Exception e) {
            log.warn("git init 失败（忽略，项目仍可用）: {} - {}", dir, e.getMessage());
        }
    }

    /** 探测目录可用性（存在/可写/是否 git 仓库），供前端创建前预检。 */
    public WorkspaceValidation validate(String rootPath) {
        Path path;
        try {
            path = Path.of(rootPath).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return new WorkspaceValidation(false, false, false, "路径非法");
        }
        boolean exists = Files.isDirectory(path);
        boolean writable = exists && Files.isWritable(path);
        boolean gitRepo = exists && Files.isDirectory(path.resolve(".git"));
        String message = !exists ? "目录不存在"
                : !writable ? "目录不可写"
                : isForbidden(path) ? "禁止使用系统敏感目录" : "可用";
        return new WorkspaceValidation(exists, writable, gitRepo, message);
    }

    private void apply(CodingWorkspace ws, WorkspaceUpsertRequest req) {
        ws.setName(req.name());
        ws.setRootPath(Path.of(req.rootPath()).toAbsolutePath().normalize().toString());
        ws.setKbId(req.kbId());
    }

    private void validateRootPath(String rootPath) {
        Path path = Path.of(rootPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "工作区目录不存在: " + rootPath);
        }
        if (!Files.isWritable(path)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "工作区目录不可写: " + rootPath);
        }
        if (isForbidden(path)) {
            throw new BizException(ErrorCode.FORBIDDEN, "禁止使用系统敏感目录作为工作区");
        }
    }

    private boolean isForbidden(Path path) {
        String p = path.toString();
        return FORBIDDEN_ROOTS.contains(p)
                || FORBIDDEN_ROOTS.stream().anyMatch(f -> !f.equals("/") && p.equals(f))
                || p.equals(System.getProperty("user.home"));
    }
}
