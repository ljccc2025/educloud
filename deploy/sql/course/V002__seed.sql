-- EduCloud Course 数据库：演示种子数据（V002）
-- 依据：docs/superpowers/specs/2026-08-23-educloud-course-design.md 第 5.3 节。
-- 约定：
--   1. 固定可读 Snowflake 区间 ID 9000000000000000100+（分类 100-108 / 课程 110-117 /
--      版本 120-127 / 归属 130-137 / 提交 140 / 选课 150-152 / 评价 160-162），
--      避免与运行时 ID 冲突；
--   2. 幂等：INSERT ... ON DUPLICATE KEY UPDATE（按各表唯一键：slug / id /
--      (course_id, version_no) / (course_id, teacher_id) / course_version_id /
--      (course_id, student_id)），重放不报错、数据不变；
--   3. 指针语义与 CourseAuditService 一致：审批通过后 published_version_id 指向
--      PUBLISHED 版本且 draft_version_id 清空；草稿/待审课程的 draft_version_id
--      指向当前工作版本；
--   4. 汇总一致性：course.enrollment_count / rating_avg / rating_count 与
--      course_enrollment / course_review 明细吻合；cover_file_id 全 NULL（前端兜底图，
--      VM 演示由教师端真实上传封面）。
-- 演示账号（user 库 seed，见 deploy/scripts/seed-demo-users.sh 与注册流程）：
--   demo_teacher = 9000000000000000001（TEACHER）、demo_admin = 9000000000000000002（ADMIN）、
--   fe_demo_10 学生 = 2091029641632157697（注册账号，M03 联调确认）、
--   demo_student_2 = 9000000000000000201（预留可读学生 ID，供 110 课程第二条评价；
--   user 库当前无此账号——评价/选课引用不强制外键，学生列表若展示该用户需先在 user 库补建）。

-- 分类树：3 顶级 + 各 2 子分类 = 9 行；slug 唯一、sort_order 有序、status=VISIBLE。
INSERT INTO course_category
  (id, parent_id, name, slug, sort_order, status, created_by, created_at, updated_by, updated_at)
VALUES
  (9000000000000000100, NULL, '前端开发', 'frontend-development', 1, 'VISIBLE', 9000000000000000002, '2026-08-09 08:00:00.000', 9000000000000000002, '2026-08-09 08:00:00.000'),
  (9000000000000000101, NULL, '后端开发', 'backend-development', 2, 'VISIBLE', 9000000000000000002, '2026-08-09 08:00:00.000', 9000000000000000002, '2026-08-09 08:00:00.000'),
  (9000000000000000102, NULL, '数据分析', 'data-analysis', 3, 'VISIBLE', 9000000000000000002, '2026-08-09 08:00:00.000', 9000000000000000002, '2026-08-09 08:00:00.000'),
  (9000000000000000103, 9000000000000000100, 'Web 基础', 'web-basics', 1, 'VISIBLE', 9000000000000000002, '2026-08-09 08:00:00.000', 9000000000000000002, '2026-08-09 08:00:00.000'),
  (9000000000000000104, 9000000000000000100, 'Vue 前端框架', 'vue-framework', 2, 'VISIBLE', 9000000000000000002, '2026-08-09 08:00:00.000', 9000000000000000002, '2026-08-09 08:00:00.000'),
  (9000000000000000105, 9000000000000000101, 'Java 后端', 'java-backend', 1, 'VISIBLE', 9000000000000000002, '2026-08-09 08:00:00.000', 9000000000000000002, '2026-08-09 08:00:00.000'),
  (9000000000000000106, 9000000000000000101, 'Spring Boot 微服务', 'spring-boot-microservices', 2, 'VISIBLE', 9000000000000000002, '2026-08-09 08:00:00.000', 9000000000000000002, '2026-08-09 08:00:00.000'),
  (9000000000000000107, 9000000000000000102, 'SQL 数据分析', 'sql-analytics', 1, 'VISIBLE', 9000000000000000002, '2026-08-09 08:00:00.000', 9000000000000000002, '2026-08-09 08:00:00.000'),
  (9000000000000000108, 9000000000000000102, 'Python 数据分析', 'python-analytics', 2, 'VISIBLE', 9000000000000000002, '2026-08-09 08:00:00.000', 9000000000000000002, '2026-08-09 08:00:00.000')
