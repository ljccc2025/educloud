-- EduCloud Order 数据库：业务表（V001）
-- 依据：docs/superpowers/specs/2026-08-24-educloud-order-design.md

-- 1. 购物车表
CREATE TABLE IF NOT EXISTS cart_item (
  id BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  student_id BIGINT NOT NULL COMMENT '学员ID',
  course_id BIGINT NOT NULL COMMENT '课程ID',
  selected TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否勾选结算: 1-是, 0-否',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_course (student_id, course_id),
  KEY idx_student_selected (student_id, selected)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员购物车项表';

-- 2. 交易订单主表
CREATE TABLE IF NOT EXISTS trade_order (
  id BIGINT NOT NULL COMMENT '订单ID（雪花算法）',
  order_no VARCHAR(64) NOT NULL COMMENT '业务订单流水号（ORD+日期+雪花）',
  student_id BIGINT NOT NULL COMMENT '下单学员ID',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '状态: PENDING_PAYMENT, PAID, CANCELLED, REFUNDED',
  original_amount DECIMAL(10, 2) NOT NULL COMMENT '订单原价（元）',
  payable_amount DECIMAL(10, 2) NOT NULL COMMENT '应付金额（元）',
  currency VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  expires_at DATETIME(3) NOT NULL COMMENT '支付截止时间（默认下单+15分钟）',
  paid_at DATETIME(3) DEFAULT NULL COMMENT '支付完成时间',
  cancelled_at DATETIME(3) DEFAULT NULL COMMENT '订单取消/关闭时间',
  idempotency_key_hash VARCHAR(64) DEFAULT NULL COMMENT '幂等防重Key哈希',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_student_idempotency (student_id, idempotency_key_hash),
  KEY idx_student_status (student_id, status),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易订单主表';

-- 3. 订单明细项表
CREATE TABLE IF NOT EXISTS trade_order_item (
  id BIGINT NOT NULL COMMENT '明细ID（雪花算法）',
  order_id BIGINT NOT NULL COMMENT '关联订单ID',
  course_id BIGINT NOT NULL COMMENT '购买课程ID',
  course_title_snapshot VARCHAR(255) NOT NULL COMMENT '课程标题快照',
  cover_file_id_snapshot BIGINT DEFAULT NULL COMMENT '封面文件ID快照',
  unit_price DECIMAL(10, 2) NOT NULL COMMENT '成交单价（元）',
  quantity INT NOT NULL DEFAULT 1 COMMENT '数量（课程固定为1）',
  line_amount DECIMAL(10, 2) NOT NULL COMMENT '明细行总额（元）',
  refund_reserved_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '退款预留中金额',
  refunded_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '已退款金额',
  fulfillment_status VARCHAR(32) NOT NULL DEFAULT 'UNFULFILLED' COMMENT '履约状态: UNFULFILLED, FULFILLED, REVOKED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_course (order_id, course_id),
  KEY idx_order_id (order_id),
  KEY idx_course_id (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细项表';

-- 4. 退款申请主表
CREATE TABLE IF NOT EXISTS refund_request (
  id BIGINT NOT NULL COMMENT '退款申请ID（雪花算法）',
  refund_no VARCHAR(64) NOT NULL COMMENT '业务退款流水号（RFD+日期+雪花）',
  order_id BIGINT NOT NULL COMMENT '关联订单ID',
  student_id BIGINT NOT NULL COMMENT '申请学员ID',
  requested_amount DECIMAL(10, 2) NOT NULL COMMENT '申请退款总额（元）',
  reason VARCHAR(512) NOT NULL COMMENT '退款原因',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '状态: PENDING_REVIEW, APPROVED, REJECTED, SUCCESS',
  reviewed_by BIGINT DEFAULT NULL COMMENT '审核人ID',
  review_reason VARCHAR(512) DEFAULT NULL COMMENT '审核理由/驳回原因',
  reviewed_at DATETIME(3) DEFAULT NULL COMMENT '审核时间',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_order_id (order_id),
  KEY idx_student_id (student_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款申请主表';

-- 5. 退款申请明细项表
CREATE TABLE IF NOT EXISTS refund_request_item (
  id BIGINT NOT NULL COMMENT '退款明细ID（雪花算法）',
  refund_request_id BIGINT NOT NULL COMMENT '关联退款申请ID',
  order_item_id BIGINT NOT NULL COMMENT '关联订单明细ID',
  course_id BIGINT NOT NULL COMMENT '课程ID',
  requested_amount DECIMAL(10, 2) NOT NULL COMMENT '申请退款金额（元）',
  approved_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '批准退款金额（元）',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_item (refund_request_id, order_item_id),
  KEY idx_order_item_id (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款申请明细项表';

-- 权限授权
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_order.cart_item TO 'order_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_order.trade_order TO 'order_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_order.trade_order_item TO 'order_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_order.refund_request TO 'order_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_order.refund_request_item TO 'order_app'@'%';
