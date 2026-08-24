-- EduCloud Content 数据库：业务表（V001）
-- 依据：docs/superpowers/specs/2026-08-24-educloud-content-design.md

-- 1. 课程内容根表
CREATE TABLE course_content (
  id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  published_revision_id BIGINT NULL,
  aggregate_version BIGINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_content_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. 内容不可变修订版本表
CREATE TABLE content_revision (
  id BIGINT NOT NULL,
  course_content_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  revision_no INT NOT NULL,
  revision_status VARCHAR(32) NOT NULL, -- DRAFT/PENDING_REVIEW/PUBLISHED/SUPERSEDED/REJECTED/WITHDRAWN
  content_hash VARCHAR(64) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  submitted_at DATETIME(3) NULL,
  published_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_revision_no (course_id, revision_no),
  KEY idx_content_revision_status (revision_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. 章节表
CREATE TABLE chapter (
  id BIGINT NOT NULL,
  content_revision_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  description VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE/DELETED
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_revision_sort (content_revision_id, sort_order),
  KEY idx_chapter_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. 课件表
CREATE TABLE courseware (
  id BIGINT NOT NULL,
  content_revision_id BIGINT NOT NULL,
  chapter_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  courseware_type VARCHAR(32) NOT NULL, -- VIDEO/DOCUMENT/PPT/EXTERNAL_URL
  file_id BIGINT NULL,
  external_url VARCHAR(1024) NULL,
  duration_seconds INT NOT NULL DEFAULT 0,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  free_preview TINYINT(1) NOT NULL DEFAULT 0, -- 1=是, 0=否
  sort_order INT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE/DELETED
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chapter_sort (chapter_id, sort_order),
  KEY idx_courseware_course (course_id),
  KEY idx_courseware_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 5. 学员课时学习进度表
CREATE TABLE user_courseware_progress (
  id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  courseware_id BIGINT NOT NULL,
  position_seconds INT NOT NULL DEFAULT 0,
  watched_seconds INT NOT NULL DEFAULT 0,
  completed TINYINT(1) NOT NULL DEFAULT 0,
  completed_at DATETIME(3) NULL,
  last_learned_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_courseware (student_id, courseware_id),
  KEY idx_progress_student_course (student_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 6. 学员课程整体进度聚合表
CREATE TABLE user_course_progress (
  id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  completed_courseware_count INT NOT NULL DEFAULT 0,
  total_courseware_count INT NOT NULL DEFAULT 0,
  progress_percent INT NOT NULL DEFAULT 0,
  last_learned_courseware_id BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_course (student_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 7. 内容审核单表
CREATE TABLE content_audit_submission (
  id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  content_revision_id BIGINT NOT NULL,
  revision_no INT NOT NULL,
  snapshot_json LONGTEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING/APPROVED/REJECTED/WITHDRAWN
  submitted_by BIGINT NOT NULL,
  reviewed_by BIGINT NULL,
  reject_reason VARCHAR(512) NULL,
  submitted_at DATETIME(3) NOT NULL,
  reviewed_at DATETIME(3) NULL,
  withdrawn_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_audit_revision (content_revision_id),
  KEY idx_content_audit_status (status, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 权限授权
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.course_content TO 'content_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.content_revision TO 'content_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.chapter TO 'content_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.courseware TO 'content_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.user_courseware_progress TO 'content_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.user_course_progress TO 'content_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.content_audit_submission TO 'content_app'@'%';
