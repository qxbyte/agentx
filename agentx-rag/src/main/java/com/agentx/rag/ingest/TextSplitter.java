package com.agentx.rag.ingest;

import java.util.List;

/** 文本切片策略——摄取流水线按文档类型选择实现（Markdown 结构感知 / 通用句读窗口）。 */
public interface TextSplitter {

    List<String> split(String text);
}
