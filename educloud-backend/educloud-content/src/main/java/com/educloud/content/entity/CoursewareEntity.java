package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("courseware")
public class CoursewareEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long contentRevisionId;

    private Long chapterId;

    private Long courseId;

    private String title;

    /**
     * VIDEO, DOCUMENT, PPT, EXTERNAL_URL
     */
    private String coursewareType;

    private Long fileId;

    private String externalUrl;

    private Integer durationSeconds;

    private Long sizeBytes;

    private Boolean freePreview;

    private Integer sortOrder;

    /**
     * ACTIVE, DELETED
     */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
