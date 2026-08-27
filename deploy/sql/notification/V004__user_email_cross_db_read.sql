-- EduCloud Notification 数据库：跨库只读授权（V004）
-- 依据：邮件投递任务需使用用户真实邮箱（educloud_user.sys_user.email，
--       替代硬编码虚构地址 user_{id}@educloud.cn）。
-- 部署前提：root 需先预授权迁移账号（一次性）：
--   GRANT SELECT ON educloud_user.sys_user TO 'notification_migration'@'%' WITH GRANT OPTION;

GRANT SELECT ON `educloud_user`.`sys_user` TO 'notification_app'@'%';
