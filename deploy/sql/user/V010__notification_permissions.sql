-- EduCloud User 数据库：Notification 权限码（V010）
-- 依据：docs/superpowers/specs/2026-08-25-educloud-notification-design.md

INSERT INTO sys_permission (id, code, name, resource, action, description)
VALUES
  (151, 'notification:publish', '发布通知/公告', 'notification', 'publish', '允许管理员发布系统全员公告或定向通知'),
  (152, 'notification:channel:view', '查看通知渠道状态', 'notification', 'view', '允许管理员查看邮件/短信通道运行状态与脱敏配置'),
  (153, 'notification:channel:test', '测试通知渠道发信', 'notification', 'test', '允许管理员向认证邮箱发送测试邮件')
AS new
ON DUPLICATE KEY UPDATE
  name = new.name,
  resource = new.resource,
  action = new.action,
  description = new.description;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES
  (1111, 6, 151),  -- SYSTEM_ADMIN (notification:publish)
  (1112, 6, 152),  -- SYSTEM_ADMIN (notification:channel:view)
  (1113, 6, 153),  -- SYSTEM_ADMIN (notification:channel:test)

  (1114, 7, 151),  -- SUPER_ADMIN (notification:publish)
  (1115, 7, 152),  -- SUPER_ADMIN (notification:channel:view)
  (1116, 7, 153)   -- SUPER_ADMIN (notification:channel:test)
AS new
ON DUPLICATE KEY UPDATE
  role_id = new.role_id,
  permission_id = new.permission_id;
