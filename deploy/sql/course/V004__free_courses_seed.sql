-- EduCloud Course 数据库：新增免费推荐课程（V004）
-- 依据：用户验收反馈（2026-08-24）——「我的课程」免费课程推荐区需要更多带真实封面的免费课。
-- 约定：幂等 INSERT ... AS new ON DUPLICATE KEY UPDATE；ID 延续固定区间（课程 118-119 / 版本 128-129 / 归属 138-139）；封面由部署脚本上传后 UPDATE。

INSERT INTO course
  (id, owner_teacher_id, lifecycle_status, published_version_id, draft_version_id,
   published_at, rating_avg, rating_count, enrollment_count, version,
   created_by, created_at, updated_by, updated_at)
VALUES
  (9000000000000000118, 9000000000000000001, 'PUBLISHED', 9000000000000000128, NULL,
   '2026-08-18 10:00:00.000', 4.50, 2, 2, 0, 9000000000000000001, '2026-08-17 09:00:00.000', 9000000000000000001, '2026-08-21 10:00:00.000'),
  (9000000000000000119, 9000000000000000001, 'PUBLISHED', 9000000000000000129, NULL,
   '2026-08-19 10:00:00.000', 4.50, 2, 2, 0, 9000000000000000001, '2026-08-18 09:00:00.000', 9000000000000000001, '2026-08-21 10:00:00.000')
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

INSERT INTO course_version
  (id, course_id, version_no, category_id, title, subtitle, description, level,
   price, currency, version_status, created_by, created_at)
VALUES
  (9000000000000000128, 9000000000000000118, 1, 9000000000000000103,
   'React 18 从入门到精通', 'Hooks、组件化与前端工程实战',
   '【课程简介】
以 React 18 为核心的前端框架实战课程：函数组件与 Hooks 心智模型、状态管理与路由、性能优化与工程化配置，最终完成三个可直接上线的业务项目。

【课程大纲】
模块一 React 基础：JSX、组件与 Props/State
模块二 Hooks 深入：useState/useEffect/useMemo/useRef 与自定义 Hooks
模块三 状态与路由：Context、Redux Toolkit 与 React Router
模块四 性能与工程化：memo/useCallback、Vite 配置、代码分割与测试
模块五 综合实战：电商后台管理端 + 移动端适配',
   'BEGINNER', 0.00, 'CNY', 'PUBLISHED', 9000000000000000001, '2026-08-17 09:00:00.000'),
  (9000000000000000129, 9000000000000000119, 1, 9000000000000000105,
   'Node.js 服务端开发实战', 'Express、数据库与接口安全',
   '【课程简介】
从零掌握 Node.js 服务端开发：事件循环与异步模型、Express 中间件体系、数据库接入（MySQL/Redis）与接口安全实践，学完即可独立开发高可用的 REST 服务。

【课程大纲】
模块一 Node 基础：事件循环、Buffer/Stream 与模块系统
模块二 Express 实战：路由、中间件、参数校验与统一异常
模块三 数据层：MySQL 接入、Redis 缓存与事务
模块四 安全与质量：JWT 鉴权、限流、日志与单元测试
模块五 综合实战：用户中心 + 订单接口服务',
   'INTERMEDIATE', 0.00, 'CNY', 'PUBLISHED', 9000000000000000001, '2026-08-18 09:00:00.000')
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

INSERT INTO course_teacher (id, course_id, teacher_id, teacher_role, joined_at)
VALUES
  (9000000000000000138, 9000000000000000118, 9000000000000000001, 'OWNER', '2026-08-17 09:00:00.000'),
  (9000000000000000139, 9000000000000000119, 9000000000000000001, 'OWNER', '2026-08-18 09:00:00.000')
AS new
ON DUPLICATE KEY UPDATE
  teacher_id = new.teacher_id,
  teacher_role = new.teacher_role,
  joined_at = new.joined_at;

-- 评价与选课（每门 2 人 2 条评价，评分 4.50/2 与汇总一致）
INSERT INTO course_enrollment
  (id, course_id, student_id, source, status, enrolled_at, version)
VALUES
  (9000000000000000167, 9000000000000000118, 9000000000000000205, 'FREE', 'ACTIVE', '2026-08-19 10:00:00.000', 0),
  (9000000000000000168, 9000000000000000118, 9000000000000000206, 'FREE', 'ACTIVE', '2026-08-19 11:00:00.000', 0),
  (9000000000000000169, 9000000000000000119, 9000000000000000205, 'FREE', 'ACTIVE', '2026-08-20 10:00:00.000', 0),
  (9000000000000000170, 9000000000000000119, 9000000000000000206, 'FREE', 'ACTIVE', '2026-08-20 11:00:00.000', 0)
AS new
ON DUPLICATE KEY UPDATE
  source = new.source,
  status = new.status,
  enrolled_at = new.enrolled_at,
  version = new.version;

INSERT INTO course_review
  (id, course_id, student_id, rating, content, status, created_by, created_at, updated_by, updated_at)
VALUES
  (9000000000000000177, 9000000000000000118, 9000000000000000205, 5,
   'Hooks 讲得通俗易懂，自定义 Hook 的封装思路非常实用，项目实战跟下来收获很大。', 'VISIBLE',
   9000000000000000205, '2026-08-20 10:30:00.000', 9000000000000000205, '2026-08-20 10:30:00.000'),
  (9000000000000000178, 9000000000000000118, 9000000000000000206, 4,
   '工程化部分很完整，从 Vite 到部署都能落地，就是最后一章节奏稍快。', 'VISIBLE',
   9000000000000000206, '2026-08-20 11:30:00.000', 9000000000000000206, '2026-08-20 11:30:00.000'),
  (9000000000000000179, 9000000000000000119, 9000000000000000205, 5,
   '中间件与鉴权章节讲解透彻，直接照着做就能搭出生产可用的服务端。', 'VISIBLE',
   9000000000000000205, '2026-08-21 10:30:00.000', 9000000000000000205, '2026-08-21 10:30:00.000'),
  (9000000000000000180, 9000000000000000119, 9000000000000000206, 4,
   '异步与数据库接入讲得扎实，练习题有梯度，适合有 JS 基础的开发者。', 'VISIBLE',
   9000000000000000206, '2026-08-21 11:30:00.000', 9000000000000000206, '2026-08-21 11:30:00.000')
AS new
ON DUPLICATE KEY UPDATE
  course_id = new.course_id,
  student_id = new.student_id,
  rating = new.rating,
  content = new.content,
  status = new.status,
  updated_by = new.updated_by,
  updated_at = new.updated_at;