AS new
ON DUPLICATE KEY UPDATE
  parent_id = new.parent_id,
  name = new.name,
  slug = new.slug,
  sort_order = new.sort_order,
  status = new.status,
  updated_by = new.updated_by,
  updated_at = new.updated_at;

-- 课程根：6 已发布（2 免费含选课/评价）+ demo_teacher DRAFT 1 + PENDING_REVIEW 1。
INSERT INTO course
  (id, owner_teacher_id, lifecycle_status, published_version_id, draft_version_id,
   published_at, rating_avg, rating_count, enrollment_count, version,
   created_by, created_at, updated_by, updated_at)
VALUES
  (9000000000000000110, 9000000000000000001, 'PUBLISHED', 9000000000000000120, NULL,
   '2026-08-10 10:00:00.000', 4.50, 2, 2, 0, 9000000000000000001, '2026-08-09 09:00:00.000', 9000000000000000001, '2026-08-16 10:00:00.000'),
  (9000000000000000111, 9000000000000000001, 'PUBLISHED', 9000000000000000121, NULL,
   '2026-08-11 10:00:00.000', 4.00, 1, 1, 0, 9000000000000000001, '2026-08-09 09:30:00.000', 9000000000000000001, '2026-08-17 14:00:00.000'),
  (9000000000000000112, 9000000000000000001, 'PUBLISHED', 9000000000000000122, NULL,
   '2026-08-12 10:00:00.000', 0.00, 0, 0, 0, 9000000000000000001, '2026-08-10 08:00:00.000', 9000000000000000001, '2026-08-12 10:00:00.000'),
  (9000000000000000113, 9000000000000000001, 'PUBLISHED', 9000000000000000123, NULL,
   '2026-08-13 10:00:00.000', 0.00, 0, 0, 0, 9000000000000000001, '2026-08-10 08:30:00.000', 9000000000000000001, '2026-08-13 10:00:00.000'),
  (9000000000000000114, 9000000000000000001, 'PUBLISHED', 9000000000000000124, NULL,
   '2026-08-14 10:00:00.000', 0.00, 0, 0, 0, 9000000000000000001, '2026-08-11 08:00:00.000', 9000000000000000001, '2026-08-14 10:00:00.000'),
  (9000000000000000115, 9000000000000000001, 'PUBLISHED', 9000000000000000125, NULL,
   '2026-08-15 10:00:00.000', 0.00, 0, 0, 0, 9000000000000000001, '2026-08-11 08:30:00.000', 9000000000000000001, '2026-08-15 10:00:00.000'),
  (9000000000000000116, 9000000000000000001, 'DRAFT', NULL, 9000000000000000126,
   NULL, 0.00, 0, 0, 0, 9000000000000000001, '2026-08-18 08:00:00.000', 9000000000000000001, '2026-08-19 08:00:00.000'),
  (9000000000000000117, 9000000000000000001, 'PENDING_REVIEW', NULL, 9000000000000000127,
   NULL, 0.00, 0, 0, 0, 9000000000000000001, '2026-08-19 08:00:00.000', 9000000000000000001, '2026-08-20 09:30:00.000')
AS new
ON DUPLICATE KEY UPDATE
  owner_teacher_id = new.owner_teacher_id,
  lifecycle_status = new.lifecycle_status,
  published_version_id = new.published_version_id,
  draft_version_id = new.draft_version_id,
  published_at = new.published_at,
  rating_avg = new.rating_avg,
  rating_count = new.rating_count,
  enrollment_count = new.enrollment_count,
  version = new.version,
  updated_by = new.updated_by,
  updated_at = new.updated_at;

-- 课程不可变版本：每门课程 1 个版本（version_no=1）；cover_file_id 全 NULL。
INSERT INTO course_version
  (id, course_id, version_no, category_id, title, subtitle, description, level,
   price, currency, version_status, created_by, created_at)
