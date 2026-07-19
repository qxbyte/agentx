package com.agentx.chat.service.memory;

import org.springframework.ai.chat.messages.Message;
import java.util.List;

/**
 * token 数启发式估算（无 tokenizer 依赖）：CJK 字符按 1 token/字、
 * 其余字符按 4 字符/token 折算。对中英混排文本相对主流 tokenizer
 * （qwen/deepseek/openai）偏保守 10%~20%——预算护栏宁紧勿松。
 */
public final class TokenEstimator {

    private TokenEstimator() {}

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjk++;
            } else {
                other++;
            }
        }
        return cjk + (other + 3) / 4;
    }

    public static int estimate(List<Message> messages) {
        int total = 0;
        for (Message m : messages) {
            total += estimate(m.getText());
        }
        return total;
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)   // CJK 统一表意文字
                || (c >= 0x3000 && c <= 0x303F) // CJK 标点
                || (c >= 0xFF00 && c <= 0xFFEF); // 全角形式
    }
}
