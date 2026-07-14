package com.agentx.rag.service;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import com.agentx.rag.domain.ExternalKb;
import com.agentx.rag.domain.ExternalKbRepository;
import com.agentx.rag.web.dto.ExternalKbDtos.ProbeResult;
import com.agentx.rag.web.dto.ExternalKbDtos.UpsertRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部知识库管理：CRUD + 连接探测（heartbeat + info）。
 * B 方案下外部库用自己的 embedding 模型检索，两侧模型解耦——探测不再比对 embedding
 * 一致性，只对"外部库尚未建立索引"给出提醒。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalKbService {

    private final ExternalKbRepository repository;

    public List<ExternalKb> list() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    public List<ExternalKb> listEnabled() {
        return repository.findByEnabledTrueOrderByCreatedAtAsc();
    }

    @Transactional
    public ExternalKb create(UpsertRequest req) {
        ExternalKb kb = new ExternalKb();
        kb.setId(UuidV7.next());
        apply(kb, req);
        return repository.save(kb);
    }

    @Transactional
    public ExternalKb update(UUID id, UpsertRequest req) {
        ExternalKb kb = get(id);
        apply(kb, req);
        return repository.save(kb);
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(get(id));
    }

    public ExternalKb get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "外部知识库不存在"));
    }

    /** 连接探测：心跳 + 指定 vault 的库信息 + 本地/外部 embedding 模型一致性比对。 */
    @SuppressWarnings("unchecked")
    public ProbeResult probe(String baseUrl, String heartbeatPath, String infoPath, String vaultId) {
        RestClient client = ExternalKbHttp.client(baseUrl);
        boolean alive;
        String service = null;
        try {
            Map<String, Object> hb = client.get().uri(heartbeatPath).retrieve().body(Map.class);
            alive = hb != null && Boolean.TRUE.equals(hb.get("ok"));
            service = hb == null ? null : String.valueOf(hb.get("service"));
        } catch (Exception e) {
            return new ProbeResult(false, null, null, null, 0, 0,
                    "心跳失败: " + e.getMessage(), null);
        }
        try {
            Map<String, Object> info = client.get()
                    .uri(b -> b.path(infoPath).queryParam("vault", vaultId).build())
                    .retrieve().body(Map.class);
            Map<String, Object> embedding = info == null
                    ? Map.of() : (Map<String, Object>) info.getOrDefault("embedding", Map.of());
            String remoteModel = embedding.get("model") == null ? null : String.valueOf(embedding.get("model"));
            int dims = embedding.get("dims") instanceof Number n ? n.intValue() : 0;
            int chunkCount = info != null && info.get("chunkCount") instanceof Number n ? n.intValue() : 0;
            return new ProbeResult(alive, service,
                    info == null ? null : String.valueOf(info.get("name")),
                    remoteModel, dims, chunkCount, null, indexWarning(chunkCount));
        } catch (Exception e) {
            return new ProbeResult(alive, service, null, null, 0, 0,
                    "库信息获取失败（确认 vault 标识正确）: " + e.getMessage(), null);
        }
    }

    /** 仓库发现：调外部 info（不带 vault）列出全部可接入仓库，供表单点选 vaultId。 */
    @SuppressWarnings("unchecked")
    public List<com.agentx.rag.web.dto.ExternalKbDtos.VaultInfo> discover(String baseUrl, String infoPath) {
        try {
            Map<String, Object> resp = ExternalKbHttp.client(baseUrl)
                    .get().uri(infoPath).retrieve().body(Map.class);
            Object vaults = resp == null ? null : resp.get("vaults");
            if (!(vaults instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .filter(o -> o instanceof Map)
                    .map(o -> (Map<String, Object>) o)
                    .map(v -> {
                        Map<String, Object> emb = v.get("embedding") instanceof Map<?, ?> m
                                ? (Map<String, Object>) m : Map.of();
                        return new com.agentx.rag.web.dto.ExternalKbDtos.VaultInfo(
                                String.valueOf(v.get("vaultId")),
                                String.valueOf(v.get("name")),
                                v.get("docCount") instanceof Number n ? n.intValue() : 0,
                                v.get("chunkCount") instanceof Number n ? n.intValue() : 0,
                                emb.get("model") == null ? null : String.valueOf(emb.get("model")),
                                emb.get("dims") instanceof Number n ? n.intValue() : 0);
                    })
                    .toList();
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "获取仓库列表失败: " + e.getMessage());
        }
    }

    /**
     * 探测提醒：B 方案下外部库用自己的 embedding 模型检索，两侧模型解耦——不再比对
     * 一致性。仅对"外部库尚未建立向量索引"给出提醒（否则检索必然无结果）。
     */
    private static String indexWarning(int chunkCount) {
        return chunkCount <= 0
                ? "外部库尚未建立向量索引，检索将无结果——请先在外部库侧完成入库"
                : null;
    }

    private void apply(ExternalKb kb, UpsertRequest req) {
        kb.setName(req.name());
        kb.setBaseUrl(req.baseUrl().replaceAll("/+$", ""));
        kb.setVaultId(req.vaultId());
        if (req.heartbeatPath() != null && !req.heartbeatPath().isBlank()) kb.setHeartbeatPath(req.heartbeatPath());
        if (req.infoPath() != null && !req.infoPath().isBlank()) kb.setInfoPath(req.infoPath());
        if (req.searchPath() != null && !req.searchPath().isBlank()) kb.setSearchPath(req.searchPath());
        if (req.topK() != null) kb.setTopK(req.topK());
        if (req.similarityThreshold() != null) kb.setSimilarityThreshold(req.similarityThreshold());
        kb.setEnabled(req.enabled());
    }
}
