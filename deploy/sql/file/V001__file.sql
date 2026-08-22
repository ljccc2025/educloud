-- EduCloud File 数据库：上传会话、文件对象、业务绑定与访问审计
-- 依据：docs/superpowers/specs/2026-08-22-educloud-file-design.md 第 5 节「数据模型（educloud_file 逻辑库）」。

-- 上传会话：PUT URL 预签发放/轮换/过期，以及对象正式登记前的中间状态。
CREATE TABLE file_upload_session (
  id BIGINT NOT NULL,
  uploader_id BIGINT NOT NULL,
  object_key VARCHAR(255) NOT NULL,
  bucket VARCHAR(64) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  expected_size_bytes BIGINT NULL,
  status VARCHAR(16) NOT NULL, -- PENDING/COMPLETED/EXPIRED/ABORTED
  put_url_expires_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  version INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_upload_session_object_key (object_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 文件对象：对象存储键唯一；绑定/解绑/删除在事务内先 SELECT ... FOR UPDATE 锁根行并递增 version。
CREATE TABLE file_object (
  id BIGINT NOT NULL,
  object_key VARCHAR(255) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  bucket VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL, -- UPLOADING/AVAILABLE/QUARANTINED/DELETED
  uploader_id BIGINT NOT NULL,
  uploaded_at DATETIME(6) NOT NULL,
  deleted_at DATETIME(6) NULL,
  version INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_object_key (object_key),
  KEY idx_file_object_sha256_status (sha256, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 业务绑定：唯一键 (file_id, owner_service, owner_type, owner_id)；解绑用 unbound_at 软标记，历史绑定可审计。
CREATE TABLE file_binding (
  id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  owner_service VARCHAR(32) NOT NULL,
  owner_type VARCHAR(64) NOT NULL,
  owner_id VARCHAR(128) NOT NULL,
  bound_at DATETIME(6) NOT NULL,
  unbound_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_binding (file_id, owner_service, owner_type, owner_id),
  KEY idx_file_binding_owner (owner_service, owner_type, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 访问审计：敏感/受保护文件访问只追加事实（GRANT_SINGLE、GRANT_BATCH_DENIED、DELETE、DELETE_FORCE、STORAGE_TEST）。
CREATE TABLE file_access_audit (
  id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  action VARCHAR(32) NOT NULL,
  result VARCHAR(16) NOT NULL,
  ip VARCHAR(64) NULL,
  request_id VARCHAR(36) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_file_access_audit_file (file_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 应用账号权限：业务表逐表授权（不授予库级权限）；审计表仅 INSERT/SELECT。
-- file_migration 库级权限（含 GRANT OPTION）由 init 脚本 001-create-databases.sh 授予，本文件不重复。
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_file.file_upload_session TO 'file_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_file.file_object TO 'file_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_file.file_binding TO 'file_app'@'%';
GRANT SELECT, INSERT ON educloud_file.file_access_audit TO 'file_app'@'%';
