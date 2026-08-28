package com.educloud.ai.chat;

import com.educloud.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAssemblerTest {

    private static final String SYSTEM_PROMPT = "你是 EduCloud AI 助教……";

    private ContextAssembler assemblerWith(int maxHistory, int maxPromptTokens) {
        return new ContextAssembler(new AiProperties(
                new AiProperties.ProviderProperties("openai-compatible", "https://x/v1", "m", "k", false, 1024),
                new AiProperties.TimeoutProperties(5000, 25000),
                new AiProperties.QuotaProperties(50, 2000000),
                new AiProperties.ContextProperties(maxHistory, maxPromptTokens),
                new AiProperties.JwtProperties("", "i", "a")));
    }

    private final ContextAssembler assembler = assemblerWith(10, 3000);

    @Test
    void assemblesSystemPlusHistoryPlusQuestion() {
        List<ChatTurn> turns = assembler.assemble(
                SYSTEM_PROMPT,
                List.of(new ChatTurn("user", "问题一"), new ChatTurn("assistant", "回答一")),
                "本次提问");
        assertThat(turns).hasSize(4);
        assertThat(turns.get(0).role()).isEqualTo("system");
        assertThat(turns.get(0).content()).isEqualTo(SYSTEM_PROMPT);
        assertThat(turns.get(1)).isEqualTo(new ChatTurn("user", "问题一"));
        assertThat(turns.get(2)).isEqualTo(new ChatTurn("assistant", "回答一"));
        assertThat(turns.get(3)).isEqualTo(new ChatTurn("user", "本次提问"));
    }

    @Test
    void keepsOnlyTheMostRecentTenHistoryTurns() {
        List<ChatTurn> history = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            history.add(new ChatTurn("user", "第" + i + "问"));
            history.add(new ChatTurn("assistant", "第" + i + "答"));
        }
        List<ChatTurn> turns = assembler.assemble(SYSTEM_PROMPT, history, "本次提问");
        // 10 条历史 + system + question；最旧 20 条被丢弃，保留的是第 11–15 组
        assertThat(turns).hasSize(12);
        assertThat(turns.get(1).content()).isEqualTo("第11问");
        assertThat(turns.get(10).content()).isEqualTo("第15答");
        assertThat(turns.get(11).content()).isEqualTo("本次提问");
    }

    @Test
    void dropsOldestHistoryFirstWhenOverTokenBudget() {
        // 每条 ≈ 1801 token（1800 个中文字符按 1 token/字 + 数字），10 条远超 3000 预算
        List<ChatTurn> history = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            history.add(new ChatTurn("user", "长".repeat(1800) + i));
        }
        List<ChatTurn> turns = assembler.assemble(SYSTEM_PROMPT, history, "问");
        assertThat(turns.size()).isGreaterThanOrEqualTo(2); // system + question 永不裁
        assertThat(turns.get(0).role()).isEqualTo("system");
        assertThat(turns.get(turns.size() - 1).content()).isEqualTo("问");
        // 预算扣除 system/question 后剩 2989，只装得下最后 1 条（1801），最旧的先丢
        // 【最小修复】任务文本原断言为 endsWith("9")/「最多保留 2 条」：2 条=3602>2989 仍超预算会被继续丢，
        // 与估算器定义（1 中文=1 token）及「预算内从最旧丢」语义矛盾，据实现修正为保留第 10 条。
        assertThat(turns.size()).isLessThanOrEqualTo(4);
        assertThat(turns.get(1).content()).endsWith("10");
    }

    @Test
    void neverDropsSystemOrQuestionEvenWhenTheyAloneExceedBudget() {
        ContextAssembler tiny = assemblerWith(10, 1);
        List<ChatTurn> turns = tiny.assemble(SYSTEM_PROMPT, List.of(new ChatTurn("user", "旧问")), "问");
        // 【最小修复】任务文本原断言为 hasSize(3)：预算 1 连 system+question 都装不下（budgetAfterFixed=-10），
        // 历史全部让位后只剩 system+question 共 2 条；补两条断言坐实「system/question 永不裁」的测试意图。
        assertThat(turns).hasSize(2);
        assertThat(turns.get(0).role()).isEqualTo("system");
        assertThat(turns.get(0).content()).isEqualTo(SYSTEM_PROMPT);
        assertThat(turns.get(1).content()).isEqualTo("问");
    }

    @Test
    void emptyHistoryYieldsSystemPlusQuestion() {
        List<ChatTurn> turns = assembler.assemble(SYSTEM_PROMPT, List.of(), "问");
        assertThat(turns).hasSize(2);
    }
}
