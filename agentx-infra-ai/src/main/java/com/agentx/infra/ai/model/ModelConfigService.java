package com.agentx.infra.ai.model;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import com.agentx.common.util.UuidV7;
import com.agentx.infra.ai.crypto.ApiKeyCrypto;
import com.agentx.infra.ai.client.ModelConfigChangedEvent;
import com.agentx.infra.ai.web.dto.ModelConfigDtos.UpsertRequest;
import com.agentx.infra.ai.web.dto.ModelConfigDtos.View;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModelConfigService {
    private final ModelConfigRepository repository;
    private final ApiKeyCrypto crypto;
    private final ApplicationEventPublisher eventPublisher;

    public List<View> list() {
        return repository.findAll().stream().map(this::toView).toList();
    }

    /** 面向用户的可选 CHAT 模型（仅启用项，默认模型排前），供输入框模型选择器使用。 */
    public List<com.agentx.infra.ai.web.dto.ModelConfigDtos.ModelOption> listSelectableChat() {
        return repository.findByTypeAndEnabledTrueOrderByDefaultModelDescNameAsc(ModelType.CHAT)
                .stream()
                .map(c -> new com.agentx.infra.ai.web.dto.ModelConfigDtos.ModelOption(
                        c.getId(), c.getName(), c.getModelName(), c.isDefaultModel()))
                .toList();
    }

    @Transactional
    public View create(UpsertRequest req) {
        repository.findByName(req.name()).ifPresent(c -> {
            throw new BizException(ErrorCode.CONFLICT, "同名配置已存在");
        });
        ModelConfig c = new ModelConfig();
        c.setId(UuidV7.next());
        apply(c, req);
        return toView(repository.save(c));
    }

    @Transactional
    public View update(UUID id, UpsertRequest req) {
        ModelConfig c = get(id);
        apply(c, req);
        View view = toView(repository.save(c));
        eventPublisher.publishEvent(new ModelConfigChangedEvent(id));
        return view;
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(get(id));
        eventPublisher.publishEvent(new ModelConfigChangedEvent(id));
    }

    @Transactional
    public View markDefault(UUID id) {
        ModelConfig c = get(id);
        repository.findByTypeAndDefaultModelTrue(c.getType()).forEach(old -> {
            old.setDefaultModel(false);
            repository.save(old);
        });
        c.setDefaultModel(true);
        return toView(repository.save(c));
    }

    public ModelConfig getDefaultChat() {
        return repository.findFirstByTypeAndDefaultModelTrueAndEnabledTrue(ModelType.CHAT)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "未配置默认 CHAT 模型"));
    }

    /** 按类型取默认启用配置（EMBEDDING 等）。 */
    public ModelConfig getDefaultOf(ModelType type) {
        return repository.findFirstByTypeAndDefaultModelTrueAndEnabledTrue(type)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND,
                        "未配置默认 " + type + " 模型"));
    }

    /** 按类型取默认启用配置，无则空——供"可选能力"（如 rerank 精排开关）判定是否启用。 */
    public java.util.Optional<ModelConfig> findDefaultOf(ModelType type) {
        return repository.findFirstByTypeAndDefaultModelTrueAndEnabledTrue(type);
    }

    /** 按 ID 取启用中的配置（工厂消费）。 */
    public ModelConfig getEnabled(UUID id) {
        ModelConfig c = get(id);
        if (!c.isEnabled()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "模型配置已禁用: " + c.getName());
        }
        return c;
    }

    public String decryptedApiKey(ModelConfig c) {
        return c.getApiKeyEnc() == null ? null : crypto.decrypt(c.getApiKeyEnc());
    }

    private ModelConfig get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "模型配置不存在"));
    }

    private void apply(ModelConfig c, UpsertRequest req) {
        c.setName(req.name());
        c.setProviderType(req.providerType());
        c.setBaseUrl(req.baseUrl());
        if (req.apiKey() != null && !req.apiKey().isBlank()) {
            c.setApiKeyEnc(crypto.encrypt(req.apiKey()));
        }
        c.setModelName(req.modelName());
        c.setType(req.type());
        c.setEnabled(req.enabled());
    }

    private View toView(ModelConfig c) {
        String masked = c.getApiKeyEnc() == null ? null : "sk-****";
        return new View(c.getId(), c.getName(), c.getProviderType(), c.getBaseUrl(),
                masked, c.getModelName(), c.getType(), c.isDefaultModel(), c.isEnabled(),
                c.getCreatedAt());
    }
}
