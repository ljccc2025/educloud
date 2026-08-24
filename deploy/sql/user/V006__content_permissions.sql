-- EduCloud User 数据库：Content 权限码（V006）
-- 依据：docs/superpowers/specs/2026-08-24-educloud-content-design.md

INSERT INTO sys_permission (id, code, name, resource, action, description)
VALUES
  (111, 'content:manage', '内容管理', 'content', 'manage', '创建与编辑课程章节和课件内容草稿并提交审核'),
  (112, 'content:audit', '内容审核', 'content', 'audit', '审核课程内容修订草稿并原子发布或驳回'),
  (113, 'content:learn', '课程学习', 'content', 'learn', '学习已选课程内容并上报学习进度')
AS new
ON DUPLICATE KEY UPDATE
  name = new.name,
  resource = new.resource,
  action = new.action,
  description = new.description;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES
  (1033, 2, 111),  -- TEACHER (content:manage)
  (1034, 6, 111),  -- SYSTEM_ADMIN (content:manage)
  (1035, 7, 111),  -- SUPER_ADMIN (content:manage)

  (1036, 5, 112),  -- COURSE_REVIEWER (content:audit)
  (1037, 6, 112),  -- SYSTEM_ADMIN (content:audit)
  (1038, 7, 112),  -- SUPER_ADMIN (content:audit)

  (1039, 1, 113),  -- STUDENT (content:learn)
  (1040, 2, 113),  -- TEACHER (content:learn)
  (1041, 6, 113),  -- SYSTEM_ADMIN (content:learn)
  (1042, 7, 113)   -- SUPER_ADMIN (content:learn)
AS new
ON DUPLICATE KEY UPDATE
  role_id = new.role_id,
  permission_id = new.permission_id;
