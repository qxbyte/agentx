package com.agentx.agent.domain;

/** Agent 执行形态：REACT 为工具循环对话；其余为官方五种确定性 workflow 模式。 */
public enum WorkflowType {
    REACT, CHAIN, ROUTING, PARALLELIZATION, ORCHESTRATOR_WORKERS, EVALUATOR_OPTIMIZER
}
