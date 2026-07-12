package com.agentx.rag.vector;

import com.agentx.infra.ai.client.EmbeddingModelFactory;
import com.agentx.rag.domain.KnowledgeBase;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.UUID;

/**
 * 向量库工厂：按知识库绑定的 embedding 模型构建 PgVectorStore（同表 vector_store，
 * schema 由 Flyway V4 管理）。实例按 embedding 模型维度缓存——store 内部持有
 * embedding 模型用于写入时向量化与查询向量化，必须与知识库一致。
 */
@Component
public class VectorStoreFactory {

    private static final UUID DEFAULT_KEY = new UUID(0, 0);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModelFactory embeddingModelFactory;
    private final int dimensions;
    private final Cache<UUID, VectorStore> cache = Caffeine.newBuilder()
            .maximumSize(16)
            .expireAfterAccess(Duration.ofHours(12))
            .build();

    public VectorStoreFactory(JdbcTemplate jdbcTemplate,
                              EmbeddingModelFactory embeddingModelFactory,
                              @Value("${agentx.rag.vector-dimensions:1024}") int dimensions) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModelFactory = embeddingModelFactory;
        this.dimensions = dimensions;
    }

    public VectorStore forKb(KnowledgeBase kb) {
        UUID key = kb.getEmbeddingModelId() == null ? DEFAULT_KEY : kb.getEmbeddingModelId();
        return cache.get(key, k -> build(kb.getEmbeddingModelId()));
    }

    private VectorStore build(UUID embeddingModelId) {
        EmbeddingModel embeddingModel = embeddingModelId == null
                ? embeddingModelFactory.getDefault()
                : embeddingModelFactory.get(embeddingModelId);
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("vector_store")
                .dimensions(dimensions)
                .initializeSchema(false)
                .build();
    }
}
