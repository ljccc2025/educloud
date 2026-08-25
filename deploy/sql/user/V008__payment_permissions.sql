-- EduCloud User 数据库：Payment 权限码（V008）
-- 依据：docs/superpowers/specs/2026-08-25-educloud-payment-design.md

INSERT INTO sys_permission (id, code, name, resource, action, description)
VALUES
  (131, 'refund:admin', '退款管理', 'refund', 'admin', '管理端查看全量退款流水与审核原路退款'),
  (132, 'reconciliation:admin', '对账管理', 'reconciliation', 'admin', '日终对账批次触发、差错核对与平账处理')
AS new
ON DUPLICATE KEY UPDATE
  name = new.name,
  resource = new.resource,
  action = new.action,
  description = new.description;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES
  (1081, 5, 131),  -- FINANCE_ADMIN (refund:admin)
  (1082, 5, 132),  -- FINANCE_ADMIN (reconciliation:admin)

  (1083, 6, 131),  -- SYSTEM_ADMIN (refund:admin)
  (1084, 6, 132),  -- SYSTEM_ADMIN (reconciliation:admin)

  (1085, 7, 131),  -- SUPER_ADMIN (refund:admin)
  (1086, 7, 132)   -- SUPER_ADMIN (reconciliation:admin)
AS new
ON DUPLICATE KEY UPDATE
  role_id = new.role_id,
  permission_id = new.permission_id;
