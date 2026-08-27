# EduCloud 角色化动态流 + 完课证书 设计规格

> **面向：** 接手实现的工程师 / AI 代理
> **日期：** 2026-08-27
> **模块：** 动态流（`activity_feed`）+ 完课证书（`certificate`），归属 `educloud-analytics`
> **状态：** 设计已确认，待实现

## 1. 概述与目标

为学员和教师提供**角色化的动态流**，并新增**完课证书**功能：

- **学员**在**学生端首页**看到自己的**学习动态**（报名 / 交作业 / 作业批改 / 完课 / 评价 / 获得证书 / 学习进度）。
- **教师**在**教师端工作台**看到自己的**教学动态**（上传 / 修改 / 发布课程 + 学员报名 / 交作业 / 评价）。
- **完课证书**：学员完课（学习进度 100%）自动生成证书，可下载，并触发证书动态。

### 1.1 已确认的关键决策（与用户确认）

| 决策 | 结论 |
|---|---|
| 角色化 | 学生看学习动态、教师看教学动态 |
| 数据源 | 动态事件表 `activity_feed`（方案 A） |
| 写入机制 | 事件驱动：消费各领域领域事件写入（方案 A2） |
| 归属服务 | `educloud-analytics` |
| 学生端位置 | 学生端首页「我的学习动态」区块 |
| 教师端位置 | 教师端工作台「最近动态」（升级现有卡片） |
| 范围 | 全部包含（含证书、学习进度），证书一并做 |

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│  各业务服务（已有领域事件，部分需补发）                            │
│  course / content / order / payment / certificate           │
└───────────────┬─────────────────────────────────────────────┘
                │ RabbitMQ 领域事件（Outbox → Exchange）
                ▼
┌─────────────────────────────────────────────────────────────┐
│  educloud-analytics：ActivityFeedConsumer（新增）             │
│  监听领域事件 → 解析 → 写入 activity_feed 表                    │
└───────────────┬─────────────────────────────────────────────┘
                │ 读
                ▼
┌─────────────────────────────────────────────────────────────┐
│  activity_feed 表（educloud_analytics 库）                    │
└───────────────┬─────────────────────────────────────────────┘
                │ 查询接口（按角色 + 用户过滤）
   ┌────────────┴────────────┐
   ▼                          ▼
