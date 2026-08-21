-- EduCloud User 数据库：会话、服务客户端、平台公开配置与登录审计
-- 依据：docs/superpowers/specs/2026-08-18-educloud-data-design.md 第 3 节「User 数据库」；
-- 会话轮换语义依据 2026-08-18-educloud-security-and-permissions.md 第 3.2 节。

-- Refresh 会话：每个 Refresh Token 一行，只保存 Token 哈希；family_id 对应 Access Token 的 sid。
CREATE TABLE refresh_session (
  id BIGINT NOT NULL,
  family_id VARCHAR(64) NOT NULL,
  token_id VARCHAR(64) NOT NULL,
  parent_token_id VARCHAR(64) NULL,
  replaced_by_token_id VARCHAR(64) NULL,
  user_id BIGINT NOT NULL,
  session_token_hash CHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  client_type VARCHAR(32) NOT NULL,
  client_fingerprint_hash CHAR(64) NOT NULL,
  issued_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  expires_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3) NULL,
  revoke_reason VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_token_id (token_id),
  UNIQUE KEY uk_refresh_token_hash (session_token_hash),
  KEY idx_refresh_family_status (family_id, status),
  KEY idx_refresh_user_expires (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 服务客户端：client_id 唯一；token_version 递增可立即撤销该客户端已签发的全部服务 Token。
CREATE TABLE service_client (
  id BIGINT NOT NULL,
  client_id VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  allowed_audiences_json JSON NOT NULL,
  allowed_scopes_json JSON NOT NULL,
  token_version BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_service_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 服务客户端凭据：secret 只存哈希；同一 client 最多一个 ACTIVE 与一个 GRACE（由应用与定时任务保证）。
CREATE TABLE service_client_credential (
  id BIGINT NOT NULL,
  service_client_id BIGINT NOT NULL,
  credential_version INT NOT NULL,
  secret_hash CHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  not_before DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NULL,
  revoked_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_service_credential_version (service_client_id, credential_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 平台公开配置：只允许站点名称、Logo、备案号等非敏感配置。
CREATE TABLE platform_public_config (
  id BIGINT NOT NULL,
  config_key VARCHAR(64) NOT NULL,
  config_value VARCHAR(1024) NOT NULL,
  value_type VARCHAR(16) NOT NULL,
  description VARCHAR(255) NULL,
  version INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_platform_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 登录审计：只追加；登录名脱敏存储。
CREATE TABLE login_audit (
  id BIGINT NOT NULL,
  user_id BIGINT NULL,
  login_name_masked VARCHAR(128) NOT NULL,
  result VARCHAR(16) NOT NULL,
  failure_code VARCHAR(64) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(512) NULL,
  request_id VARCHAR(36) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_login_audit_user_time (user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 平台公开配置 seed（非敏感示例值）。
INSERT INTO platform_public_config (id, config_key, config_value, value_type, description, version, created_at, updated_at) VALUES
  (1, 'site_name', 'EduCloud', 'STRING', '站点名称', 0, NOW(3), NOW(3)),
  (2, 'site_logo_url', '', 'STRING', '站点 Logo（短期展示地址或相对路径）', 0, NOW(3), NOW(3)),
  (3, 'icp_record', '', 'STRING', 'ICP 备案号', 0, NOW(3), NOW(3));

-- 应用账号权限。
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_user.refresh_session TO 'user_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_user.service_client TO 'user_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_user.service_client_credential TO 'user_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_user.platform_public_config TO 'user_app'@'%';
GRANT SELECT, INSERT ON educloud_user.login_audit TO 'user_app'@'%';
