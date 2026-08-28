package com.educloud.content.exam;

import java.util.List;

/** 组卷快照：判分与展示只读此快照，不受题库编辑影响。 */
public record ExamQuestionSnapshot(
        Long questionId,
        String questionType,
        List<String> options,
        List<Integer> answer,
        int score) {
}
