package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_courseware_progress")
public class UserCoursewareProgressEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long studentId;

    private Long courseId;

    private Long coursewareId;

    private Integer positionSeconds;

    private Integer watchedSeconds;

    private Boolean completed;

    private LocalDateTime completedAt;

    private LocalDateTime lastLearnedAt;
}
