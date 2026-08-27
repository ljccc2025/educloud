# 角色化动态流 + 完课证书 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为学员和教师提供角色化动态流（学生端首页 + 教师端工作台），并新增完课证书功能（完课自动生成证书）。

**架构：** 各业务服务通过 Outbox 发布领域事件 → `educloud-analytics` 的 `ActivityFeedConsumer` 集中消费 → 写入 `activity_feed` 表 → 按角色查询。完课时 `educloud-content` 生成证书（`course_certificate` 表）并发证书事件。

**技术栈：** Java 17 / Spring Boot 3.2.5 / MyBatis-Plus / RabbitMQ / MySQL / React 18 / Vite。

**规格依据：** `docs/superpowers/specs/2026-08-27-activity-feed-certificate-design.md`

---

## 文件结构总览

**后端（新增）：**
- `deploy/sql/analytics/V003__activity_feed.sql` — 动态表
- `deploy/sql/content/V00X__course_certificate.sql` — 证书表
- `educloud-analytics/.../entity/ActivityFeedEntity.java`
- `educloud-analytics/.../mapper/ActivityFeedMapper.java`
- `educloud-analytics/.../messaging/ActivityFeedConsumer.java`
- `educloud-analytics/.../service/ActivityFeedService.java` + impl
- `educloud-analytics/.../controller/ActivityFeedController.java`
- `educloud-content/.../entity/CourseCertificateEntity.java`
- `educloud-content/.../mapper/CourseCertificateMapper.java`
- `educloud-content/.../service/CertificateService.java`
- `educloud-content/.../controller/CertificateController.java`

**后端（修改，补发事件）：**
- `educloud-content/.../messaging/ContentEventPublisher.java` — 加作业提交/批改、完课、证书事件方法
- `educloud-content/.../service/CourseProgressService.java` — 完课时发完课事件 + 触发证书
- `educloud-course/.../messaging/CourseEventPublisher.java` — 加课程创建/修改/发布事件
- `educloud-course/.../service/CourseReviewService.java` — 评价时发评价事件

**前端（修改）：**
- `student-portal/src/pages/Home.tsx` — 加「我的学习动态」区块
- `student-portal/src/services/api.ts` + `types/index.ts` — 动态接口 + 类型
- `teacher-portal/src/pages/Dashboard.tsx` — 升级「最近动态」
- `student-portal/src/pages/Certificates.tsx`（新增）— 我的证书页

---

## 阶段一：动态表 + 消费者 + 查询接口

### 任务 1：动态表迁移

**文件：**
- 创建：`deploy/sql/analytics/V003__activity_feed.sql`

- [ ] **步骤 1：创建迁移文件**

```sql
CREATE TABLE IF NOT EXISTS activity_feed (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id     VARCHAR(64)  NOT NULL COMMENT '行为主体用户ID',
    actor_role   VARCHAR(16)  NOT NULL COMMENT 'STUDENT / TEACHER',
    action_type  VARCHAR(32)  NOT NULL,
    target_type  VARCHAR(32)  NULL,
    target_id    VARCHAR(64)  NULL,
    target_title VARCHAR(255) NULL,
    extra_json   JSON         NULL,
    source_event VARCHAR(64)  NULL,
    occurred_at  DATETIME(3)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_event (source_event),
    KEY idx_actor_role_time (actor_id, actor_role, occurred_at DESC),
    KEY idx_action_type (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色化动态流';
```

- [ ] **步骤 2：提交**

```bash
git add deploy/sql/analytics/V003__activity_feed.sql
git commit -m "feat(动态): 新增角色化动态流表迁移"
```

### 任务 2：动态实体 + Mapper

**文件：**
- 创建：`educloud-analytics/src/main/java/com/educloud/analytics/entity/ActivityFeedEntity.java`
- 创建：`educloud-analytics/src/main/java/com/educloud/analytics/mapper/ActivityFeedMapper.java`
- 测试：`educloud-analytics/src/test/java/com/educloud/analytics/mapper/ActivityFeedEntityTest.java`

- [ ] **步骤 1：实体**

```java
@Data
@TableName("activity_feed")
public class ActivityFeedEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String actorId;
    private String actorRole;
    private String actionType;
    private String targetType;
    private String targetId;
    private String targetTitle;
    private String extraJson;
    private String sourceEvent;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
```

- [ ] **步骤 2：Mapper（含幂等插入 + 按角色查询）**

```java
@Mapper
public interface ActivityFeedMapper extends BaseMapper<ActivityFeedEntity> {
    @Insert("INSERT INTO activity_feed (actor_id, actor_role, action_type, target_type, target_id, target_title, extra_json, source_event, occurred_at) "
          + "VALUES (#{actorId}, #{actorRole}, #{actionType}, #{targetType}, #{targetId}, #{targetTitle}, #{extraJson}, #{sourceEvent}, #{occurredAt}) "
          + "ON DUPLICATE KEY UPDATE id = id")
    int insertIdempotent(ActivityFeedEntity entity);
}
```
（按角色查询用 MyBatis-Plus `LambdaQueryWrapper`：`eq(actor_id).eq(actor_role).orderByDesc(occurred_at).last("LIMIT n")`）