VALUES
  (9000000000000000120, 9000000000000000110, 1, 9000000000000000103,
   '前端开发入门实战', '从零开始掌握 HTML/CSS/JavaScript 与工程化基础',
   '面向零基础学员的前端入门课程：HTML 语义化、CSS 布局、JavaScript 核心语法与模块化工程基础，配套动手练习。',
   'BEGINNER', 0.00, 'CNY', 'PUBLISHED', 9000000000000000001, '2026-08-09 09:00:00.000'),
  (9000000000000000121, 9000000000000000111, 1, 9000000000000000105,
   'Java 后端开发基础', 'Java 语法、面向对象与常用集合',
   'Java 语言基础到面向对象编程：JDK 工具链、语法、OOP、异常与常用集合，为后端开发打底。',
   'BEGINNER', 0.00, 'CNY', 'PUBLISHED', 9000000000000000001, '2026-08-09 09:30:00.000'),
  (9000000000000000122, 9000000000000000112, 1, 9000000000000000104,
   'Vue 3 组件化开发实战', '组合式 API、组件通信与工程化实践',
   'Vue 3 组合式 API、响应式原理、组件设计与通信、Vite 工程化与实战项目演练。',
   'INTERMEDIATE', 129.00, 'CNY', 'PUBLISHED', 9000000000000000001, '2026-08-10 08:00:00.000'),
  (9000000000000000123, 9000000000000000113, 1, 9000000000000000106,
   'Spring Boot 微服务实践', 'Spring Boot、Spring Cloud 与容器化部署',
   'Spring Boot 自动装配、Web/REST、数据访问、Spring Cloud 注册发现与网关、Docker 部署实践。',
   'ADVANCED', 199.00, 'CNY', 'PUBLISHED', 9000000000000000001, '2026-08-10 08:30:00.000'),
  (9000000000000000124, 9000000000000000114, 1, 9000000000000000107,
   'SQL 数据分析实战', 'SQL 查询优化与业务分析场景',
   '关系模型、复杂查询、窗口函数与查询优化，结合业务场景完成数据分析实战。',
   'BEGINNER', 89.00, 'CNY', 'PUBLISHED', 9000000000000000001, '2026-08-11 08:00:00.000'),
  (9000000000000000125, 9000000000000000115, 1, 9000000000000000108,
   'Python 数据分析入门', 'Pandas/NumPy 与可视化入门',
   'Python 基础、NumPy/Pandas 数据清洗与聚合、Matplotlib 可视化与小型分析项目。',
   'INTERMEDIATE', 159.00, 'CNY', 'PUBLISHED', 9000000000000000001, '2026-08-11 08:30:00.000'),
  (9000000000000000126, 9000000000000000116, 1, 9000000000000000106,
   '微服务架构设计（草稿）', '演进式架构、限流熔断与可观测性（演示草稿）',
   '演示用 DRAFT 课程：微服务拆分原则、流量治理与可观测性设计，尚未提交审核。',
   'ADVANCED', 299.00, 'CNY', 'DRAFT', 9000000000000000001, '2026-08-18 08:00:00.000'),
  (9000000000000000127, 9000000000000000117, 1, 9000000000000000103,
   'TypeScript 全栈进阶（待审）', '类型系统、服务端与前端全栈实践（演示待审）',
   '演示用 PENDING_REVIEW 课程：TypeScript 类型体操、Node 服务端与前端全栈工程实践，已提交审核待管理员审批。',
   'INTERMEDIATE', 169.00, 'CNY', 'PENDING_REVIEW', 9000000000000000001, '2026-08-19 08:00:00.000')
AS new
ON DUPLICATE KEY UPDATE
  course_id = new.course_id,
  version_no = new.version_no,
  category_id = new.category_id,
  title = new.title,
  subtitle = new.subtitle,
  description = new.description,
  level = new.level,
  price = new.price,
  currency = new.currency,
  version_status = new.version_status,
  created_by = new.created_by,
  created_at = new.created_at;

