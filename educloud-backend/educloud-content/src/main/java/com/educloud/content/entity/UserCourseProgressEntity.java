package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_course_progress")
public class UserCourseProgressEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long studentId;

    private Long courseId;

    private Integer completedCoursewareCount;

    private Integer totalCoursewareCount;

    private Integer progressPercent;

    private Long lastLearnedCoursewareId;

    private LocalDateTime updatedAt;
}
