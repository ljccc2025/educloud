-- EduCloud Content 数据库：Outbox CAS 认领支持（V003）
-- 依据：角色化动态流阶段 2 —— content 模块补建 Outbox 投递器（参照 course 模块
--   V006 的 CAS 认领模式），防止多实例部署重复投递。
--   updated_at：状态变更时间戳（认领/发布/失败回置自动刷新），供 releaseStaleClaims
--               判定实例崩溃后的过期认领（occurred_at 为事件发生时间，不能用于认领时效）。
--   claim_owner：认领实例标识（JVM 生命周期唯一）；认领后仅归属实例可取回并投递，
--               避免其他实例把在途 CLAIMED 批次重复投递；终态写入时清空。

ALTER TABLE outbox_event
  ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  ADD COLUMN claim_owner VARCHAR(64) NULL;
