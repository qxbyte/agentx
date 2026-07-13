package com.agentx.coding.service;

import com.agentx.coding.domain.CodingWorkspace;
import com.agentx.coding.domain.CodingWorkspaceRepository;
import com.agentx.coding.web.dto.CodingDtos.WorkspaceUpsertRequest;
import com.agentx.coding.web.dto.CodingDtos.WorkspaceValidation;
import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 工作区管理（设计文档 §3/§8）。root_path 创建/更新时校验：目录存在、可读写、
 * 不落在敏感系统路径。工作区按用户隔离，越权表现为 404。
 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    /** 禁止作为工作区根的敏感路径前缀。 */
    private static final Set<String> FORBIDDEN_ROOTS =
            Set.of("/", "/etc", "/usr", "/bin", "/sbin", "/var", "/System", "/Library", "/boot");

    private final CodingWorkspaceRepository repository;

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
