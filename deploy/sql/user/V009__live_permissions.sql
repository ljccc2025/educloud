-- EduCloud User 数据库：Live 权限码（V009）
-- 依据：docs/superpowers/specs/2026-08-25-educloud-live-design.md

INSERT INTO sys_permission (id, code, name, resource, action, description)
VALUES
  (141, 'live:create', '直播创建', 'live', 'create', '教师/管理端创建直播间'),
  (142, 'live:manage', '直播管理', 'live', 'manage', '开播、下播、修改直播间配置'),
  (143, 'live:view', '直播查看', 'live', 'view', '查看直播间详情、历史弹幕与录制回放'),
  (144, 'live:join', '直播参与', 'live', 'join', '申请进入直播间与建立 WebSocket 互动长连接'),
  (145, 'live:moderate', '直播管控', 'live', 'moderate', '房间全员禁言与违规弹幕撤回管理')
AS new
ON DUPLICATE KEY UPDATE
  name = new.name,
  resource = new.resource,
  action = new.action,
  description = new.description;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES
  -- STUDENT (1)
  (1091, 1, 143),  -- STUDENT (live:view)
  (1092, 1, 144),  -- STUDENT (live:join)

  -- TEACHER (2)
  (1093, 2, 141),  -- TEACHER (live:create)
  (1094, 2, 142),  -- TEACHER (live:manage)
  (1095, 2, 143),  -- TEACHER (live:view)
  (1096, 2, 144),  -- TEACHER (live:join)
  (1097, 2, 145),  -- TEACHER (live:moderate)

  -- SYSTEM_ADMIN (6)
  (1098, 6, 141),  -- SYSTEM_ADMIN (live:create)
  (1099, 6, 142),  -- SYSTEM_ADMIN (live:manage)
  (1100, 6, 143),  -- SYSTEM_ADMIN (live:view)
  (1101, 6, 144),  -- SYSTEM_ADMIN (live:join)
  (1102, 6, 145),  -- SYSTEM_ADMIN (live:moderate)

  -- SUPER_ADMIN (7)
  (1103, 7, 141),  -- SUPER_ADMIN (live:create)
  (1104, 7, 142),  -- SUPER_ADMIN (live:manage)
  (1105, 7, 143),  -- SUPER_ADMIN (live:view)
  (1106, 7, 144),  -- SUPER_ADMIN (live:join)
  (1107, 7, 145)   -- SUPER_ADMIN (live:moderate)
AS new
ON DUPLICATE KEY UPDATE
  role_id = new.role_id,
  permission_id = new.permission_id;
