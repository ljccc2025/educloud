-- EduCloud Analytics 数据库：技术表（V000）
-- 依据：docs/superpowers/specs/2026-08-26-educloud-analytics-design.md

CREATE TABLE IF NOT EXISTS schema_migration_history (
  version VARCHAR(32) NOT NULL,
  description VARCHAR(255) NOT NULL,
  script_name VARCHAR(255) NOT NULL,
  checksum_sha256 CHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  installed_by VARCHAR(64) NOT NULL,
  installed_at DATETIME(3) NOT NULL,
  execution_ms BIGINT NOT NULL,
  error_summary VARCHAR(1024) NULL,
  PRIMARY KEY (version),
  UNIQUE KEY uk_schema_migration_script (script_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS analytics_event_inbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  source_service VARCHAR(64) NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_analytics_event_id (event_id),
  KEY idx_analytics_event_type (event_type),
  KEY idx_analytics_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分析中心事件幂等收件箱';

CREATE TABLE IF NOT EXISTS consumer_watermark (
  source_service VARCHAR(64) NOT NULL,
  last_event_id VARCHAR(64) NOT NULL,
  last_occurred_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (source_service)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件消费水位表';

GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_analytics.schema_migration_history TO 'analytics_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_analytics.analytics_event_inbox TO 'analytics_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_analytics.consumer_watermark TO 'analytics_app'@'%';
