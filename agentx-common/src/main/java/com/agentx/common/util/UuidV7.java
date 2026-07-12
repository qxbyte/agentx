package com.agentx.common.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID v7 生成器：48bit 毫秒时间戳 + 版本/变体位 + 随机位，字符串排序即时间排序。
 */
public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    public static UUID next() {
        long ts = System.currentTimeMillis();
        long msb = (ts << 16) | 0x7000L | (RANDOM.nextLong() & 0x0FFFL);
        long lsb = 0x8000000000000000L | (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(msb, lsb);
    }
}
