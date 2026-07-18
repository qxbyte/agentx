package com.agentx.plugin.service;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * git 拉取（ProcessBuilder 固定参数，不经 shell）：clone --depth 1 与 rev-parse HEAD。
 * 仅接受 https URL(由上层校验),失败时把 git 输出尾部透传给用户。
 */
@Slf4j
@Component
public class GitFetcher {

    private static final long TIMEOUT_SECONDS = 180;

    public void cloneShallow(String url, Path dest) {
        run(List.of("git", "clone", "--depth", "1", "--quiet", url, dest.toString()), null);
    }

    /** 浅克隆仓库更新到远端最新（fetch --depth 1 + reset --hard,兼容 force-push）。 */
    public void updateShallow(Path repo) {
        run(List.of("git", "-C", repo.toString(), "fetch", "--depth", "1", "--quiet", "origin", "HEAD"), null);
        run(List.of("git", "-C", repo.toString(), "reset", "--hard", "--quiet", "FETCH_HEAD"), null);
    }

    /** HEAD 提交 SHA；非 git 目录返回 null。 */
    public String headSha(Path repo) {
        try {
            return run(List.of("git", "-C", repo.toString(), "rev-parse", "HEAD"), null).strip();
        } catch (BizException e) {
            return null;
        }
    }

    private String run(List<String> command, Path workDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BizException(ErrorCode.BAD_REQUEST, "git 操作超时（" + TIMEOUT_SECONDS + "s）");
            }
            if (process.exitValue() != 0) {
                String tail = output.length() > 400 ? output.substring(output.length() - 400) : output;
                throw new BizException(ErrorCode.BAD_REQUEST, "git 失败: " + tail.strip());
            }
            return output;
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "git 不可用: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "git 操作被中断");
        }
    }
}
