package com.agentx.coding.runtime;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话实时编码模式表：模式切换「立即生效」的关键。
 * 开轮时由 {@link CodingStreamCustomizer} 按请求模式播种；用户轮内切换模式经
 * 回传端点更新；{@link ApprovalGate} 在每次危险工具执行前查询——
 * 切到 AUTO 后，本轮后续工具调用直接放行，不再发起审批。
 * <p>
 * 条目随会话覆盖复用（下一轮开轮重新播种），不主动清理——
 * 一机一用户的本地工具，每会话一条极小记录，进程存活期内可忽略。
 */
@Component
public class CodingModeRegistry {

    private record Entry(UUID userId, CodingMode mode) {}

    private final Map<UUID, Entry> live = new ConcurrentHashMap<>();

    /** 开轮播种：以本轮请求模式为基准，后续轮内切换在此之上覆盖。 */
    public void seed(UUID conversationId, UUID userId, CodingMode mode) {
        live.put(conversationId, new Entry(userId, mode));
    }

    /** 当前实时模式；无记录（会话从未开轮）回退 fallback。 */
    public CodingMode currentOr(UUID conversationId, CodingMode fallback) {
        Entry e = live.get(conversationId);
        return e == null ? fallback : e.mode();
    }

    /** 轮内切换：仅播种者本人可更新（归属不符视同未命中，不泄漏会话存在性）。 */
    public boolean update(UUID conversationId, UUID userId, CodingMode mode) {
        Entry e = live.get(conversationId);
        if (e == null || !e.userId().equals(userId)) {
            return false;
        }
        live.put(conversationId, new Entry(userId, mode));
        return true;
    }
}
