-- EduCloud User 数据库：Order 权限码（V007）
-- 依据：docs/superpowers/specs/2026-08-24-educloud-order-design.md

INSERT INTO sys_permission (id, code, name, resource, action, description)
VALUES
  (121, 'order:create', '创建订单', 'order', 'create', '提交结算并生成待支付交易单'),
  (122, 'order:view', '查看订单', 'order', 'view', '查询个人订单详情与列表'),
  (123, 'order:cancel', '取消订单', 'order', 'cancel', '主动取消待支付订单'),
  (124, 'order:refund', '申请退款', 'order', 'refund', '对已支付课程提交退款申请'),
  (125, 'order:admin', '订单管理', 'order', 'admin', '后台查看全量订单流水与审核退款')
AS new
ON DUPLICATE KEY UPDATE
  name = new.name,
  resource = new.resource,
  action = new.action,
  description = new.description;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES
  (1051, 1, 121),  -- STUDENT (order:create)
  (1052, 1, 122),  -- STUDENT (order:view)
  (1053, 1, 123),  -- STUDENT (order:cancel)
  (1054, 1, 124),  -- STUDENT (order:refund)

  (1055, 2, 121),  -- TEACHER (order:create)
  (1056, 2, 122),  -- TEACHER (order:view)
  (1057, 2, 123),  -- TEACHER (order:cancel)
  (1058, 2, 124),  -- TEACHER (order:refund)

  (1059, 5, 121),  -- FINANCE_ADMIN (order:create)
  (1060, 5, 122),  -- FINANCE_ADMIN (order:view)
  (1061, 5, 123),  -- FINANCE_ADMIN (order:cancel)
  (1062, 5, 124),  -- FINANCE_ADMIN (order:refund)
  (1063, 5, 125),  -- FINANCE_ADMIN (order:admin)

  (1064, 6, 121),  -- SYSTEM_ADMIN (order:create)
  (1065, 6, 122),  -- SYSTEM_ADMIN (order:view)
  (1066, 6, 123),  -- SYSTEM_ADMIN (order:cancel)
  (1067, 6, 124),  -- SYSTEM_ADMIN (order:refund)
  (1068, 6, 125),  -- SYSTEM_ADMIN (order:admin)

  (1069, 7, 121),  -- SUPER_ADMIN (order:create)
  (1070, 7, 122),  -- SUPER_ADMIN (order:view)
  (1071, 7, 123),  -- SUPER_ADMIN (order:cancel)
  (1072, 7, 124),  -- SUPER_ADMIN (order:refund)
  (1073, 7, 125)   -- SUPER_ADMIN (order:admin)
AS new
ON DUPLICATE KEY UPDATE
  role_id = new.role_id,
  permission_id = new.permission_id;
