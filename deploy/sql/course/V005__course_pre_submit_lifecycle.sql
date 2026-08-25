-- EduCloud Course 数据库：课程根新增「提交前生命周期」列（V005）
-- 依据：代码审查 BUG-052（2026-08-24）——已发布课程迭代新版本被驳回/撤回时，
-- lifecycle_status 被无条件打回 DRAFT：课程从公开目录消失（目录仅查 PUBLISHED），
-- 并进入死锁态（offline 仅允许 PUBLISHED、republish 仅允许 OFFLINE，仅重新提审通过才恢复）。
-- 修复：submitForReview 记录提交前状态（DRAFT/PUBLISHED/OFFLINE），
-- 驳回/撤回恢复原状态——已发布课程迭代被驳回后旧版本继续在售，下架课程保持下架。
-- 存量说明：已处于死锁态（lifecycle_status='DRAFT' 且 published_version_id 非空）的存量
-- 课程无法区分原状态（PUBLISHED/OFFLINE），不做自动改写，由管理员按业务实际手工处理。

ALTER TABLE course
  ADD COLUMN pre_submit_lifecycle_status VARCHAR(32) NULL
    COMMENT '提交审核前的生命周期（驳回/撤回时恢复）；NULL 表示无待恢复状态'
    AFTER lifecycle_status;
