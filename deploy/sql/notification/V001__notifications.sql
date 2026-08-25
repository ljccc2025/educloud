-- EduCloud Notification 数据库：业务表（V001）
-- 依据：docs/superpowers/specs/2026-08-25-educloud-notification-design.md

CREATE TABLE IF NOT EXISTS sys_notification (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花算法通知主键 ID',
    title VARCHAR(255) NOT NULL COMMENT '通知标题',
    content TEXT NOT NULL COMMENT '通知正文内容',
    kind VARCHAR(32) NOT NULL COMMENT '通知分类: SYSTEM/COURSE/LIVE/ASSIGNMENT/EXAM/PAYMENT',
    target_type VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '受众类型: USER/ALL/ROLE',
    sender_id BIGINT NULL COMMENT '发信人 ID (系统自动触发为 0 或 NULL)',
    action_label VARCHAR(64) NULL COMMENT '前端交互操作文案，如：进入直播/查看作业',
    action_path VARCHAR(255) NULL COMMENT '前端路由跳转路径，如：/live/1 或 /assignments',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_kind_created (kind, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知主体元数据表';

CREATE TABLE IF NOT EXISTS sys_user_notification (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '收件箱记录 ID',
    user_id BIGINT NOT NULL COMMENT '接收人用户 ID',
    notification_id BIGINT NOT NULL COMMENT '关联通知 ID (sys_notification.id)',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读: 0-未读, 1-已读',
    read_at DATETIME(3) NULL COMMENT '读取时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-已删除',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_notification (user_id, notification_id),
    INDEX idx_user_unread (user_id, is_deleted, is_read, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收件箱与已读隔离表';

CREATE TABLE IF NOT EXISTS sys_delivery_task (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '投递任务 ID',
    notification_id BIGINT NOT NULL COMMENT '关联通知 ID',
    user_id BIGINT NOT NULL COMMENT '接收人用户 ID',
    channel_code VARCHAR(32) NOT NULL DEFAULT 'EMAIL' COMMENT '渠道: EMAIL/SMS',
    receiver_target VARCHAR(255) NOT NULL COMMENT '接收目标 (脱敏邮箱或手机号)',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUCCESS/FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retries INT NOT NULL DEFAULT 3 COMMENT '最大允许重试次数',
    next_retry_at DATETIME(3) NULL COMMENT '下次重试时间 (指数退避)',
    last_error_message VARCHAR(500) NULL COMMENT '最后一次失败原因 (脱敏)',
    sent_at DATETIME(3) NULL COMMENT '实际投递成功时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部渠道异步投递任务表';