- [ ] **步骤 3：实体单测（字段映射 + Builder）**
- [ ] **步骤 4：运行测试验证通过** `mvn -pl educloud-analytics -am test`
- [ ] **步骤 5：提交** `feat(动态): 新增动态实体与幂等 Mapper`

### 任务 3：动态消费者（事件 → 动态）

**文件：**
- 创建：`educloud-analytics/src/main/java/com/educloud/analytics/messaging/ActivityFeedConsumer.java`
- 创建：`educloud-analytics/src/main/java/com/educloud/analytics/service/ActivityFeedService.java` + impl
- 测试：`educloud-analytics/src/test/java/com/educloud/analytics/messaging/ActivityFeedConsumerTest.java`

- [ ] **步骤 1：ActivityFeedService（事件 → 动态映射 + 幂等写入）**
  - 方法 `recordActivity(String actorId, String actorRole, String actionType, String targetType, String targetId, String targetTitle, Map<String,Object> extra, String sourceEvent, LocalDateTime occurredAt)`
  - 内部调 `activityFeedMapper.insertIdempotent`（source_event 幂等）
- [ ] **步骤 2：ActivityFeedConsumer**
  - 参照 analytics 现有 `AnalyticsEventConsumer` 的 `@RabbitListener` 模式订阅领域事件
  - 解析各事件类型 → 调 `ActivityFeedService.recordActivity`
  - 事件类型映射（见规格 4.1）：选课→ENROLLED/STUDENT_ENROLLED、作业批改→ASSIGNMENT_GRADED、课程发布→COURSE_PUBLISHED 等
  - 教师视角：从事件课程归属解析教师 `teacher_id`
- [ ] **步骤 3：消费者单测**（各事件 → 正确动态；幂等；教师/学生角色映射；解析失败容错）
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：提交** `feat(动态): 新增动态消费者写入动态流`

### 任务 4：动态查询接口

**文件：**
- 创建：`educloud-analytics/src/main/java/com/educloud/analytics/controller/ActivityFeedController.java`
- 创建：`educloud-analytics/src/main/java/com/educloud/analytics/dto/response/ActivityItem.java`
- 测试：`educloud-analytics/src/test/java/com/educloud/analytics/controller/ActivityFeedControllerTest.java`

- [ ] **步骤 1：ActivityItem DTO**（id/actionType/action 文案/targetTitle/extra/timestamp）
- [ ] **步骤 2：Controller**
```java
@GetMapping("/student/activities")
public ApiResponse<List<ActivityItem>> studentActivities(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue="10") int limit)
@GetMapping("/teacher/activities")
public ApiResponse<List<ActivityItem>> teacherActivities(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue="10") int limit)
```
  - 按当前登录用户 + 角色过滤；时间返回 ISO `timestamp`
  - 升级现有 `TeacherAnalyticsController.getActivities` 改为读 `activity_feed`（或新增端点，保留旧端点兼容）
- [ ] **步骤 3：Controller 单测**（按角色过滤、空结果、时间格式）
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：提交** `feat(动态): 新增学生/教师动态查询接口`

## 阶段二：事件补发

### 任务 5：补发作业提交/批改事件（content）

**文件：**
- 修改：`educloud-content/.../messaging/ContentEventPublisher.java` — 加 `assignmentSubmitted`、`assignmentGraded` 方法
- 修改：`educloud-content/.../service/AssignmentService.java` — 提交/批改时调事件发布

- [ ] **步骤 1：加事件方法**（参照 `contentRevisionPublished` 的 OutboxWriter 模式）
- [ ] **步骤 2：在提交/批改逻辑中调用**
- [ ] **步骤 3：测试 + 提交** `feat(内容): 补发作业提交与批改领域事件`

### 任务 6：补发课程创建/修改/发布事件（course）+ 评价事件

**文件：**
- 修改：`educloud-course/.../messaging/CourseEventPublisher.java` — 加 `courseCreated/Updated/Published`
- 修改：`educloud-course/.../service/CourseReviewService.java` — 评价 upsert 时发 `courseReviewed`

- [ ] **步骤 1：加课程事件方法**
- [ ] **步骤 2：课程创建/修改/发布逻辑中调用**
- [ ] **步骤 3：评价事件**（`CourseReviewService` upsert 时发，含 rating）
- [ ] **步骤 4：测试 + 提交** `feat(课程): 补发课程操作与评价领域事件`

## 阶段三：完课证书

### 任务 7：证书表 + 证书服务（content）

**文件：**
- 创建：`deploy/sql/content/V00X__course_certificate.sql`
- 创建：`educloud-content/.../entity/CourseCertificateEntity.java` + Mapper
- 创建：`educloud-content/.../service/CertificateService.java`
- 创建：`educloud-content/.../controller/CertificateController.java`

