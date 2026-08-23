-- EduCloud User 数据库：补种 file:upload 权限（V005）
-- 依据：M04 规格（上传会话需 file:upload 权限码）+ VM 重装后权限丢失的回归修复（2026-08-24）。
-- 约定：幂等 INSERT ... AS new ON DUPLICATE KEY UPDATE（按 code / (role_id, permission_id) 唯一键）。
-- 权限码：id 110（避开 V001 1-9 与 V004 101-109）；挂载 id 1029-1032（避开 V001 1-20 与 V004 1001-1028）。

INSERT INTO sys_permission (id, code, name, resource, action, description)
VALUES (110, 'file:upload', '文件上传', 'file', 'upload', '创建上传会话并上传文件（头像/课程封面等）')
AS new
ON DUPLICATE KEY UPDATE
  name = new.name,
  resource = new.resource,
  action = new.action,
  description = new.description;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES
  (1029, 1, 110),  -- STUDENT（头像上传）
  (1030, 2, 110),  -- TEACHER（课程封面上传）
  (1031, 6, 110),  -- SYSTEM_ADMIN
  (1032, 7, 110)   -- SUPER_ADMIN
AS new
ON DUPLICATE KEY UPDATE
  role_id = new.role_id,
  permission_id = new.permission_id;
