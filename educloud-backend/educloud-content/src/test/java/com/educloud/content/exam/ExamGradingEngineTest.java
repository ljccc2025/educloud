package com.educloud.content.exam;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExamGradingEngineTest {

    private static ExamQuestionSnapshot q(Long id, String type, List<Integer> answer, int score) {
        return new ExamQuestionSnapshot(id, type, List.of("A", "B", "C", "D"), answer, score);
    }

    @Test
    void single_correctAndWrong() {
        var paper = List.of(q(1L, "SINGLE", List.of(0), 10), q(2L, "SINGLE", List.of(2), 10));
        var result = ExamGradingEngine.grade(paper, Map.of(1L, List.of(0), 2L, List.of(1)));
        assertThat(result.earnedScore()).isEqualTo(10);
        assertThat(result.totalScore()).isEqualTo(20);
        assertThat(result.details()).extracting("correct").containsExactly(true, false);
    }

    @Test
    void multiple_requiresExactSetMatch() {
        var paper = List.of(q(1L, "MULTIPLE", List.of(0, 2), 20));
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(0, 2))).earnedScore()).isEqualTo(20);
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(2, 0))).earnedScore()).isEqualTo(20);
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(0))).earnedScore()).isZero();
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(0, 1, 2))).earnedScore()).isZero();
    }

    @Test
    void judge_treatedAsSingle() {
        var paper = List.of(q(1L, "JUDGE", List.of(0), 5));
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(0))).earnedScore()).isEqualTo(5);
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(1))).earnedScore()).isZero();
    }

    @Test
    void unansweredAndUnknownQuestionScoreZero() {
        var paper = List.of(q(1L, "SINGLE", List.of(0), 10), q(2L, "SINGLE", List.of(1), 10));
        var result = ExamGradingEngine.grade(paper, Map.of(1L, List.of(0), 99L, List.of(1)));
        assertThat(result.earnedScore()).isEqualTo(10);
        assertThat(result.totalScore()).isEqualTo(20);
    }

    @Test
    void emptyAnswersScoresZero() {
        var paper = List.of(q(1L, "SINGLE", List.of(0), 10));
        assertThat(ExamGradingEngine.grade(paper, Map.of()).earnedScore()).isZero();
        assertThat(ExamGradingEngine.grade(paper, null).earnedScore()).isZero();
    }
}
