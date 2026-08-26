-- EduCloud Search 数据库：技术表（V000）
-- 依据：docs/superpowers/specs/2026-08-18-educloud-data-design.md 与 2026-08-25-educloud-search-design.md

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

CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGINT NOT NULL,
  event_id VARCHAR(36) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  event_version INT NOT NULL,
  aggregate_version BIGINT NOT NULL,
  payload_json JSON NOT NULL,
  request_id VARCHAR(36) NOT NULL,
  trace_id VARCHAR(64) NULL,
  occurred_at DATETIME(3) NOT NULL,
  source_sequence BIGINT NOT NULL,
  publish_status VARCHAR(16) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NULL,
  published_at DATETIME(3) NULL,
  archived_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_outbox_event_id (event_id),
  UNIQUE KEY uk_outbox_source_sequence (source_sequence),
  KEY idx_outbox_pending (publish_status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS outbox_sequence (
  source_name VARCHAR(64) NOT NULL,
  `last_value` BIGINT NOT NULL,
  PRIMARY KEY (source_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO outbox_sequence (source_name, `last_value`) VALUES ('educloud-search', 0)
ON DUPLICATE KEY UPDATE `last_value` = `last_value`;

CREATE TABLE IF NOT EXISTS inbox_event (
  id BIGINT NOT NULL,
  event_id VARCHAR(36) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  source_service VARCHAR(64) NOT NULL,
  event_version INT NOT NULL,
  source_sequence BIGINT NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  process_status VARCHAR(16) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  first_received_at DATETIME(3) NOT NULL,
  last_attempt_at DATETIME(3) NULL,
  processed_at DATETIME(3) NULL,
  error_summary VARCHAR(1024) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inbox_event_id (event_id),
  UNIQUE KEY uk_inbox_source_seq (source_service, source_sequence),
  KEY idx_inbox_pending (process_status, last_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_event (
  id BIGINT NOT NULL,
  audit_id VARCHAR(36) NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  actor_id VARCHAR(64) NOT NULL,
  actor_roles_json JSON NULL,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64) NULL,
  result VARCHAR(16) NOT NULL,
  reason VARCHAR(512) NULL,
  before_summary_json JSON NULL,
  after_summary_json JSON NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(512) NULL,
  request_id VARCHAR(36) NOT NULL,
  trace_id VARCHAR(64) NULL,
  occurred_at DATETIME(3) NOT NULL,
  retention_class VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_audit_audit_id (audit_id),
  KEY idx_audit_occurred_at (occurred_at),
  KEY idx_audit_actor (actor_type, actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS idempotency_record (
  id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  operation VARCHAR(64) NOT NULL,
  idempotency_key_hash CHAR(64) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  response_status INT NOT NULL,
  response_body_json JSON NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_idempotency (user_id, operation, idempotency_key_hash),
  KEY idx_idempotency_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_search.outbox_event TO 'search_app'@'%';
GRANT SELECT, UPDATE ON educloud_search.outbox_sequence TO 'search_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_search.inbox_event TO 'search_app'@'%';
GRANT SELECT, INSERT ON educloud_search.audit_event TO 'search_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_search.idempotency_record TO 'search_app'@'%';
