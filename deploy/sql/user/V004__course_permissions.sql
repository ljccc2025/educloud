-- EduCloud User 数据库：课程域权限码与角色挂载（V004）
-- 依据：docs/superpowers/specs/2026-08-23-educloud-course-design.md 第 3 节「RBAC 扩展」。
-- 幂等约定：全部 INSERT ... AS new ON DUPLICATE KEY UPDATE（MySQL 8.0.19+ 行别名语法），
-- 可重复执行（角色按 code、权限按 code、关联按 (role_id, permission_id) 唯一键命中）。
-- id 段：权限 101-109 避开 V001 的 1-9；sys_role_permission 1001+ 避开 V001 的 1-20。
-- COURSE_REVIEWER 内置角色已在 V001 以 id=3 创建（本迁移按 code 幂等 upsert，不改变既有 id）。

INSERT INTO sys_permission (id, code, name, resource, action, description) VALUES
  (101, 'course:create', '创建课程', 'course', 'create', '教师创建课程草稿'),
  (102, 'course:update', '更新课程', 'course', 'update', '编辑自有课程草稿'),
  (103, 'course:submit', '提交审核', 'course', 'submit', '提交课程审核'),
  (104, 'course:audit', '课程审核', 'course', 'audit', '管理端课程审核与审批发布'),
  (105, 'course:offline', '课程下架', 'course', 'offline', '下架已发布课程'),
  (106, 'course:republish', '重新上架', 'course', 'republish', '已下线课程重新上架'),
  (107, 'course:archive', '课程归档', 'course', 'archive', '归档下架课程'),
  (108, 'course:enroll', '课程选课', 'course', 'enroll', '免费课程选课'),
  (109, 'course:student:read', '课程学生读取', 'course', 'student:read', '查看课程学生列表')
AS new
ON DUPLICATE KEY UPDATE
  name = new.name,
  resource = new.resource,
  action = new.action,
  description = new.description;

INSERT INTO sys_role (id, code, name, description, status, built_in, created_at, updated_at) VALUES
  (100, 'COURSE_REVIEWER', '课程审核', '课程审核', 'ACTIVE', 1, NOW(3), NOW(3))
AS new
ON DUPLICATE KEY UPDATE
  name = new.name,
  description = new.description,
  status = new.status,
  built_in = new.built_in,
  updated_at = new.updated_at;

-- 角色-权限挂载（role_id：2=TEACHER、3=COURSE_REVIEWER、6=SYSTEM_ADMIN、7=SUPER_ADMIN，均来自 V001 seed；
-- ADMIN 语义 = SYSTEM_ADMIN + SUPER_ADMIN 两个内置管理角色，各挂载全部 9 项，与 V001 用户域权限一致）。
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
  (1001, 3, 104), -- COURSE_REVIEWER → course:audit
  (1002, 2, 101), (1003, 2, 102), (1004, 2, 103), (1005, 2, 105),
  (1006, 2, 106), (1007, 2, 107), (1008, 2, 108), (1009, 2, 109), -- TEACHER → 8 项
  (1010, 6, 101), (1011, 6, 102), (1012, 6, 103), (1013, 6, 104), (1014, 6, 105),
  (1015, 6, 106), (1016, 6, 107), (1017, 6, 108), (1018, 6, 109), -- SYSTEM_ADMIN → 全部
  (1019, 7, 101), (1020, 7, 102), (1021, 7, 103), (1022, 7, 104), (1023, 7, 105),
  (1024, 7, 106), (1025, 7, 107), (1026, 7, 108), (1027, 7, 109)  -- SUPER_ADMIN → 全部
AS new
ON DUPLICATE KEY UPDATE
  role_id = new.role_id,
  permission_id = new.permission_id;

-- STUDENT → course:enroll（免费选课；规格审查补充——学生 JWT 需携带该权限码，否则选课 403）。
-- TEACHER → enroll 保留不动，作为安全超集。
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
  (1028, 1, 108) -- STUDENT → course:enroll
AS new
ON DUPLICATE KEY UPDATE
  role_id = new.role_id,
  permission_id = new.permission_id;
