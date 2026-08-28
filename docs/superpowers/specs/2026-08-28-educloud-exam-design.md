# EduCloud 在线考试模块设计规格（并入 educloud-content）

> **面向：** 下一位接手考试模块开发与验收的工程师 / AI 代理。
> **日期：** 2026-08-28
> **状态：** 已与用户分节确认，待用户书面审查。

## 1. 概述

为 EduCloud 补齐**在线考试闭环**：教师维护题库（客观题）→ 组卷发布 → 学生限时在线考试 → 服务端机器判分 → 成绩与动态流/通知联动。前端考试 UI（学生端 `ExamSessionModal`、教师端 `ExamManage`）已存在但全部运行在 mock 数据上，本模块将其对接真实后端。

- **并入 `educloud-content` 模块**（与作业/证书同属课程内容域），不新建微服务。
- **无 AI 判卷**：仅客观题机器判分（单选/多选/判断），主观题不在本期范围。
- **纯派生能力**：任何故障不得影响登录、课程、学习、订单主链路；前端保留 mock 回退。

### 1.1 已确认决策（与用户逐项确认）

| 决策点 | 结论 |
|---|---|
| 模块归属 | 并入 `educloud-content`，不新建 `educloud-exam` 独立服务 |
| 题型范围 | 客观题全覆盖：单选 `SINGLE` / 多选 `MULTIPLE` / 判断 `JUDGE`，全部机器判分 |
| 教师端范围 | 题库 CRUD + 组卷 + 发布（不含成绩管理导出） |
| 考试纪律 | 限时 + 切屏次数监控（`tabSwitchCount >= 5` 标记 `flagged`，不影响分数）；不做摄像头抓拍 |
| 事件联动 | 及格/交卷后发布 `ExamGraded` 领域事件 → 动态流 + 站内通知；**不发证书** |
| 判分边界 | 多选全对得分、错选/漏选 0 分，不做部分得分 |

## 2. 模块总览

### 2.1 服务定位

| 项 | 值 |
|---|---|
| 宿主模块 | `educloud-backend/educloud-content/`（业务端口 8083，监控端口 8084） |
| 逻辑库 | `educloud_content`（新增 4 张表） |
| 网关路由 | 已预留：`/api/v1/me/exams**`（学生）、`/api/v1/exams/**`、`/api/v1/exam-attempts/**`（教师/管理），无需新增路由条目 |
| 迁移脚本 | `deploy/sql/content/V005__exam.sql` |

### 2.2 复用清单（零新基建）

| 现有能力 | 复用方式 |
|---|---|
| Outbox（`OutboxWriter` / `OutboxEventDispatcher` / `OutboxEventEntity`） | 事件发布与 CAS 认领投递全部复用 |
| `ContentEventPublisher` | 新增 `examGraded(...)` 方法（对齐现有 4 个事件方法风格） |
| RabbitMQ 交换机 | `educloud.events` 全域总线（routing key `exam.graded`，与 `assignment.graded` 同模式） |
| `CourseClient` 跨库反查 | 教师组卷时按课程筛选题库、考试列表展示课程标题 |
| 安全模型 | oauth2-resource-server + `SecurityContextFacade.currentUser()`，身份只取 JWT subject |
| 定时任务模式 | 超时收敛 @Scheduled（对齐订单超时收敛模式） |

## 3. 数据模型（`educloud_content` 库，4 张表）

### 3.1 `exam_bank_question` 题库题目表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| course_id | BIGINT | 归属课程 |
| teacher_id | BIGINT | 出题教师 |
| question_type | VARCHAR(16) | `SINGLE` / `MULTIPLE` / `JUDGE` |
| stem | TEXT | 题干 |
| options | JSON | 选项数组（判断题固定 `["正确","错误"]`） |
| answer | JSON | 答案数组（单选/判断 `[n]`，多选 `[0,2]`） |
| analysis | TEXT NULL | 答案解析（可选） |
| default_score | INT | 默认分值 |
| status | VARCHAR(16) | `ENABLED` / `DISABLED`（软删） |
| created_at / updated_at | DATETIME(3) | 审计 |

### 3.2 `exam` 考试表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| course_id | BIGINT | 课程 |
| course_title | VARCHAR(255) | 课程标题快照 |
| title | VARCHAR(255) | 考试标题 |
| description | TEXT NULL | 说明 |
| duration_minutes | INT | 限时时长 |
| total_score | INT | 总分（发布时由试卷汇总） |
| pass_score | INT | 及格分 |
| start_time / end_time | DATETIME(3) | 考试窗口 |
| status | VARCHAR(16) | `DRAFT` / `PUBLISHED` / `CLOSED` |
| teacher_id | BIGINT | 创建教师 |
| created_at / updated_at | DATETIME(3) | 审计 |

