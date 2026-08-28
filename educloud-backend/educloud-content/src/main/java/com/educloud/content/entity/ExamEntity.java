package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam")
public class ExamEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private String courseTitle;
    private String title;
    private String description;
    private Integer durationMinutes;
    private Integer totalScore;
    private Integer passScore;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Long teacherId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
