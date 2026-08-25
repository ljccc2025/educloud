-- EduCloud Payment 数据库：业务表（V001）
-- 依据：docs/superpowers/specs/2026-08-25-educloud-payment-design.md

CREATE TABLE IF NOT EXISTS `payment_order` (
    `id` BIGINT NOT NULL COMMENT '支付单号(雪花ID)',
    `order_id` BIGINT NOT NULL COMMENT '业务订单ID(关联educloud-order)',
    `user_id` BIGINT NOT NULL COMMENT '付款学员ID',
    `amount_cents` BIGINT NOT NULL COMMENT '应付金额(单位:分)',
    `currency` VARCHAR(16) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `channel_code` VARCHAR(32) NOT NULL COMMENT '支付渠道(MOCK, ALIPAY, WECHAT)',
    `trade_type` VARCHAR(32) NOT NULL DEFAULT 'NATIVE' COMMENT '交易类型(NATIVE, PAGE, APP)',
    `status` VARCHAR(32) NOT NULL DEFAULT 'INITIATED' COMMENT '状态(INITIATED, PAYING, SUCCESS, FAILED, CLOSED)',
    `channel_trade_no` VARCHAR(128) NULL COMMENT '外部第三方渠道交易流水号',
    `pay_url` VARCHAR(1024) NULL COMMENT '收银台跳转URL或表单',
    `qr_code` TEXT NULL COMMENT '扫码支付二维码内容或Base64',
    `expires_at` DATETIME(3) NOT NULL COMMENT '支付有效截止时间',
    `paid_at` DATETIME(3) NULL COMMENT '支付成功时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_channel_trade_no` (`channel_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付主单表';

CREATE TABLE IF NOT EXISTS `payment_transaction` (
    `id` BIGINT NOT NULL COMMENT '流水ID(雪花ID)',
    `payment_order_id` BIGINT NOT NULL COMMENT '支付单ID',
    `transaction_no` VARCHAR(64) NOT NULL COMMENT '商户交易流水号',
    `channel_code` VARCHAR(32) NOT NULL COMMENT '支付渠道',
    `action_type` VARCHAR(32) NOT NULL DEFAULT 'PAY' COMMENT '动作类型(PAY, QUERY, CLOSE, REFUND)',
    `amount_cents` BIGINT NOT NULL COMMENT '涉及金额(分)',
    `fee_cents` BIGINT NOT NULL DEFAULT 0 COMMENT '渠道手续费(分)',
    `raw_request` MEDIUMTEXT NULL COMMENT '向渠道发送的原始请求报文',
    `raw_response` MEDIUMTEXT NULL COMMENT '渠道返回的原始响应报文',
    `status` VARCHAR(32) NOT NULL COMMENT '通信状态(SUCCESS, FAILED, UNKNOWN)',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_payment_order_id` (`payment_order_id`),
    KEY `idx_transaction_no` (`transaction_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付通信流水表';

CREATE TABLE IF NOT EXISTS `payment_callback_log` (
    `id` BIGINT NOT NULL COMMENT '日志ID(雪花ID)',
    `channel_code` VARCHAR(32) NOT NULL COMMENT '支付渠道',
    `notify_id` VARCHAR(128) NOT NULL COMMENT '渠道通知ID或商户单号',
    `request_hash` VARCHAR(64) NOT NULL COMMENT '请求内容SHA256哈希',
    `raw_payload` MEDIUMTEXT NOT NULL COMMENT '原始回调完整请求体',
    `verify_result` VARCHAR(32) NOT NULL COMMENT '验签结果(PASSED, FAILED)',
    `processed_status` VARCHAR(32) NOT NULL COMMENT '处理状态(PROCESSED, DUPLICATED, IGNORED, ERROR)',
    `error_msg` VARCHAR(512) NULL COMMENT '异常错误信息',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_notify` (`channel_code`, `notify_id`),
    KEY `idx_request_hash` (`request_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回调防重与审计表';

CREATE TABLE IF NOT EXISTS `payment_refund` (
    `id` BIGINT NOT NULL COMMENT '退款单ID(雪花ID)',
    `payment_order_id` BIGINT NOT NULL COMMENT '原支付单ID',
    `order_id` BIGINT NOT NULL COMMENT '关联业务订单ID',
    `refund_request_id` BIGINT NULL COMMENT '关联order模块refund_request_id',
    `refund_amount_cents` BIGINT NOT NULL COMMENT '退款金额(分)',
    `currency` VARCHAR(16) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `reason` VARCHAR(256) NOT NULL COMMENT '退款原因',
    `channel_code` VARCHAR(32) NOT NULL COMMENT '原支付渠道',
    `channel_refund_no` VARCHAR(128) NULL COMMENT '渠道退款流水号',
    `status` VARCHAR(32) NOT NULL DEFAULT 'APPLIED' COMMENT '状态(APPLIED, PROCESSING, SUCCESS, FAILED, REJECTED)',
    `audited_by` BIGINT NULL COMMENT '审核人ID',
    `audited_at` DATETIME(3) NULL COMMENT '审核时间',
    `audit_remark` VARCHAR(512) NULL COMMENT '审核备注',
    `refunded_at` DATETIME(3) NULL COMMENT '退款到账时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_payment_order_id` (`payment_order_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付退款单表';

CREATE TABLE IF NOT EXISTS `reconciliation_batch` (
    `id` BIGINT NOT NULL COMMENT '批次ID(雪花ID)',
    `batch_no` VARCHAR(64) NOT NULL COMMENT '对账批次号(如 REC_20260825_MOCK)',
    `reconcile_date` DATE NOT NULL COMMENT '对账账单日期',
    `channel_code` VARCHAR(32) NOT NULL COMMENT '对账渠道',
    `total_count` INT NOT NULL DEFAULT 0 COMMENT '比对交易总笔数',
    `total_amount_cents` BIGINT NOT NULL DEFAULT 0 COMMENT '比对交易总金额(分)',
    `diff_count` INT NOT NULL DEFAULT 0 COMMENT '差异差错笔数',
    `status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '批次状态(RUNNING, MATCHED, DIFF_FOUND, RESOLVED)',
    `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `finished_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date_channel` (`reconcile_date`, `channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日终对账批次表';

CREATE TABLE IF NOT EXISTS `reconciliation_diff` (
    `id` BIGINT NOT NULL COMMENT '差错单ID(雪花ID)',
    `batch_id` BIGINT NOT NULL COMMENT '关联对账批次ID',
    `diff_type` VARCHAR(32) NOT NULL COMMENT '差错类型(LOCAL_MORE, CHANNEL_MORE, AMOUNT_MISMATCH, STATUS_MISMATCH)',
    `payment_order_id` BIGINT NULL COMMENT '本地支付单ID',
    `channel_trade_no` VARCHAR(128) NULL COMMENT '渠道交易号',
    `local_amount_cents` BIGINT NULL COMMENT '本地金额(分)',
    `channel_amount_cents` BIGINT NULL COMMENT '渠道金额(分)',
    `local_status` VARCHAR(32) NULL COMMENT '本地状态',
    `channel_status` VARCHAR(32) NULL COMMENT '渠道状态',
    `resolve_status` VARCHAR(32) NOT NULL DEFAULT 'UNRESOLVED' COMMENT '平账状态(UNRESOLVED, RESOLVED, IGNORED)',
    `resolve_action` VARCHAR(32) NULL COMMENT '处理动作(MANUAL_REPAIR, REFUND_OFFLINE, ADJUST_AMOUNT, MANUAL_SYNC, IGNORE)',
    `resolve_remark` VARCHAR(512) NULL COMMENT '平账处理备注',
    `resolved_by` BIGINT NULL COMMENT '平账操作人ID',
    `resolved_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_batch_id` (`batch_id`),
    KEY `idx_resolve_status` (`resolve_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账差错单表';

CREATE TABLE IF NOT EXISTS `payment_outbox_event` (
    `id` BIGINT NOT NULL COMMENT '事件ID(雪花ID)',
    `aggregate_type` VARCHAR(32) NOT NULL COMMENT '聚合根类型(PAYMENT, REFUND)',
    `aggregate_id` BIGINT NOT NULL COMMENT '聚合根ID',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型(PaymentSucceededEvent, PaymentRefundedEvent)',
    `payload` JSON NOT NULL COMMENT '事件序列化JSON内容',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING, SENDING, PUBLISHED, FAILED)',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次重试时间',
    `published_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付中心事务发件箱';
