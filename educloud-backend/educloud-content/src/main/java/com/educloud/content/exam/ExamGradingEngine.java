package com.educloud.content.exam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 客观题判分纯函数：SINGLE/JUDGE 索引相等，MULTIPLE 集合完全相等；不做部分得分。 */
public final class ExamGradingEngine {

    public record GradedQuestion(Long questionId, String questionType, int score, boolean correct) {
    }

    public record GradeResult(int earnedScore, int totalScore, List<GradedQuestion> details) {
    }

    private ExamGradingEngine() {
    }

    public static GradeResult grade(List<ExamQuestionSnapshot> paper, Map<Long, List<Integer>> answers) {
        List<GradedQuestion> details = new ArrayList<>();
        int earned = 0;
        int total = 0;
        for (ExamQuestionSnapshot question : paper) {
            List<Integer> chosen = answers == null
                    ? List.of()
                    : answers.getOrDefault(question.questionId(), List.of());
            boolean correct = isCorrect(question, chosen);
            if (correct) {
                earned += question.score();
            }
            total += question.score();
            details.add(new GradedQuestion(question.questionId(), question.questionType(), question.score(), correct));
        }
        return new GradeResult(earned, total, Collections.unmodifiableList(details));
    }

    private static boolean isCorrect(ExamQuestionSnapshot question, List<Integer> chosen) {
        List<Integer> expected = question.answer();
        if ("MULTIPLE".equals(question.questionType())) {
            return chosen.size() == expected.size()
                    && chosen.stream().sorted().toList().equals(expected.stream().sorted().toList());
        }
        return chosen.size() == 1 && chosen.get(0).equals(expected.get(0));
    }
}
