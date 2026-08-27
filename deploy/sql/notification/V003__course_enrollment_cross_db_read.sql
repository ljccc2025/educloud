-- EduCloud Notification 数据库：跨库只读授权（V003）
-- 依据：直播开播通知需解析真实报名学生（educloud_course.course_enrollment.student_id，
--       替代硬编码演示用户 2091648316809035778L）；live 库无预报名表，报名事实在 course 库。
-- 部署前提：root 需先预授权迁移账号（一次性）：
--   GRANT SELECT ON educloud_course.course_enrollment TO 'notification_migration'@'%' WITH GRANT OPTION;

GRANT SELECT ON `educloud_course`.`course_enrollment` TO 'notification_app'@'%';
