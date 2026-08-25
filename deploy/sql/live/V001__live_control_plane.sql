-- EduCloud Live 数据库：控制面、场次、消息、出勤与回放表（V001）
-- 依据：docs/superpowers/specs/2026-08-25-educloud-live-design.md

-- 1. 直播间基础表
CREATE TABLE IF NOT EXISTS `live_room` (
  `id` BIGINT NOT NULL COMMENT '直播间ID (雪花算法)',
  `course_id` BIGINT NOT NULL COMMENT '关联课程ID',
  `teacher_id` BIGINT NOT NULL COMMENT '主讲教师ID',
  `title` VARCHAR(128) NOT NULL COMMENT '直播间标题',
  `description` VARCHAR(1024) NULL COMMENT '直播间简介',
  `scheduled_start_at` DATETIME NOT NULL COMMENT '计划开播时间',
  `scheduled_end_at` DATETIME NOT NULL COMMENT '计划结束时间',
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED, LIVING, ENDED, CANCELLED',
  `provider_type` VARCHAR(32) NOT NULL DEFAULT 'MOCK' COMMENT '流媒体供应商: MOCK, ALIYUN, TENCENT, SRS',
  `stream_key` VARCHAR(128) NOT NULL COMMENT '推流唯一标识码',
  `allow_chat` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许弹幕(1-允许, 0-全员禁言)',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT 'CAS乐观锁版本号',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_course_id` (`course_id`),
  INDEX `idx_teacher_id` (`teacher_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播间表';

-- 2. 直播场次记录表
CREATE TABLE IF NOT EXISTS `live_session` (
  `id` BIGINT NOT NULL COMMENT '场次ID (雪花算法)',
  `room_id` BIGINT NOT NULL COMMENT '直播间ID',
  `session_no` INT NOT NULL DEFAULT 1 COMMENT '场次序号',
  `status` VARCHAR(32) NOT NULL DEFAULT 'LIVING' COMMENT '场次状态: LIVING, ENDED',
  `started_at` DATETIME NOT NULL COMMENT '实际开播时间',
  `ended_at` DATETIME NULL COMMENT '实际结课时间',
  `started_by` BIGINT NOT NULL COMMENT '开播操作人',
  `ended_by` BIGINT NULL COMMENT '结课操作人',
  `peak_viewers` INT NOT NULL DEFAULT 0 COMMENT '最高在线人数峰值',
  `total_viewers` INT NOT NULL DEFAULT 0 COMMENT '累计观看人次',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_room_id` (`room_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播场次记录表';

-- 3. 课堂弹幕与信令消息持久化表
CREATE TABLE IF NOT EXISTS `live_message` (
  `id` BIGINT NOT NULL COMMENT '消息ID (雪花算法)',
  `room_id` BIGINT NOT NULL COMMENT '直播间ID',
  `session_id` BIGINT NOT NULL COMMENT '场次ID',
  `sender_id` BIGINT NOT NULL COMMENT '发送人ID',
  `sender_name` VARCHAR(64) NOT NULL COMMENT '发送人昵称/姓名',
  `sender_role` VARCHAR(32) NOT NULL COMMENT '发送人角色: TEACHER, STUDENT, ASSISTANT, SYSTEM',
  `message_type` VARCHAR(32) NOT NULL DEFAULT 'CHAT' COMMENT '类型: CHAT, LIKE, HAND_UP, WHITEBOARD, SYSTEM',
  `content` TEXT NOT NULL COMMENT '消息正文或信令负载 JSON',
  `status` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '状态: NORMAL, RECALLED, BLOCKED',
  `sent_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间戳',
  `recalled_at` DATETIME NULL COMMENT '撤回时间',
  `recalled_by` BIGINT NULL COMMENT '撤回操作人',
  PRIMARY KEY (`id`),
  INDEX `idx_session_time` (`session_id`, `sent_at`),
  INDEX `idx_room_time` (`room_id`, `sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课堂弹幕与信令持久化表';

-- 4. 学生出勤与观看时长统计表
CREATE TABLE IF NOT EXISTS `live_attendance` (
  `id` BIGINT NOT NULL COMMENT '出勤记录ID',
  `room_id` BIGINT NOT NULL COMMENT '直播间ID',
  `session_id` BIGINT NOT NULL COMMENT '场次ID',
  `student_id` BIGINT NOT NULL COMMENT '学员ID',
  `joined_at` DATETIME NOT NULL COMMENT '首次进入时间',
  `last_active_at` DATETIME NOT NULL COMMENT '最后活跃时间',
  `left_at` DATETIME NULL COMMENT '退出时间',
  `watched_seconds` BIGINT NOT NULL DEFAULT 0 COMMENT '累计观看时长(秒)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_student` (`session_id`, `student_id`),
  INDEX `idx_student_room` (`student_id`, `room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播出勤与观看时长表';

-- 5. 录制回放文件关联表
CREATE TABLE IF NOT EXISTS `live_replay` (
  `id` BIGINT NOT NULL COMMENT '回放ID (雪花算法)',
  `room_id` BIGINT NOT NULL COMMENT '直播间ID',
  `session_id` BIGINT NOT NULL COMMENT '场次ID',
  `file_id` BIGINT NOT NULL COMMENT '关联 File 服务文件ID',
  `title` VARCHAR(128) NOT NULL COMMENT '回放标题',
  `duration_seconds` BIGINT NOT NULL DEFAULT 0 COMMENT '回放视频时长(秒)',
  `size_bytes` BIGINT NOT NULL DEFAULT 0 COMMENT '视频文件大小(字节)',
  `status` VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态: PENDING, AVAILABLE, FAILED, DELETED',
  `available_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回放可用时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_room_id` (`room_id`),
  INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='录制回放表';
