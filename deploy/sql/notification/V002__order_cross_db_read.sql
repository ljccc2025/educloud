-- EduCloud Notification 数据库：跨库只读授权（V002）
-- 依据：支付成功通知需解析真实课程名（trade_order_item.course_title_snapshot）
-- 部署前提：root 需先预授权迁移账号（一次性）：
--   GRANT SELECT ON educloud_order.trade_order_item TO 'notification_migration'@'%' WITH GRANT OPTION;

GRANT SELECT ON `educloud_order`.`trade_order_item` TO 'notification_app'@'%';
