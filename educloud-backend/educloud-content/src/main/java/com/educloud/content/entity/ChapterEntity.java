package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chapter")
public class ChapterEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long contentRevisionId;

    private Long courseId;

    private String title;

    private String description;

    private Integer sortOrder;

    /**
     * ACTIVE, DELETED
     */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