### 3.3 `exam_paper_question` 组卷明细表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| exam_id | BIGINT | 考试 |
| question_id | BIGINT | 题库题目 ID |
| question_snapshot | JSON | **题目快照**（组卷时复制题干/选项/答案/题型），判分与展示只读快照 |
| score | INT | 本题分值 |
| sort_order | INT | 排序 |

快照决策：已发布考试不受题库编辑影响，判分防篡改，这是考试存档正确性的基本要求。

### 3.4 `exam_attempt` 考试记录表

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 雪花 ID |
| exam_id | BIGINT | 考试 |
| student_id | BIGINT | 学员 |
| status | VARCHAR(16) | `IN_PROGRESS` / `GRADED` |
| started_at | DATETIME(3) | 服务端记录的开始时间 |
| submitted_at | DATETIME(3) NULL | 交卷/超时收敛时间 |
| score | INT NULL | 判分结果 |
| passed | TINYINT NULL | 是否及格 |
| answers_json | JSON NULL | 作答记录（questionId → 答案数组） |
| tab_switch_count | INT DEFAULT 0 | 切屏次数 |
| flagged | TINYINT DEFAULT 0 | `tabSwitchCount >= 5` 时置 1（仅标记） |
| timeout | TINYINT DEFAULT 0 | 是否超时自动交卷 |
| created_at / updated_at | DATETIME(3) | 审计 |

约束：`UNIQUE (exam_id, student_id)` — 同一学员同一考试仅一次有效考试（防重复考试刷分）。

## 4. API 设计

### 4.1 教师端（`/api/v1/teacher/exams/**`，需 `ROLE_TEACHER`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/teacher/exam-bank/questions` | 创建题目 |
| GET | `/api/v1/teacher/exam-bank/questions` | 题库列表（按课程筛选） |
| PUT | `/api/v1/teacher/exam-bank/questions/{id}` | 修改题目 |
| DELETE | `/api/v1/teacher/exam-bank/questions/{id}` | 软删（被已发布考试引用的题目拒绝） |
| POST | `/api/v1/teacher/exams` | 创建考试（基本信息 + 组卷 `[{questionId, score}]`） |
| GET | `/api/v1/teacher/exams` | 考试列表 |
| PUT | `/api/v1/teacher/exams/{id}` | 修改（仅 DRAFT） |
| POST | `/api/v1/teacher/exams/{id}/publish` | 发布（校验题目齐全、窗口合法、汇总总分） |
| DELETE | `/api/v1/teacher/exams/{id}` | 删除（仅 DRAFT） |

### 4.2 学生端（`/api/v1/me/exams/**`，登录即可）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/me/exams` | 考试列表（含状态与成绩摘要） |
| GET | `/api/v1/me/exams/{id}` | 考试详情（题目列表，**不含答案**） |
| POST | `/api/v1/me/exams/{id}/attempts` | 开始考试（服务端写 started_at，返回 attemptId） |
| POST | `/api/v1/me/exams/{id}/attempts/{attemptId}/submit` | 交卷（answers + tabSwitchCount → 判分） |
| GET | `/api/v1/me/exams/{id}/attempts/{attemptId}` | 成绩与答卷（判分后展示正确答案） |

## 5. 判分引擎与状态机

### 5.1 判分规则（服务端，交卷瞬间完成）

| 题型 | 规则 |
|---|---|
| SINGLE / JUDGE | 答案索引相等 → 满分，否则 0 |
| MULTIPLE | 选择集合与答案集合完全相等 → 满分，否则 0（不做部分得分） |

- `totalScore = Σ 试卷各题 score`，`passed = score >= pass_score`
- 判分只读 `exam_paper_question.question_snapshot`

### 5.2 状态机

```
exam:    DRAFT ──publish──> PUBLISHED ──end_time 到达（或手动关闭）──> CLOSED
attempt: IN_PROGRESS ──交卷──> GRADED
              └──超时收敛──> GRADED (timeout=1)
```

**展示状态映射**（API 返回给前端的展示态由上述状态推导，前端不做独立状态机）：

- 学生端列表：`NOT_STARTED`（当前时间 < start_time）、`IN_PROGRESS`（窗口内且 attempt 为 IN_PROGRESS 或未开始考试）、`GRADED`（attempt 已判分，含 score/passed）
- 教师端列表：`DRAFT`、`PUBLISHED`（含 ONGOING 语义：窗口内）、`CLOSED`（含 ENDED 语义：窗口已过或手动关闭）

