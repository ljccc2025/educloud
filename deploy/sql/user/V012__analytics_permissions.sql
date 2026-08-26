-- EduCloud User 数据库：Analytics 权限码（V012）
-- 依据：docs/superpowers/specs/2026-08-26-educloud-analytics-design.md

INSERT INTO sys_permission (id, code, name, resource, action, description)
VALUES
  (171, 'analytics:view', '数据分析查看', 'analytics', 'view', '允许管理员与审计员查看全平台数据指标与大屏'),
  (172, 'analytics:rebuild', '全量指标重算', 'analytics', 'rebuild', '允许管理员触发全量指标平滑重算')
AS new
ON DUPLICATE KEY UPDATE
  name = new.name,
  resource = new.resource,
  action = new.action,
  description = new.description;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES
  (1131, 4, 171),  -- AUDITOR (analytics:view)
  (1132, 6, 171),  -- SYSTEM_ADMIN (analytics:view)
  (1133, 6, 172),  -- SYSTEM_ADMIN (analytics:rebuild)
  (1134, 7, 171),  -- SUPER_ADMIN (analytics:view)
  (1135, 7, 172)   -- SUPER_ADMIN (analytics:rebuild)
AS new
ON DUPLICATE KEY UPDATE
  role_id = new.role_id,
  permission_id = new.permission_id;
