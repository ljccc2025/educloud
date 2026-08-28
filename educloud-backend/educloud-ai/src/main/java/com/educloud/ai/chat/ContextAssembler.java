package com.educloud.ai.chat;

import com.educloud.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 上下文装配（规格 §5.5）：system + 最近 N 条历史 + 本次提问，prompt token 预算超限时
 * 从最旧的历史开始丢弃（system 与本次提问不裁）。学生输入只进 user 角色，不拼进 system。
 * token 估算为保守启发式：中文字符≈1 token，ASCII≈4 字符/token（实测 Qwen 中文 1-1.5 字/token）。
 */
@Component
public class ContextAssembler {

    private final AiProperties properties;

    public ContextAssembler(AiProperties properties) {
        this.properties = properties;
    }

    public List<ChatTurn> assemble(String systemPrompt, List<ChatTurn> historyAsc, String question) {
        int maxHistory = properties.context().maxHistoryMessages();
        int maxPromptTokens = properties.context().maxPromptTokens();

        Deque<ChatTurn> recent = new ArrayDeque<>();
        int offset = Math.max(0, historyAsc.size() - maxHistory);
        for (ChatTurn turn : historyAsc.subList(offset, historyAsc.size())) {
            recent.addLast(turn);
        }

        long budgetAfterFixed = maxPromptTokens - estimate(systemPrompt) - estimate(question);
        while (!recent.isEmpty() && budgetAfterFixed - estimateOf(recent) < 0) {
            recent.removeFirst();
        }

        List<ChatTurn> turns = new ArrayList<>();
        turns.add(new ChatTurn("system", systemPrompt));
        turns.addAll(recent);
        turns.add(new ChatTurn("user", question));
        return turns;
    }

    private long estimateOf(Deque<ChatTurn> turns) {
        long total = 0;
        for (ChatTurn turn : turns) {
            total += estimate(turn.content());
        }
        return total;
    }

    int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int ascii = 0;
        int nonAscii = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < 128) {
                ascii++;
            } else {
                nonAscii++;
            }
        }
        return nonAscii + (ascii + 3) / 4;
    }
}
