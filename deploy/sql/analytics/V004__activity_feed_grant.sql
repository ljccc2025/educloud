-- 角色化动态流表权限（规格：2026-08-27-activity-feed-certificate-design.md §3.1）
-- V003 建表时遗漏授权，此处补充给 analytics_app 应用账号授权。
GRANT SELECT, INSERT, UPDATE, DELETE ON `educloud_analytics`.`activity_feed` TO 'analytics_app'@'%';
