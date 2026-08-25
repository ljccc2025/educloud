-- 消息通知中心权限码定义（V010）
-- 依据：docs/superpowers/specs/2026-08-25-educloud-notification-design.md

INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, description, created_at, updated_at)
VALUES
  (151, 'notification:publish', '发布通知/公告', 'API', '允许管理员发布系统全员公告或定向通知', NOW(3), NOW(3)),
  (152, 'notification:channel:view', '查看通知渠道状态', 'API', '允许管理员查看邮件/短信通道运行状态与脱敏配置', NOW(3), NOW(3)),
  (153, 'notification:channel:test', '测试通知渠道发信', 'API', '允许管理员向认证邮箱发送测试邮件', NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
  permission_name = VALUES(permission_name),
  description = VALUES(description),
  updated_at = NOW(3);

-- 绑定 ROLE_ADMIN (role_id = 6)
INSERT INTO sys_role_permission (id, role_id, permission_id, created_at)
VALUES
  (151, 6, 151, NOW(3)),
  (152, 6, 152, NOW(3)),
  (153, 6, 153, NOW(3))
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);
