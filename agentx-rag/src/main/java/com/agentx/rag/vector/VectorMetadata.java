package com.agentx.rag.vector;

/** vector_store metadata 键常量——写入与检索过滤的唯一契约。 */
public final class VectorMetadata {
    public static final String KB_ID = "kb_id";
    public static final String DOC_ID = "doc_id";
    public static final String SEGMENT_ID = "segment_id";
    public static final String DOC_NAME = "doc_name";
    // 来源定位（可选，外部知识库命中携带）：文件路径 / 章节链 / 原文行号区间
    public static final String DOC_PATH = "doc_path";
    public static final String HEADINGS = "headings";
    public static final String START_LINE = "start_line";
    public static final String END_LINE = "end_line";

    private VectorMetadata() {}
}
