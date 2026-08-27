-- EduCloud Recommendation 数据库：跨库只读授权（V002）
-- 依据：docs/superpowers/specs/2026-08-27-educloud-recommendation-design.md §4.1
-- 应用账号仅授予 educloud_course / educloud_file 只读 SELECT（逐表授权，与仓库权限模型一致）

GRANT SELECT ON `educloud_course`.`course` TO 'recommendation_app'@'%';
GRANT SELECT ON `educloud_course`.`course_version` TO 'recommendation_app'@'%';
GRANT SELECT ON `educloud_course`.`course_category` TO 'recommendation_app'@'%';
GRANT SELECT ON `educloud_course`.`course_enrollment` TO 'recommendation_app'@'%';
GRANT SELECT ON `educloud_file`.`file_object` TO 'recommendation_app'@'%';
