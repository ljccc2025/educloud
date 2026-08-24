-- EduCloud Order 数据库：初始种子数据（V002）
-- 依据：docs/superpowers/specs/2026-08-24-educloud-order-design.md

-- 1. 购物车初始数据（学员 2091648316809035778）
INSERT INTO cart_item (id, student_id, course_id, selected, created_at, updated_at)
VALUES
  (9000000000000000701, 2091648316809035778, 9000000000000000112, 1, '2026-08-24 09:00:00.000', '2026-08-24 09:00:00.000'),
  (9000000000000000702, 2091648316809035778, 9000000000000000113, 0, '2026-08-24 09:30:00.000', '2026-08-24 09:30:00.000')
AS new
ON DUPLICATE KEY UPDATE
  selected = new.selected,
  updated_at = new.updated_at;

-- 2. 交易订单初始数据
INSERT INTO trade_order
  (id, order_no, student_id, status, original_amount, payable_amount, currency,
   expires_at, paid_at, cancelled_at, idempotency_key_hash, version, created_at, updated_at)
VALUES
  (9000000000000000801, 'ORD202608240001', 2091648316809035778, 'PAID', 199.00, 199.00, 'CNY',
   '2026-08-24 10:15:00.000', '2026-08-24 10:05:00.000', NULL, NULL, 1, '2026-08-24 10:00:00.000', '2026-08-24 10:05:00.000'),
  (9000000000000000802, 'ORD202608240002', 2091648316809035778, 'PENDING_PAYMENT', 99.00, 99.00, 'CNY',
   '2026-08-24 23:59:59.000', NULL, NULL, NULL, 0, '2026-08-24 12:00:00.000', '2026-08-24 12:00:00.000')
AS new
ON DUPLICATE KEY UPDATE
  status = new.status,
  payable_amount = new.payable_amount,
  paid_at = new.paid_at,
  cancelled_at = new.cancelled_at,
  version = new.version,
  updated_at = new.updated_at;

-- 3. 订单明细项初始数据
INSERT INTO trade_order_item
  (id, order_id, course_id, course_title_snapshot, cover_file_id_snapshot, unit_price, quantity,
   line_amount, refund_reserved_amount, refunded_amount, fulfillment_status, created_at, updated_at)
VALUES
  (9000000000000000811, 9000000000000000801, 9000000000000000110, 'Spring Cloud 微服务架构实战', NULL, 199.00, 1,
   199.00, 0.00, 0.00, 'FULFILLED', '2026-08-24 10:00:00.000', '2026-08-24 10:05:00.000'),
  (9000000000000000812, 9000000000000000802, 9000000000000000111, 'React 18 全栈进阶', NULL, 99.00, 1,
   99.00, 0.00, 0.00, 'UNFULFILLED', '2026-08-24 12:00:00.000', '2026-08-24 12:00:00.000')
AS new
ON DUPLICATE KEY UPDATE
  course_title_snapshot = new.course_title_snapshot,
  unit_price = new.unit_price,
  line_amount = new.line_amount,
  fulfillment_status = new.fulfillment_status,
  updated_at = new.updated_at;
