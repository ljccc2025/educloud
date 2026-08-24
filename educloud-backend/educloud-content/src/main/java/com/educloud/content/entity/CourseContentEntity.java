package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_content")
public class CourseContentEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long courseId;

    private Long publishedRevisionId;

    @Version
    private Long aggregateVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