- 交卷校验链：attempt 归属当前用户（防 IDOR）→ 考试已发布且窗口内 → `started_at + duration` 未超时；超时则按已答题目判分并置 `timeout=1`（服务端时间为准，防前端篡改倒计时）
- **超时收敛定时任务**：@Scheduled 每 30s 扫描超时未交卷的 `IN_PROGRESS` attempt 自动判分（防前端挂死导致记录悬空）
- 状态迁移经 CAS 条件更新（对齐 Outbox 幂等纪律）

### 5.3 切屏监控

- 前端答题弹窗打开时监听 `blur` + `visibilitychange` 计数，交卷随 `answers` 上报 `tabSwitchCount`
- 服务端存 `tab_switch_count`，`>= 5` 置 `flagged=1`（供教师端后续查看，本期不影响分数、不做教师端展示）

## 6. 事件联动

交卷判分完成后发布 `ExamGraded` 事件（一次交卷只发一个事件，不做提交/批改两段式）：

| 项 | 值 |
|---|---|
| routing key | `exam.graded` |
| 交换机 | `educloud.events`（全域总线，与 `assignment.graded` 同模式） |
| payload | `examId, examTitle, courseId, courseTitle, studentId, score, passed, gradedAt` |
| 发布点 | `ContentEventPublisher.examGraded(...)`，经 Outbox 投递 |

消费端改造：

1. **`educloud-analytics` `ActivityFeedConsumer`**：switch 增加 `"exam.graded"` / `"examgraded"` 映射；新增 `mapExamGraded(...)` → 学生动态文案：「通过了《XX》考试，得分 YY」/「完成了《XX》考试（未通过）」；作业队列绑定增加 `exam.graded` 路由（或复用通配队列，按现有队列声明方式对齐）。
2. **`educloud-notification` `DomainNotificationConsumer`**：新增 `ROUTING_KEY_EXAM_GRADED` 分支 → 站内通知「《XX》考试成绩：YY 分 · 已通过/未通过」，复用现有通知落库流程。

## 7. 安全模型

- 考试详情/答卷接口**永不下发答案**；`GET /me/exams/{id}/attempts/{attemptId}` 判分后才返回正确答案
- 交卷与查询均校验 attempt 归属（身份只取 JWT subject，禁止前端传参伪造——对齐 M13 修复的 IDOR 教训）
- 交卷时间以服务端为准（`started_at + duration`），前端倒计时仅展示
- 教师接口 RBAC `ROLE_TEACHER`；学生接口登录即可
- 删除/修改受状态机约束（仅 DRAFT 可改/删；被已发布考试引用的题目拒绝删除）

## 8. 前端改动

### 8.1 学生端（`student-portal`）

1. `types`：`ExamQuestion` 增加 `questionType`；`Exam` 增加 attempt 相关字段
2. `ExamSessionModal`：支持多选（选中集合）；判断复用 options 机制；**删除交卷后的 correctAnswer 展示**（mock 能力，真实系统不下发）；答题弹窗打开时加切屏监听
3. `studentAssignmentService`：`getExams` 改为先请求 `/me/exams` 再回退 localStorage（对齐 `getAssignments` 的既有模式）；新增 `startExam` / `submitExam` 对接真实 API

### 8.2 教师端（`teacher-portal`）

- `ExamManage`（现有 mock 页面骨架）落地为真实页面：
  - 题库管理：题目列表 / 创建 / 编辑（三种题型表单：题干、选项、答案、分值、解析）
  - 考试管理：创建考试（标题/课程/时长/窗口）、从题库组卷配分、发布/关闭
- `api.ts`：`getExams` 从 `delay(mockExams)` 改为真实 HTTP，保留 mock 回退
- 复用现有 UI 风格（学术编辑风组件、CustomSelect、弹窗模式）

## 9. 测试与门禁（对齐项目既有纪律）

| 层级 | 内容 |
|---|---|
| 单元测试 | 判分引擎（三种题型、边界、超时、空卷）、状态机迁移、快照防篡改 |
| 集成测试 | `-Pintegration` 下 attempt 全链路（开始→交卷→判分→Outbox 落库→事件投递） |
| 部署契约脚本 | `deploy/tests/content-exam-contract-tests.sh`：表结构、GRANT、网关路由（已预留验证）、事件路由键 |
| E2E | VM 上学生端考试全流程 + 教师端组卷发布 + 动态流/通知出现考试动态 |

## 10. 实施顺序建议

1. `V005__exam.sql` 迁移 + 实体/Mapper
2. 判分引擎（纯函数，先行单测）
3. 学生端 API（attempt 状态机 + 判分接入 + 超时收敛）
4. 教师端 API（题库 CRUD + 组卷 + 发布）
5. 事件发布 `examGraded` + analytics/notification 消费端
6. 前端学生端弹窗对接 + 教师端页面落地
7. 契约脚本 + 集成测试 + E2E 验收
