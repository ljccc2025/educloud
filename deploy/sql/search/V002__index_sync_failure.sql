-- EduCloud Search 数据库：索引同步死信失败记录表（V002）
-- 依据：08-26 代码审查 P3-12（DLQ 无消费者与重放任务 -> 死信堆积无处理路径）
-- 该表由 DLQ 消费者写入，供定时/手动重放任务扫描处理

CREATE TABLE IF NOT EXISTS `index_sync_failure` (
    `id` BIGINT UNSIGNED NOT NULL COMMENT '雪花ID',
    `message_id` VARCHAR(128) NOT NULL COMMENT '消息全局唯一ID',
    `exchange` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '死信发生前所在交换机',
    `routing_key` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '死信发生前所在路由键',
    `payload` JSON NOT NULL COMMENT '死信消息载荷',
    `error` VARCHAR(1024) NULL COMMENT '失败原因',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数（重放失败累加，达到上限转 DEAD）',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING / RESOLVED / DEAD',
    `occurred_at` DATETIME(3) NOT NULL COMMENT '死信产生时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_occurred` (`status`, `occurred_at`),
    KEY `idx_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索索引同步死信失败记录表';

GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_search.index_sync_failure TO 'search_app'@'%';
