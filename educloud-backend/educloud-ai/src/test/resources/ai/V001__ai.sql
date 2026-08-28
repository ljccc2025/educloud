-- 【测试资源副本】本文件是 deploy/sql/ai/V001__ai.sql 的逐字副本（勿在此改动表结构），
-- 仅供 AiChatPersistenceIT 的 Testcontainers withInitScript("ai/V001__ai.sql") 使用：
-- MySQL 官方镜像 entrypoint 先执行 docker_setup_db（创建 educloud_ai 库与 ai_app 用户，
-- 由 withDatabaseName/withUsername/withPassword 指定），再以 root 跑本脚本，
-- 故末尾两条 GRANT（授权给已存在的 ai_app@%）能成功。
-- 源文件变更时请同步覆盖本副本。

-- EduCloud AI 数据库：AI 助教 P1（V001）
-- 依据：docs/superpowers/specs/2026-08-28-ai-assistant-p1-design.md §3

CREATE TABLE IF NOT EXISTS ai_conversation (
    id              BIGINT       NOT NULL COMMENT '雪花 ID，对外字符串化',
    student_id      BIGINT       NOT NULL COMMENT '归属学生，取自 JWT sub',
    title           VARCHAR(120) NOT NULL COMMENT '首条学生提问截断生成',
    message_count   INT          NOT NULL DEFAULT 0 COMMENT '消息数（含软删）',
    last_message_at DATETIME(3)  NOT NULL COMMENT '列表排序依据',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_student_last (student_id, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 会话';

CREATE TABLE IF NOT EXISTS ai_message (
    id                BIGINT      NOT NULL COMMENT '雪花 ID',
    conversation_id   BIGINT      NOT NULL COMMENT '会话',
    role              VARCHAR(16) NOT NULL COMMENT 'user/assistant',
    content           TEXT        NULL COMMENT '答案正文（只存 content，不存 reasoning_content）',
    provider          VARCHAR(64) NULL COMMENT '实际调用的供应商',
    model             VARCHAR(64) NULL COMMENT '实际调用的模型',
    prompt_tokens     INT         NULL,
    completion_tokens INT         NULL,
    latency_ms        INT         NULL COMMENT '外部调用耗时',
    finish_reason     VARCHAR(16) NULL COMMENT 'stop/length/error',
    status            VARCHAR(16) NOT NULL COMMENT 'OK/TRUNCATED/FAILED',
    error_code        VARCHAR(64) NULL COMMENT '失败时的错误码',
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_conversation_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 消息（兼调用审计）';

GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_ai.ai_conversation TO 'ai_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_ai.ai_message TO 'ai_app'@'%';
