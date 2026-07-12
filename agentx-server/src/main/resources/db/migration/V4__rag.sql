-- RAG 知识库（设计文档 §4.7）：kb_segment 是管理真源，向量层可由分段全量重建。

CREATE TABLE kb_knowledge_base (
    id                   UUID PRIMARY KEY,
    user_id              UUID          NOT NULL REFERENCES sys_user (id),
    name                 VARCHAR(128)  NOT NULL,
    description          VARCHAR(512)  NOT NULL DEFAULT '',
    embedding_model_id   UUID,                     -- 空则用默认 EMBEDDING 模型
    chunk_size           INT           NOT NULL DEFAULT 800,
    chunk_overlap        INT           NOT NULL DEFAULT 80,
    top_k                INT           NOT NULL DEFAULT 5,
    similarity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_kb_user ON kb_knowledge_base (user_id);

CREATE TABLE kb_document (
    id            UUID PRIMARY KEY,
    kb_id         UUID          NOT NULL REFERENCES kb_knowledge_base (id) ON DELETE CASCADE,
    filename      VARCHAR(255)  NOT NULL,
    file_path     VARCHAR(512)  NOT NULL,
    mime_type     VARCHAR(128),
    size_bytes    BIGINT        NOT NULL DEFAULT 0,
    status        VARCHAR(16)   NOT NULL DEFAULT 'UPLOADED',  -- UPLOADED|PARSING|INGESTING|READY|FAILED
    segment_count INT           NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_doc_kb ON kb_document (kb_id, created_at DESC);

CREATE TABLE kb_segment (
    id         UUID PRIMARY KEY,
    doc_id     UUID        NOT NULL REFERENCES kb_document (id) ON DELETE CASCADE,
    kb_id      UUID        NOT NULL,
    seq_no     INT         NOT NULL,
    content    TEXT        NOT NULL,
    char_count INT         NOT NULL DEFAULT 0,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_segment_doc ON kb_segment (doc_id, seq_no);
CREATE INDEX idx_segment_kb ON kb_segment (kb_id);

CREATE TABLE rag_ingest_task (
    id          UUID PRIMARY KEY,
    doc_id      UUID        NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING|RUNNING|SUCCEEDED|FAILED
    progress    INT         NOT NULL DEFAULT 0,
    error_msg   VARCHAR(1024),
    retries     INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);
CREATE INDEX idx_ingest_doc ON rag_ingest_task (doc_id, created_at DESC);

-- Spring AI PgVectorStore 表（schema 由 Flyway 管理，应用侧 initializeSchema=false）。
-- 维度经 Flyway placeholder 注入（application.yml: spring.flyway.placeholders.vector-dim），
-- 全平台统一 embedding 维度；更换不同维度模型需新建向量表并全量重建（由分段真源重灌）。
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding vector(${vector-dim})
);
CREATE INDEX idx_vector_store_hnsw ON vector_store
    USING HNSW (embedding vector_cosine_ops);
CREATE INDEX idx_vector_store_metadata ON vector_store USING GIN (metadata);
