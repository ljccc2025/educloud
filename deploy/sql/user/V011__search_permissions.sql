-- EduCloud User 数据库：Search 权限码（V011）
-- 依据：docs/superpowers/specs/2026-08-25-educloud-search-design.md

INSERT INTO sys_permission (id, code, name, resource, action, description)
VALUES
  (161, 'search:rebuild', '索引重建', 'search', 'rebuild', '允许管理员触发 Elasticsearch 物理索引平滑重建与切换'),
  (162, 'search:admin:view', '搜索运维看板查看', 'search', 'view', '允许管理员查看搜索中心索引状态与重建任务历史')
AS new
ON DUPLICATE KEY UPDATE
  name = new.name,
  resource = new.resource,
  action = new.action,
  description = new.description;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES
  (1121, 6, 161),  -- SYSTEM_ADMIN (search:rebuild)
  (1122, 6, 162),  -- SYSTEM_ADMIN (search:admin:view)

  (1123, 7, 161),  -- SUPER_ADMIN (search:rebuild)
  (1124, 7, 162)   -- SUPER_ADMIN (search:admin:view)
AS new
ON DUPLICATE KEY UPDATE
  role_id = new.role_id,
  permission_id = new.permission_id;
