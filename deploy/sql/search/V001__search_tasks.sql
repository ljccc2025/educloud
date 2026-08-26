-- EduCloud Search 数据库：业务与任务表（V001）
-- 依据：docs/superpowers/specs/2026-08-25-educloud-search-design.md

CREATE TABLE IF NOT EXISTS `search_index_task` (
    `id` BIGINT UNSIGNED NOT NULL COMMENT '雪花ID',
    `task_no` VARCHAR(64) NOT NULL COMMENT '任务唯一编号',
    `index_name` VARCHAR(128) NOT NULL COMMENT '目标物理索引名称',
    `alias_name` VARCHAR(128) NOT NULL COMMENT '关联别名',
    `task_type` VARCHAR(32) NOT NULL COMMENT '任务类型: FULL_REBUILD / INCREMENTAL_REPAIR',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING / RUNNING / SUCCESS / FAILED',
    `total_records` INT NOT NULL DEFAULT 0 COMMENT '待处理总记录数',
    `processed_records` INT NOT NULL DEFAULT 0 COMMENT '已成功处理记录数',
    `failed_records` INT NOT NULL DEFAULT 0 COMMENT '失败记录数',
    `error_message` TEXT NULL COMMENT '失败异常原因',
    `started_at` DATETIME(3) NULL COMMENT '开始时间',
    `finished_at` DATETIME(3) NULL COMMENT '完成时间',
    `created_by` VARCHAR(64) NOT NULL COMMENT '触发人',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY `idx_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索索引任务表';

CREATE TABLE IF NOT EXISTS `search_sync_inbox` (
    `id` BIGINT UNSIGNED NOT NULL COMMENT '雪花ID',
    `message_id` VARCHAR(128) NOT NULL COMMENT '消息全局唯一ID',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
    `aggregate_type` VARCHAR(64) NOT NULL COMMENT '聚合根类型',
    `aggregate_id` VARCHAR(64) NOT NULL COMMENT '聚合根ID',
    `aggregate_version` BIGINT NOT NULL COMMENT '聚合根单调递增版本',
    `payload` JSON NOT NULL COMMENT '事件消息载荷',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PROCESSED' COMMENT '处理状态: PROCESSED / FAILED',
    `error_reason` VARCHAR(512) NULL COMMENT '失败原因',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '接收时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    KEY `idx_aggregate` (`aggregate_type`, `aggregate_id`, `aggregate_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索事件消费接收箱';

GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_search.search_index_task TO 'search_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_search.search_sync_inbox TO 'search_app'@'%';
