package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_bank_question")
public class ExamBankQuestionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long teacherId;
    private String questionType;
    private String stem;
    private String options;
    private String answer;
    private String analysis;
    private Integer defaultScore;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
