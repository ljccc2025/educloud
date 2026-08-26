-- EduCloud Analytics 数据库：业务聚合指标与审计读模型表（V001）
-- 依据：docs/superpowers/specs/2026-08-26-educloud-analytics-design.md

-- 1. 教师日度统计指标表
CREATE TABLE IF NOT EXISTS `daily_teacher_metrics` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `teacher_id` VARCHAR(64) NOT NULL COMMENT '教师用户ID',
    `metric_date` DATE NOT NULL COMMENT '统计日期 (YYYY-MM-DD)',
    `new_enrollments` INT NOT NULL DEFAULT 0 COMMENT '当日新增选课人数',
    `revenue_cents` BIGINT NOT NULL DEFAULT 0 COMMENT '当日归属营收(分)',
    `active_students` INT NOT NULL DEFAULT 0 COMMENT '当日活跃学员数',
    `completed_courses_count` INT NOT NULL DEFAULT 0 COMMENT '当日完课人次',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_teacher_date` (`teacher_id`, `metric_date`),
    KEY `idx_teacher_date_range` (`teacher_id`, `metric_date` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师日度统计预聚合表';

-- 2. 平台运营日度统计指标表
CREATE TABLE IF NOT EXISTS `daily_platform_metrics` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `metric_date` DATE NOT NULL COMMENT '统计日期 (YYYY-MM-DD)',
    `total_users` INT NOT NULL DEFAULT 0 COMMENT '累计用户总数',
    `new_users` INT NOT NULL DEFAULT 0 COMMENT '当日新增注册用户数',
    `total_courses` INT NOT NULL DEFAULT 0 COMMENT '累计已发布课程数',
    `new_courses` INT NOT NULL DEFAULT 0 COMMENT '当日新增发布课程数',
    `total_orders` INT NOT NULL DEFAULT 0 COMMENT '当日成功支付订单数',
    `gmv_cents` BIGINT NOT NULL DEFAULT 0 COMMENT '当日GMV总流水(分)',
    `refund_cents` BIGINT NOT NULL DEFAULT 0 COMMENT '当日退款金额(分)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_date` (`metric_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台运营日度统计表';

-- 3. 平台财务日度统计指标表
CREATE TABLE IF NOT EXISTS `daily_finance_metrics` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `metric_date` DATE NOT NULL COMMENT '统计日期 (YYYY-MM-DD)',
    `gross_revenue_cents` BIGINT NOT NULL DEFAULT 0 COMMENT '当日总流水(分)',
    `refund_amount_cents` BIGINT NOT NULL DEFAULT 0 COMMENT '当日退款金额(分)',
    `net_revenue_cents` BIGINT NOT NULL DEFAULT 0 COMMENT '当日净营收(分)',
    `order_count` INT NOT NULL DEFAULT 0 COMMENT '当日支付订单笔数',
    `refund_count` INT NOT NULL DEFAULT 0 COMMENT '当日退款笔数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_finance_date` (`metric_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='财务日度统计表';

-- 4. 课程参与度与完课率快照表
CREATE TABLE IF NOT EXISTS `course_engagement_stats` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` VARCHAR(64) NOT NULL COMMENT '课程ID',
    `course_title` VARCHAR(255) NOT NULL COMMENT '课程名称快照',
    `teacher_id` VARCHAR(64) NOT NULL COMMENT '教师ID',
    `total_enrollments` INT NOT NULL DEFAULT 0 COMMENT '累计选课人数',
    `active_learners` INT NOT NULL DEFAULT 0 COMMENT '在学学员人数',
    `completed_count` INT NOT NULL DEFAULT 0 COMMENT '已完课学员人数',
    `completion_rate` DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '完课率百分比 (0.00-100.00)',
    `avg_rating` DECIMAL(3, 2) NOT NULL DEFAULT 5.00 COMMENT '课程评分 (0.00-5.00)',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_id` (`course_id`),
    KEY `idx_teacher_rank` (`teacher_id`, `total_enrollments` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程参与度统计快照表';

-- 5. 全平台集中式操作审计只读视图表
CREATE TABLE IF NOT EXISTS `audit_event_read_model` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `audit_id` VARCHAR(64) NOT NULL COMMENT '审计全局唯一ID',
    `source_service` VARCHAR(64) NOT NULL COMMENT '事件来源微服务',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '操作人用户ID/用户名',
    `actor_roles` VARCHAR(128) NULL COMMENT '操作人角色快照',
    `action` VARCHAR(64) NOT NULL COMMENT '动作类型 (如 COURSE_PUBLISH, REFUND)',
    `resource_type` VARCHAR(64) NOT NULL COMMENT '目标资源类型 (如 COURSE, ORDER)',
    `resource_id` VARCHAR(64) NULL COMMENT '目标资源ID',
    `level` VARCHAR(16) NOT NULL DEFAULT 'INFO' COMMENT '日志级别 (INFO, WARN, ERROR)',
    `client_ip` VARCHAR(64) NULL COMMENT '客户端IP',
    `occurred_at` DATETIME(3) NOT NULL COMMENT '事件发生时间 (毫秒级)',
    `payload_json` JSON NULL COMMENT '详细上下文负载快照',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_audit_id` (`audit_id`),
    KEY `idx_occurred_at` (`occurred_at` DESC),
    KEY `idx_service_actor` (`source_service`, `actor_id`),
    KEY `idx_level` (`level`),
    KEY `idx_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集中式审计日志只读模型表';

-- 6. 全量指标重算任务记录表
CREATE TABLE IF NOT EXISTS `analytics_rebuild_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_no` VARCHAR(64) NOT NULL COMMENT '重算任务编号 (唯一)',
    `trigger_by` VARCHAR(64) NOT NULL COMMENT '触发人用户ID/用户名',
    `status` VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING, SUCCESS, FAILED',
    `stage` VARCHAR(32) NOT NULL DEFAULT 'INITIALIZING' COMMENT '当前阶段: USER, COURSE, PAYMENT, COMPLETED',
    `total_items` INT NOT NULL DEFAULT 0 COMMENT '待处理事实总数',
    `processed_items` INT NOT NULL DEFAULT 0 COMMENT '已处理事实数',
    `error_msg` VARCHAR(1024) NULL COMMENT '异常详情',
    `started_at` DATETIME(3) NOT NULL COMMENT '任务开始时间',
    `finished_at` DATETIME(3) NULL COMMENT '任务完成时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rebuild_task_no` (`task_no`),
    KEY `idx_rebuild_started_at` (`started_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标重算调度追踪表';

-- 授权
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_analytics.daily_teacher_metrics TO 'analytics_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_analytics.daily_platform_metrics TO 'analytics_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_analytics.daily_finance_metrics TO 'analytics_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_analytics.course_engagement_stats TO 'analytics_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_analytics.audit_event_read_model TO 'analytics_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_analytics.analytics_rebuild_task TO 'analytics_app'@'%';
