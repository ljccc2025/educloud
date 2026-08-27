-- 教师端最近动态：关联用户姓名与课程标题（跨库只读）
GRANT SELECT ON `educloud_user`.`sys_user` TO 'analytics_app'@'%';
GRANT SELECT ON `educloud_user`.`user_profile` TO 'analytics_app'@'%';
GRANT SELECT ON `educloud_course`.`course` TO 'analytics_app'@'%';
GRANT SELECT ON `educloud_course`.`course_version` TO 'analytics_app'@'%';
