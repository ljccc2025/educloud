-- EduCloud Content 数据库：完课证书表（V004）
-- 依据：角色化动态流阶段 3（规格 2026-08-27-activity-feed-certificate-design.md §3.2/§6）
--   学员完课（学习进度 100%）时由 CertificateService 自动生成，
--   uk_user_course 保证同一学员同一课程仅颁发一次（幂等）。

CREATE TABLE IF NOT EXISTS course_certificate (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    cert_no       VARCHAR(64)  NOT NULL COMMENT '证书编号（唯一）',
    user_id       BIGINT       NOT NULL COMMENT '学员ID',
    course_id     BIGINT       NOT NULL COMMENT '课程ID',
    course_title  VARCHAR(255) NOT NULL COMMENT '课程标题快照',
    issued_at     DATETIME(3)  NOT NULL COMMENT '颁发时间',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cert_no (cert_no),
    UNIQUE KEY uk_user_course (user_id, course_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='完课证书';

-- 权限授权（与 V001 业务表一致）
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.course_certificate TO 'content_app'@'%';
