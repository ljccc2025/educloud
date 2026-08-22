-- 加宽审计 actor_type：SERVICE_BOOTSTRAP_JOB(19) 等主体类型超出 VARCHAR(16)（M04 联调暴露）
ALTER TABLE audit_event MODIFY COLUMN actor_type VARCHAR(32) NOT NULL;
