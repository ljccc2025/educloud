-- EduCloud Payment 数据库：初始种子数据（V002）
-- 依据：docs/superpowers/specs/2026-08-25-educloud-payment-design.md

-- 预置历史对账已完成批次种子数据
INSERT INTO `reconciliation_batch` (`id`, `batch_no`, `reconcile_date`, `channel_code`, `total_count`, `total_amount_cents`, `diff_count`, `status`, `started_at`, `finished_at`)
VALUES
  (9000000000000000801, 'REC_20260824_MOCK', '2026-08-24', 'MOCK', 10, 199000, 0, 'MATCHED', '2026-08-24 02:00:00.000', '2026-08-24 02:00:05.000')
AS new
ON DUPLICATE KEY UPDATE `status` = new.`status`;
