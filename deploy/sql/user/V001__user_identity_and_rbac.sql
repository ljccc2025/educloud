-- EduCloud User 数据库：身份与 RBAC
-- 依据：docs/superpowers/specs/2026-08-18-educloud-data-design.md 第 3 节「User 数据库」；
-- 权限码目录依据 2026-08-18-educloud-security-and-permissions.md 第 6 节（User 域权限码）。

-- 账号：登录名/邮箱/手机唯一；token_version 用于在线撤销；version 为 User 聚合乐观锁。
CREATE TABLE sys_user (
  id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  email VARCHAR(128) NULL,
  phone VARCHAR(32) NULL,
  password_hash VARCHAR(255) NOT NULL,
  user_type VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  token_version BIGINT NOT NULL DEFAULT 0,
  email_verified TINYINT(1) NOT NULL DEFAULT 0,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME(3) NULL,
  last_login_at DATETIME(3) NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_email (email),
  UNIQUE KEY uk_user_phone (phone),
  KEY idx_user_type_status (user_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户档案：user_id 唯一并关联本库 sys_user。
CREATE TABLE user_profile (
  id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  avatar_file_id BIGINT NULL,
  bio VARCHAR(500) NULL,
  locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_profile_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
  id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  description VARCHAR(255) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  built_in TINYINT(1) NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_permission (
  id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  resource VARCHAR(128) NOT NULL,
  action VARCHAR(64) NOT NULL,
  description VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
  id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  assigned_by BIGINT NULL,
  assigned_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_permission (
  id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 内置角色 seed（依据安全设计第 5 节 RBAC 角色表）。
INSERT INTO sys_role (id, code, name, description, status, built_in, created_at, updated_at) VALUES
  (1, 'STUDENT', '学生', '学习、购买、提交、考试、通知和社区', 'ACTIVE', 1, NOW(3), NOW(3)),
  (2, 'TEACHER', '教师', '自有课程、内容、直播、作业、考试和学生分析', 'ACTIVE', 1, NOW(3), NOW(3)),
  (3, 'COURSE_REVIEWER', '课程审核', '课程审核', 'ACTIVE', 1, NOW(3), NOW(3)),
  (4, 'CONTENT_REVIEWER', '内容审核', '课件和社区内容审核', 'ACTIVE', 1, NOW(3), NOW(3)),
  (5, 'FINANCE_ADMIN', '财务管理员', '订单、支付、退款和对账', 'ACTIVE', 1, NOW(3), NOW(3)),
  (6, 'SYSTEM_ADMIN', '系统管理员', '用户状态、角色权限、公开平台配置和审计查询', 'ACTIVE', 1, NOW(3), NOW(3)),
  (7, 'SUPER_ADMIN', '超级管理员', '经严格审计的全局管理', 'ACTIVE', 1, NOW(3), NOW(3));

-- 权限目录 seed（User 域；总权限数受 Gateway JWT permissions<=64 约束，见 M03 设计规格第 6 节）。
INSERT INTO sys_permission (id, code, name, resource, action, description) VALUES
  (1, 'user:read', '用户读取', 'user', 'read', '管理端用户分页与详情'),
  (2, 'user:status:update', '用户状态更新', 'user', 'status:update', '锁定、禁用或恢复用户'),
  (3, 'rbac:read', 'RBAC 读取', 'rbac', 'read', '角色与权限目录查询'),
  (4, 'rbac:manage', 'RBAC 维护', 'rbac', 'manage', '角色维护'),
  (5, 'rbac:assign', '角色分配', 'rbac', 'assign', '为用户分配角色'),
  (6, 'platform:config:read', '平台配置读取', 'platform-config', 'read', '平台公开配置读取'),
  (7, 'platform:config:update', '平台配置更新', 'platform-config', 'update', '更新非敏感平台公开配置'),
  (8, 'security:key-status:read', '签名密钥状态读取', 'security', 'key-status:read', '查询 JWT 签名公钥非敏感状态'),
  (9, 'audit:read', '审计读取', 'audit', 'read', '审计查询');

-- 基础角色映射 seed（User 域权限）。
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
  (1, 6, 1), (2, 6, 2), (3, 6, 3), (4, 6, 4), (5, 6, 5), (6, 6, 6), (7, 6, 7), (8, 6, 8), (9, 6, 9),
  (10, 7, 1), (11, 7, 2), (12, 7, 3), (13, 7, 4), (14, 7, 5), (15, 7, 6), (16, 7, 7), (17, 7, 8), (18, 7, 9),
  (19, 5, 6), (20, 5, 9);

-- 应用账号权限：业务表逐表授权（不授予库级权限）。
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_user.sys_user TO 'user_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_user.user_profile TO 'user_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_user.sys_role TO 'user_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_user.sys_permission TO 'user_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_user.sys_user_role TO 'user_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_user.sys_role_permission TO 'user_app'@'%';