学生端首页                 教师端工作台
「我的学习动态」           「最近动态」
```

**完课证书链路**：学习进度达 100% → 完课 → 生成证书（写 `course_certificate`）→ 发证书事件 → `ActivityFeedConsumer` 写证书动态。

## 3. 数据模型

### 3.1 动态表 `activity_feed`（新增，`educloud_analytics` 库）

```sql
CREATE TABLE activity_feed (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id     VARCHAR(64)  NOT NULL COMMENT '行为主体用户ID',
    actor_role   VARCHAR(16)  NOT NULL COMMENT 'STUDENT / TEACHER',
    action_type  VARCHAR(32)  NOT NULL COMMENT '动态类型，见 4.1',
    target_type  VARCHAR(32)  NULL COMMENT '目标类型：COURSE/ASSIGNMENT/CERTIFICATE',
    target_id    VARCHAR(64)  NULL COMMENT '目标ID（课程ID/作业ID/证书ID）',
    target_title VARCHAR(255) NULL COMMENT '目标标题（冗余，便于展示）',
    extra_json   JSON         NULL COMMENT '扩展字段：分数/进度/星级/评语',
    source_event VARCHAR(64)  NULL COMMENT '来源事件ID（幂等）',
    occurred_at  DATETIME(3)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_event (source_event),
    KEY idx_actor_role_time (actor_id, actor_role, occurred_at DESC),
    KEY idx_action_type (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色化动态流';
```

`source_event` 唯一约束保证**幂等**（同一事件重复消费不重复写动态）。

### 3.2 证书表 `course_certificate`（新增，归属 `educloud_content` 库）

```sql
CREATE TABLE course_certificate (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    cert_no       VARCHAR(64)  NOT NULL COMMENT '证书编号（唯一）',
    user_id       BIGINT       NOT NULL COMMENT '学员ID',
    course_id     BIGINT       NOT NULL COMMENT '课程ID',
    course_title  VARCHAR(255) NOT NULL COMMENT '课程标题快照',
    issued_at     DATETIME(3)  NOT NULL COMMENT '颁发时间',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cert_no (cert_no),
    UNIQUE KEY uk_user_course (user_id, course_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='完课证书';
```

`uk_user_course` 保证同一学员同一课程**只有一张证书**（幂等）。

## 4. 动态类型与领域事件

### 4.1 动态类型定义（`action_type`）

| action_type | 角色 | 文案模板 | 扩展字段 |
|---|---|---|---|
| `ENROLLED` | STUDENT | 你报名了《{title}》 | — |
| `ASSIGNMENT_SUBMITTED` | STUDENT | 你提交了《{title}》的作业 | — |
| `ASSIGNMENT_GRADED` | STUDENT | 你的《{title}》作业已批改：{score} 分 | `score` |
| `COURSE_COMPLETED` | STUDENT | 你完成了《{title}》 | — |
| `COURSE_REVIEWED` | STUDENT | 你评价了《{title}》：{rating} 星 | `rating` |
| `CERTIFICATE_ISSUED` | STUDENT | 你获得了《{title}》完课证书 | — |
| `PROGRESS_MILESTONE` | STUDENT | 你的《{title}》进度达到 {progress}% | `progress` |
| `COURSE_CREATED` | TEACHER | 你创建了《{title}》 | — |
| `COURSE_UPDATED` | TEACHER | 你更新了《{title}》 | — |
| `COURSE_PUBLISHED` | TEACHER | 你发布了《{title}》 | — |
| `STUDENT_ENROLLED` | TEACHER | 有学员报名了《{title}》 | `studentName` |
| `STUDENT_SUBMITTED` | TEACHER | 有学员提交了《{title}》作业 | `studentName` |
| `STUDENT_REVIEWED` | TEACHER | 有学员评价了《{title}》：{rating} 星 | `studentName`,`rating` |

### 4.2 领域事件来源与需补发清单

| 动态 | 领域事件 | 现状 | 说明 |
|---|---|---|---|
| 报名（学生/教师） | 选课/支付成功事件 | ✅ 已有 | 复用现有选课事件（含课程归属教师） |
| 交作业 | 作业提交事件 | ⚠️ 补发 | `AssignmentService` 提交时补发 `assignment.submitted` |
| 作业批改 | 作业批改事件 | ✅ 已有 | `AssignmentGraded`（content 已有） |
| 完课 | 完课事件 | ❌ 新增 | 学习进度 100% 时发 `course.completed` |
| 评价 | 课程评价事件 | ⚠️ 补发 | `CourseReviewService` upsert 时补发 `course.reviewed` |
| 证书 | 证书生成事件 | ❌ 新增 | 证书生成时发 `certificate.issued` |
| 学习进度 | 进度里程碑事件 | ❌ 新增 | 进度达 80% 等阈值时发 `progress.milestone` |
| 课程创建/修改/发布 | 课程事件 | ⚠️ 补发 | `CourseEventPublisher` 补发 `course.created/updated/published` |

**事件契约**：沿用项目现有领域事件信封（`source_service`/`event_type`/`payload_json`），通过 RabbitMQ 发布，`ActivityFeedConsumer` 订阅。

## 5. 动态消费者（`ActivityFeedConsumer`，analytics 新增）

- 位置：`educloud-analytics/src/main/java/com/educloud/analytics/messaging/ActivityFeedConsumer.java`
- 订阅上述领域事件的 exchange/queue（参照 analytics 现有 `AnalyticsEventConsumer` / notification 的消费模式）。
- 处理逻辑：解析事件 → 映射为 `activity_feed` 记录 → 插入（`source_event` 幂等）。
- **幂等**：`source_event` 唯一约束 + `INSERT ... ON DUPLICATE KEY UPDATE id=id`（或先查后插）。
- **容错**：解析失败记录日志 + 死信兜底（参照 search DLQ 模式），不阻断其他事件。
- 教师视角动态（`STUDENT_ENROLLED` 等）：从事件的课程归属解析出教师 `teacher_id`，`actor_id=teacher_id`、`actor_role=TEACHER`。

## 6. 完课证书子功能

### 6.1 证书归属（已定：`educloud_content`）
证书是**学习成果**，数据归属 `educloud-content`（学习/课程域）：
- 证书表 `course_certificate` 建在 `educloud_content` 库。
- 完课判定 + 证书生成在 `educloud-content`（`CertificateService`）。
- 证书生成后发 `certificate.issued` 事件 → analytics 写证书动态。

### 6.2 完课判定与证书生成
- **完课判定**：学习进度达 100%（`LearningProgressService`，需确认完课判定字段/逻辑）。
- 完课触发：进度更新到 100% 时 → 若该课程未发过证书 → 生成证书（`uk_user_course` 幂等）→ 发 `certificate.issued` 事件 + `course.completed` 事件。
- **证书编号**：规则生成（如 `CERT-{yyyyMMdd}-{雪花短码}`），保证唯一。
- **证书下载**：生成证书（可先返回证书信息 + 编号；证书图片生成可作为后续增强）。首期证书 = 证书记录 + 编号 + 可展示的证书信息页。

### 6.3 证书查询接口
- `GET /api/v1/content/certificates`（学员的证书列表）
- `GET /api/v1/content/certificates/{certNo}`（证书详情/下载）

## 7. 动态查询接口（analytics）

| 接口 | 说明 |
|---|---|
| `GET /api/v1/analytics/student/activities?limit=&actionType=` | 学员自己的学习动态（按当前登录学员过滤 `actor_role=STUDENT`） |
| `GET /api/v1/analytics/teacher/activities?limit=&actionType=` | 教师的教学动态（**升级现有接口**，改为读 `activity_feed`，按当前教师过滤） |

- 返回结构参照现有 `TeacherActivityItem`（id/action/content/timestamp），新增 `actionType`、`extra`。
- 时间返回 **ISO `timestamp`**（避免 Invalid Date，沿用既有修复经验）。
- 默认 `limit=10`，上限 50。
- 无动态返回空数组（前端显示空状态）。

## 8. 前端设计

### 8.1 学生端首页「我的学习动态」（新增区块）
- 位置：学生端首页（`Home.tsx`），与教师端「最近动态」对称。
- 内容：动态列表（类型图标 + 文案 + 相对时间）+ 类型筛选标签（全部/作业/课程）+ 「查看全部」。
- 类型图标：报名📖/交作业✍️/批改✅/完课🏆/证书🎓/进度📈/评价⭐（不同底色）。
- 相对时间：用 `timestamp` 计算相对时间（避免 Invalid Date）。
- 空状态：无动态时友好提示。
- 点击跳转：点击动态跳对应页面（课程页/作业页/证书页）。
- 未登录：不显示该区块（或提示登录）。

### 8.2 教师端工作台「最近动态」（升级现有卡片）
- 位置：教师端工作台（`Dashboard.tsx`），现有卡片。
- 升级：从审计日志改为读 `activity_feed`（教师教学动态）。
- 内容：上传/修改/发布课程 + 学员报名/交作业/评价（类型图标 + 文案 + 相对时间）。
- 保留现有卡片布局，内容角色化。

### 8.3 证书展示
- 学生端新增「我的证书」入口（个人中心/学习页），展示证书列表 + 证书信息。

## 9. 错误处理与降级

- **消费者容错**：单事件解析/写入失败 → 记日志 + 死信兜底，不阻断其他事件。
- **查询降级**：动态查询失败 → 返回空数组，前端显示空状态（不影响首页/工作台其他部分）。
- **幂等**：动态写入 `source_event` 唯一；证书 `uk_user_course` 唯一。
- **相对时间**：前端用 `timestamp` 计算，空/无效时显示绝对时间或省略。

## 10. 测试策略

- **消费者单测**：各事件类型 → 正确写入动态（含幂等、教师/学生角色映射）。
- **证书单测**：完课触发证书生成（幂等、编号唯一）。
- **查询接口测试**：按角色/用户过滤、分页、空结果。
- **前端**：动态列表渲染、类型筛选、相对时间、空状态。
- **集成**：完课 → 证书 → 证书动态全链路。

## 11. 实施分期

| 期 | 内容 |
|---|---|
| **P1** | 动态表 + 消费者 + 已有事件接入（报名/作业批改/选课）+ 学生端/教师端动态展示 |
| **P2** | 补发事件（交作业/评价/课程创建修改发布）+ 对应动态 |
| **P3** | 完课判定 + 证书功能 + 证书动态 + 我的证书页 |
| **P4** | 学习进度里程碑动态 + 动态点击跳转 + 类型筛选完善 |

## 12. YAGNI 边界（明确不做）

- ❌ 不做动态的点赞/评论/分享
- ❌ 不做跨用户关注/粉丝动态流
- ❌ 不做证书图片的复杂模板设计（首期证书 = 记录 + 编号 + 信息页）
- ❌ 不做动态的已读/未读标记（后续可加）
- ❌ 不做动态的多语言

## 13. 实现时需确认的点

- 完课判定字段/逻辑（`LearningProgressService`，进度 100% 如何判定）。
- 各领域事件的**实际事件类型名与 payload 结构**（`ContentEventPublisher`/`CourseEventPublisher` 现状）。
- 课程评价 `CourseReviewService` 的 upsert 触发点。
- analytics 消费各事件的 exchange/queue 绑定（参照现有 `AnalyticsEventConsumer`）。