- [ ] **步骤 1：证书表迁移**（规格 3.2）
- [ ] **步骤 2：证书实体 + Mapper**
- [ ] **步骤 3：CertificateService**
  - `issueCertificate(studentId, courseId, courseTitle)`：幂等（`uk_user_course`）+ 生成证书编号（`CERT-{yyyyMMdd}-{短码}`）
- [ ] **步骤 4：证书查询接口** `GET /certificates`、`GET /certificates/{certNo}`
- [ ] **步骤 5：测试 + 提交** `feat(内容): 新增完课证书表与服务`

### 任务 8：完课触发证书 + 完课/证书事件

**文件：**
- 修改：`educloud-content/.../service/CourseProgressService.java` — 课程进度达 100% 时触发
- 修改：`educloud-content/.../messaging/ContentEventPublisher.java` — 加 `courseCompleted`、`certificateIssued` 方法

- [ ] **步骤 1：完课判定**：`CourseProgressService` 中 `progressPercent >= 100` 且未发过完课事件时 → 调 `CertificateService.issueCertificate` + 发 `courseCompleted` + `certificateIssued` 事件
- [ ] **步骤 2：事件方法**（参照 OutboxWriter 模式）
- [ ] **步骤 3：测试 + 提交** `feat(内容): 完课自动生成证书并发事件`

## 阶段四：前端

### 任务 9：学生端「我的学习动态」区块

**文件：**
- 修改：`student-portal/src/services/api.ts` + `types/index.ts` — 动态接口 + 类型
- 修改：`student-portal/src/pages/Home.tsx` — 加动态区块

- [ ] **步骤 1：类型 + 接口**（`getStudentActivities`，参照现有 `getActivities` 用 `timestamp`）
- [ ] **步骤 2：首页动态区块**（类型图标 + 文案 + 相对时间 + 筛选 + 空状态，参照设计 8.1）
- [ ] **步骤 3：相对时间用 `timestamp` 计算**（避免 Invalid Date）
- [ ] **步骤 4：`npx tsc --noEmit` 验证**
- [ ] **步骤 5：提交** `feat(学生端): 首页新增我的学习动态区块`

### 任务 10：教师端「最近动态」升级

**文件：**
- 修改：`teacher-portal/src/services/api.ts` + `types/index.ts`
- 修改：`teacher-portal/src/pages/Dashboard.tsx`

- [ ] **步骤 1：改 `getActivities` 调新动态接口**（读 `activity_feed`）
- [ ] **步骤 2：动态内容组合**（学员名 + 动作文案 + 课程名，空字段不拼接）
- [ ] **步骤 3：`npx tsc --noEmit` 验证**
- [ ] **步骤 4：提交** `feat(教师端): 最近动态升级为角色化教学动态`

### 任务 11：学生端「我的证书」页

**文件：**
- 创建：`student-portal/src/pages/Certificates.tsx`
- 修改：`student-portal/src/services/api.ts` + 路由 + 导航入口

- [ ] **步骤 1：证书接口 + 页面**（证书列表 + 证书信息）
- [ ] **步骤 2：路由 + 导航入口**
- [ ] **步骤 3：`npx tsc --noEmit` 验证**
- [ ] **步骤 4：提交** `feat(学生端): 新增我的证书页`

## 阶段五：部署 + 验证

### 任务 12：本地验证 + VM 部署 + E2E

- [ ] **步骤 1：后端全量测试** `mvn -q -f educloud-backend/pom.xml test`
- [ ] **步骤 2：三端 `tsc --noEmit`**
- [ ] **步骤 3：上传变更文件到 VM + 执行迁移**（analytics V003、content 证书表）
- [ ] **步骤 4：重编译受影响服务（analytics/content/course）+ 重启**
- [ ] **步骤 5：Playwright E2E**：完课 → 证书生成 → 学生端动态显示证书动态；报名 → 学生端/教师端动态显示；控制台无 Invalid Date
- [ ] **步骤 6：提交 + 推送**

---

## 自检对照

- 规格 §3 动态表 → 任务 1/2 ✓
- 规格 §4 动态类型/事件 → 任务 3/5/6 ✓
- 规格 §5 消费者 → 任务 3 ✓
- 规格 §6 证书 → 任务 7/8 ✓
- 规格 §7 查询接口 → 任务 4 ✓
- 规格 §8 前端 → 任务 9/10/11 ✓
- 规格 §9 错误处理 → 任务 3（消费者容错）/ 任务 4（空结果）✓
- 规格 §10 测试 → 各任务测试步骤 ✓
- 规格 §11 分期 → 阶段一~五 ✓

**实现时需确认**（实现各任务时先读对应代码）：完课判定字段（`UserCourseProgressEntity.progressPercent`）、各领域事件实际类型名与 payload、评价触发点、analytics 事件绑定。
