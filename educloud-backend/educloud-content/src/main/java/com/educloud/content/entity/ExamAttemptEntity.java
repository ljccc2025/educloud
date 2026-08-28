package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_attempt")
public class ExamAttemptEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private Long studentId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private Integer score;
    private Integer passed;
    private String answersJson;
    private Integer tabSwitchCount;
    private Integer flagged;
    private Integer timeout;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
