-- EduCloud Course 数据库：业务表（V001）
-- 依据：docs/superpowers/specs/2026-08-23-educloud-course-design.md 第 5.2 节（8 张业务表）
-- 与 docs/superpowers/specs/2026-08-18-educloud-data-design.md 第 3 节通用审计字段约定
-- （created_by BIGINT NULL / created_at DATETIME(3) NOT NULL / updated_by BIGINT NULL / updated_at DATETIME(3) NOT NULL）。
-- 约定：Snowflake BIGINT 主键（无 AUTO_INCREMENT）；状态字段 VARCHAR + 应用层状态机校验；
-- 不建外键（跨服务引用由服务层校验，与 V000/file V001 风格一致）。

-- 课程分类：slug 全局唯一；parent_id 自关联可空（分类树）；公开读取只返回可见分类。
CREATE TABLE course_category (
  id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  name VARCHAR(128) NOT NULL,
  slug VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL, -- 状态（VISIBLE/HIDDEN，应用层枚举校验）
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_category_slug (slug),
  KEY idx_course_category_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 课程聚合根：公开读取只跟随 published_version_id；教师编辑只操作 draft_version_id。
CREATE TABLE course (
  id BIGINT NOT NULL,
  owner_teacher_id BIGINT NOT NULL,
  lifecycle_status VARCHAR(32) NOT NULL, -- DRAFT/PENDING_REVIEW/PUBLISHED/OFFLINE/ARCHIVED
  published_version_id BIGINT NULL,
  draft_version_id BIGINT NULL,
  published_at DATETIME(3) NULL,
  rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  rating_count INT NOT NULL DEFAULT 0,
  enrollment_count INT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0, -- 乐观锁
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_course_owner_status (owner_teacher_id, lifecycle_status),
  KEY idx_course_published_at (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 课程不可变版本：提交审核后不可原地修改；(course_id, version_no) 唯一。
CREATE TABLE course_version (
  id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  category_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  subtitle VARCHAR(255) NULL,
  description TEXT NULL,
  cover_file_id BIGINT NULL,
  level VARCHAR(16) NOT NULL, -- BEGINNER/INTERMEDIATE/ADVANCED
  price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
  version_status VARCHAR(32) NOT NULL, -- DRAFT/PENDING_REVIEW/REJECTED/PUBLISHED/SUPERSEDED/WITHDRAWN
  content_hash VARCHAR(64) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_version_no (course_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 授课教师（负责人 + 共同授课）：(course_id, teacher_id) 唯一；归属校验为服务内硬规则。
CREATE TABLE course_teacher (
  id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  teacher_role VARCHAR(16) NOT NULL, -- OWNER/CO_TEACHER
  joined_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_teacher (course_id, teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 审核提交：course_version_id 唯一（一个版本至多一条提交）；驳回时 reason 必填。
CREATE TABLE course_audit_submission (
  id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  course_version_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL, -- PENDING/APPROVED/REJECTED/WITHDRAWN
  submitted_by BIGINT NOT NULL,
  submitted_at DATETIME(3) NOT NULL,
  withdrawn_at DATETIME(3) NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME(3) NULL,
  reason VARCHAR(512) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_audit_submission_version (course_version_id),
  KEY idx_course_audit_submission_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 选课：(course_id, student_id) 唯一（幂等）；version 行内递增（Enrollment 聚合乐观锁）。
CREATE TABLE course_enrollment (
  id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  source VARCHAR(16) NOT NULL, -- FREE/ORDER
  source_order_id BIGINT NULL,
  status VARCHAR(16) NOT NULL, -- ACTIVE/REVOKED
  enrolled_at DATETIME(3) NOT NULL,
  access_ended_at DATETIME(3) NULL,
  revoke_reason VARCHAR(255) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_enrollment (course_id, student_id),
  KEY idx_enrollment_student_status (student_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 内容就绪投影：一课程一行（course_id 唯一）+ source_event_id 唯一（幂等）；M05 仅建表不激活 gate。
CREATE TABLE course_content_readiness_projection (
  id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  content_root_id BIGINT NULL,
  published_revision_id BIGINT NULL,
  ready TINYINT(1) NOT NULL DEFAULT 0,
  source_event_id VARCHAR(36) NOT NULL,
  last_aggregate_version BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_readiness_course (course_id),
  UNIQUE KEY uk_course_readiness_event (source_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 评价：rating 1-5；(course_id, student_id) 唯一（upsert 语义）；评价表仍是事实来源。
CREATE TABLE course_review (
  id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  rating TINYINT NOT NULL, -- 1-5
  content TEXT NULL,
  status VARCHAR(16) NOT NULL, -- VISIBLE/HIDDEN
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_review (course_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 应用账号权限：业务表逐表授权（不授予库级权限；与 V000 授权风格一致）。
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_course.course_category TO 'course_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_course.course TO 'course_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_course.course_version TO 'course_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_course.course_teacher TO 'course_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_course.course_audit_submission TO 'course_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_course.course_enrollment TO 'course_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_course.course_content_readiness_projection TO 'course_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_course.course_review TO 'course_app'@'%';
