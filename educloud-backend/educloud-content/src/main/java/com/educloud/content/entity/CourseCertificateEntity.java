package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 完课证书（角色化动态流阶段 3）：学员完课（学习进度 100%）时自动生成。
 * 表 {@code course_certificate} 建在 educloud_content 库（学习成果域），
 * {@code uk_user_course} 唯一约束保证同一学员同一课程仅颁发一次（幂等）。
 */
@Data
@TableName("course_certificate")
public class CourseCertificateEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 证书编号（唯一），格式 CERT-{yyyyMMdd}-{6位随机}。 */
    private String certNo;

    /** 学员ID。 */
    private Long userId;

    /** 课程ID。 */
    private Long courseId;

    /** 课程标题快照（颁发时刻）。 */
    private String courseTitle;

    /** 颁发时间。 */
    private LocalDateTime issuedAt;

    private LocalDateTime createdAt;
}
