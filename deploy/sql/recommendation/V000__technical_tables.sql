-- EduCloud Recommendation 数据库：技术表（V000）
-- 依据：docs/superpowers/specs/2026-08-27-educloud-recommendation-design.md

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

GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_recommendation.schema_migration_history TO 'recommendation_app'@'%';
