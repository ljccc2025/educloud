-- 角色化动态流表（规格：2026-08-27-activity-feed-certificate-design.md §3.1）
-- source_event 唯一约束保证事件消费幂等（同一事件重复消费不重复写动态）。
CREATE TABLE IF NOT EXISTS activity_feed (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id     VARCHAR(64)  NOT NULL COMMENT '行为主体用户ID',
    actor_role   VARCHAR(16)  NOT NULL COMMENT 'STUDENT / TEACHER',
    action_type  VARCHAR(32)  NOT NULL,
    target_type  VARCHAR(32)  NULL,
    target_id    VARCHAR(64)  NULL,
    target_title VARCHAR(255) NULL,
    extra_json   JSON         NULL,
    source_event VARCHAR(64)  NULL,
    occurred_at  DATETIME(3)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_event (source_event),
    KEY idx_actor_role_time (actor_id, actor_role, occurred_at DESC),
    KEY idx_action_type (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色化动态流';