-- 授课教师归属：8 门课程全部 OWNER = demo_teacher（M05 种子阶段无其他已验证 TEACHER 账号）。
INSERT INTO course_teacher (id, course_id, teacher_id, teacher_role, joined_at)
VALUES
  (9000000000000000130, 9000000000000000110, 9000000000000000001, 'OWNER', '2026-08-09 09:00:00.000'),
  (9000000000000000131, 9000000000000000111, 9000000000000000001, 'OWNER', '2026-08-09 09:30:00.000'),
  (9000000000000000132, 9000000000000000112, 9000000000000000001, 'OWNER', '2026-08-10 08:00:00.000'),
  (9000000000000000133, 9000000000000000113, 9000000000000000001, 'OWNER', '2026-08-10 08:30:00.000'),
  (9000000000000000134, 9000000000000000114, 9000000000000000001, 'OWNER', '2026-08-11 08:00:00.000'),
  (9000000000000000135, 9000000000000000115, 9000000000000000001, 'OWNER', '2026-08-11 08:30:00.000'),
  (9000000000000000136, 9000000000000000116, 9000000000000000001, 'OWNER', '2026-08-18 08:00:00.000'),
  (9000000000000000137, 9000000000000000117, 9000000000000000001, 'OWNER', '2026-08-19 08:00:00.000')
AS new
ON DUPLICATE KEY UPDATE
  teacher_id = new.teacher_id,
  teacher_role = new.teacher_role,
  joined_at = new.joined_at;

-- 审核提交：待审课程唯一 PENDING 提交（submitted_by = demo_teacher）。
INSERT INTO course_audit_submission
  (id, course_id, course_version_id, status, submitted_by, submitted_at)
VALUES
  (9000000000000000140, 9000000000000000117, 9000000000000000127, 'PENDING',
   9000000000000000001, '2026-08-20 09:30:00.000')
AS new
ON DUPLICATE KEY UPDATE
  course_id = new.course_id,
  status = new.status,
  submitted_by = new.submitted_by,
  submitted_at = new.submitted_at;

-- 选课：fe_demo_10 免费选 2 门已发布课程（我的课程演示）+ demo_student_2 选 110
-- 为 110 课程第二条评价提供 ACTIVE 选课依据，与服务层 NOT_ENROLLED 校验一致。
INSERT INTO course_enrollment
  (id, course_id, student_id, source, status, enrolled_at, version)
VALUES
  (9000000000000000150, 9000000000000000110, 2091029641632157697, 'FREE', 'ACTIVE', '2026-08-16 10:00:00.000', 0),
  (9000000000000000151, 9000000000000000111, 2091029641632157697, 'FREE', 'ACTIVE', '2026-08-17 14:00:00.000', 0),
  (9000000000000000152, 9000000000000000110, 9000000000000000201, 'FREE', 'ACTIVE', '2026-08-18 10:00:00.000', 0)
AS new
ON DUPLICATE KEY UPDATE
  source = new.source,
  status = new.status,
  enrolled_at = new.enrolled_at,
  version = new.version;

-- 评价：3 条，全部对应已选课程；110 课程 5+4 → avg 4.50（落规格 §5.3 区间 [3.8, 4.9]），
-- 111 课程 4 → avg 4.00；course.rating_avg/rating_count 与之吻合。
INSERT INTO course_review
  (id, course_id, student_id, rating, content, status, created_by, created_at, updated_by, updated_at)
VALUES
  (9000000000000000160, 9000000000000000110, 2091029641632157697, 5,
   '课程结构清晰，零基础也能跟上，实战练习很有帮助。', 'VISIBLE',
   2091029641632157697, '2026-08-16 11:00:00.000', 2091029641632157697, '2026-08-16 11:00:00.000'),
  (9000000000000000161, 9000000000000000111, 2091029641632157697, 4,
   '讲解扎实，示例丰富，个别章节节奏稍快。', 'VISIBLE',
   2091029641632157697, '2026-08-17 15:00:00.000', 2091029641632157697, '2026-08-17 15:00:00.000'),
  (9000000000000000162, 9000000000000000110, 9000000000000000201, 4,
   '课程内容全面，示例贴近实际工作，推荐。', 'VISIBLE',
   9000000000000000201, '2026-08-18 11:00:00.000', 9000000000000000201, '2026-08-18 11:00:00.000')
AS new
ON DUPLICATE KEY UPDATE
  course_id = new.course_id,
  student_id = new.student_id,
  rating = new.rating,
  content = new.content,
  status = new.status,
  updated_by = new.updated_by,
  updated_at = new.updated_at;
