package com.agentx.coding.runtime;

import com.agentx.infra.ai.stream.ApprovalRegistry;
import com.agentx.infra.ai.stream.ChatStreamContext;
import com.agentx.infra.ai.stream.ToolEventSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/** 审批网关：阻塞等回传，批准放行 / 拒绝或超时不执行（设计文档 §6）。 */
class ApprovalGateTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private final ApprovalRegistry registry = new ApprovalRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 捕获 approval-request 帧里的 approvalId（前端回传所依赖的标识） */
    private final AtomicReference<String> requestedApprovalId = new AtomicReference<>();
    private final CountDownLatch requested = new CountDownLatch(1);
    private final AtomicBoolean delegateCalled = new AtomicBoolean(false);

    private ChatStreamContext context;
    private ToolCallback delegate;

    @BeforeEach
    void setUp() {
        ToolEventSink sink = new ToolEventSink() {
            @Override
            public void onToolCall(String callId, String toolName, String argsJson) {}

            @Override
            public void onToolResult(String callId, String toolName, String result) {}

            @Override
            public void onApprovalRequest(String approvalId, String toolName, String kind,
                                          Map<String, Object> preview) {
                requestedApprovalId.set(approvalId);
                requested.countDown();
            }
        };
        context = ChatStreamContext.of(UUID.randomUUID(), CONVERSATION_ID, null,
                Set.of(), UUID.randomUUID(), "ASK", sink);
        delegate = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return new DefaultToolDefinition("runShell", "执行命令", "{}");
            }

            @Override
            public String call(String toolInput) {
                delegateCalled.set(true);
                return "exit=0\nok";
            }
        };
    }

    /** 在后台线程执行 gate.call（模拟工具线程阻塞），返回结果 future。 */
    private CompletableFuture<String> callAsync(ApprovalGate gate) {
        return CompletableFuture.supplyAsync(() -> gate.call("{\"command\":\"ls\"}"));
    }

    @Test
    void approveUnblocksAndExecutesDelegate() throws Exception {
        ApprovalGate gate = new ApprovalGate(delegate, context, registry, objectMapper, 5_000);
        CompletableFuture<String> result = callAsync(gate);

        assertThat(requested.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(result).isNotDone(); // 审批前保持阻塞

        boolean hit = registry.resolve(UUID.fromString(requestedApprovalId.get()), true);
        assertThat(hit).isTrue();
        assertThat(result.get(2, TimeUnit.SECONDS)).contains("exit=0");
        assertThat(delegateCalled).isTrue();
    }

    @Test
    void rejectReturnsRefusalWithoutExecuting() throws Exception {
        ApprovalGate gate = new ApprovalGate(delegate, context, registry, objectMapper, 5_000);
        CompletableFuture<String> result = callAsync(gate);

        assertThat(requested.await(2, TimeUnit.SECONDS)).isTrue();
        registry.resolve(UUID.fromString(requestedApprovalId.get()), false);

        assertThat(result.get(2, TimeUnit.SECONDS)).contains("拒绝");
        assertThat(delegateCalled).isFalse();
    }

    @Test
    void timeoutReturnsSkippedWithoutExecuting() throws Exception {
        ApprovalGate gate = new ApprovalGate(delegate, context, registry, objectMapper, 150);
        String result = gate.call("{\"command\":\"ls\"}"); // 无人回传，等到超时

        assertThat(result).contains("未获批准");
        assertThat(delegateCalled).isFalse();
    }

    @Test
    void cancelConversationRejectsPendingApproval() throws Exception {
        ApprovalGate gate = new ApprovalGate(delegate, context, registry, objectMapper, 5_000);
        CompletableFuture<String> result = callAsync(gate);

        assertThat(requested.await(2, TimeUnit.SECONDS)).isTrue();
        // 断流兜底：会话级取消 → 未决审批一律按拒绝解冻
        registry.cancelConversation(CONVERSATION_ID);

        assertThat(result.get(2, TimeUnit.SECONDS)).contains("拒绝");
        assertThat(delegateCalled).isFalse();
    }
}
