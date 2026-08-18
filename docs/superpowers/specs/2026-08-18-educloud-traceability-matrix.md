# EduCloud 页面—接口—服务追踪矩阵

> 状态：`【目标设计】`
>
> 说明：当前业务数据主要为前端 Mock；下列测试 ID 是目标验收场景，不代表已有自动化测试。

## 1. 使用方法

每个页面能力必须能追踪到：

```text
页面操作 → API → 权威服务 → 核心数据 → 权限 → 验收测试
```

实现任务完成时，在对应行增加实际接口版本、测试文件和验证证据。页面按钮存在但没有处理函数时，状态仍为 `【前端 Mock】`。

## 2. 学生端

| 页面/操作 | 当前 | 目标 API | 服务/数据 | 权限 | 验收 |
|---|---|---|---|---|---|
| 登录 | Mock 任意非空凭据 | `POST /auth/login`、`GET /me` | User：用户、会话 | 匿名/本人 | S-AUTH-01 |
| 学生注册 | “立即注册”入口无真实闭环 | `POST /auth/register` | User：学生账号/档案 | 匿名、限流、开关 | S-AUTH-03 |
| 刷新与退出 | localStorage 或缺失 | `POST /auth/refresh`、`POST /auth/logout` | User：Refresh 会话 | 本人 | S-AUTH-02 |
| 首页课程 | 本地生成数据 | `GET /courses`、`GET /recommendations/courses` | Course/Recommendation | 公开 | S-COURSE-01 |
| 课程筛选搜索 | 本地数组筛选 | `GET /search/courses` | Search：课程索引 | 公开 | S-SEARCH-01 |
| 课程详情 | Mock | `GET /courses/{id}` | Course | 按可见性 | S-COURSE-02 |
| 课程目录 | Mock | `GET /courses/{id}/chapters` | Content | 目录公开、课件受限 | S-CONTENT-01 |
| 免费选课 | 内存修改 | `POST /courses/{id}/enrollments` | Course：选课 | `course:enroll` | S-ENROLL-01 |
| 加入购物车 | Zustand 本地 | `POST /cart/items` | Order：购物车 | 本人 | S-ORDER-01 |
| 立即购买 | 仅加入购物车并跳转 | `POST /orders`、`POST /payments` | Order/Payment | 本人、幂等 | S-ORDER-02 |
| 我的课程 | Mock | `GET /me/enrollments` | Course：选课 | 本人 | S-ENROLL-02 |
| 进入课件 | Mock | `GET /coursewares/{id}/download-url`；Content 授权后内部申请 File grant | Content/File | 有效选课/教师/免费预览 | S-LEARN-01 |
| 更新进度 | 页面未持久化 | `PUT /coursewares/{id}/progress` | Content：学习进度 | 本人、有选课 | S-LEARN-02 |
| 作业列表 | Mock | `GET /me/assignments` | Content：作业/提交投影 | 本人 | S-ASG-01 |
| 提交作业 | 按钮无真实闭环 | `POST /assignments/{id}/submissions` | Content：提交 | 本人、有效选课 | S-ASG-02 |
| 查看批改 | 按钮无真实闭环 | `GET /submissions/{id}` | Content：提交/成绩 | 本人 | S-ASG-03 |
| 考试列表 | Mock | `GET /me/exams` | Content：考试投影 | 本人 | S-EXAM-01 |
| 开始考试 | 按钮无真实闭环 | `POST /exams/{id}/attempts` | Content：答卷快照 | 本人、时间窗口 | S-EXAM-02 |
| 保存答案/交卷 | 未实现 | `PUT /exam-attempts/{id}/answers/{questionId}`、`POST /exam-attempts/{id}/submit` | Content：答案/答卷 | 本人、幂等 | S-EXAM-03 |
| 个人资料 | 只显示成功提示 | `PATCH /me/profile` | User：档案 | 本人 | S-PROFILE-01 |
| 订单列表 | Mock | `GET /orders` | Order | 本人 | S-ORDER-03 |
| 去支付 | 按钮无真实闭环 | `POST /payments`、`GET /payments/{id}` | Payment | 本人 | S-PAY-01 |
| 退款申请 | 未实现 | `POST /orders/{id}/refund-requests` | Order/Payment | 本人 | S-REFUND-01 |
| 直播列表/详情 | Mock | `GET /live-rooms`、`GET /live-rooms/{id}` | Live | 按可见性 | S-LIVE-01 |
| 加入直播和聊天 | 本地状态 | `POST /live-rooms/{id}/join`、`POST /live-rooms/{id}/connection-ticket`、`GET /ws/v1/live/{id}?ticket=...` | Live | 有效选课、一次性票据 | S-LIVE-02 |
| 通知列表 | Zustand 会话状态 | `GET /notifications` | Notification | 本人 | S-NOTIFY-01 |
| 标记已读 | 本地更新 | `PUT /notifications/{id}/read` | Notification：收件箱 | 本人、幂等 | S-NOTIFY-02 |
| 社区信息流 | 本地 Store | `GET /community/posts` | Content：帖子 | 已登录/可见性 | S-COMM-01 |
| 发布和回复 | 本地 Store | `POST /community/posts`、`POST /community/posts/{id}/comments` | Content：帖子/评论 | 已登录 | S-COMM-02 |
| 点赞和收藏 | 本地 Store | `PUT/DELETE /community/targets/{type}/{id}/reactions/{reaction}` | Content：互动 | 本人、幂等 | S-COMM-03 |
| AI 助手 | 默认 Mock，可选远端 | `POST /assistant/questions` | Recommendation 后续适配 | `【后续规划】` | S-AI-01 |

## 3. 教师端

| 页面/操作 | 当前 | 目标 API | 服务/数据 | 权限 | 验收 |
|---|---|---|---|---|---|
| 登录和路由保护 | Mock，无统一 ProtectedRoute | `POST /auth/login`、`GET /me` | User | 教师身份 | T-AUTH-01 |
| 仪表盘 | Mock 指标 | `GET /analytics/teacher/overview` | Analytics | 教师本人 | T-DASH-01 |
| 课程列表 | 本地数组 | `GET /courses?owner=me` | Course | `course:update`/归属 | T-COURSE-01 |
| 创建课程 | 本地新增 | `POST /courses` | Course：课程 | `course:create` | T-COURSE-02 |
| 编辑课程 | 本地更新 | `GET /teacher/courses/{id}/draft`、`PUT /course-drafts/{versionId}` | Course | 课程归属、草稿版本 | T-COURSE-03 |
| 封面上传 | URL/占位图 | `POST /file-upload-sessions`、`POST /file-upload-sessions/{id}/complete` | File/Course | `file:upload`、归属 | T-FILE-01 |
| 提交审核 | 无完整闭环 | `POST /course-drafts/{versionId}/submit-review` | Course：不可变版本 | `course:submit`、归属 | T-AUDIT-01 |
| 下架/重新上架/归档 | 本地状态或缺失 | `POST /courses/{id}/offline`、`POST /courses/{id}/republish`、`POST /courses/{id}/archive` | Course：生命周期 | 分项权限、归属、状态机 | T-COURSE-04 |
| 章节维护 | 本地状态 | `GET /teacher/courses/{id}/content-draft`、`POST /courses/{id}/chapters`、`PUT /chapters/{id}` | Content | `content:manage`、草稿修订 | T-CONTENT-01 |
| 课件上传 | 只创建 `url:#` 元数据 | `POST /file-upload-sessions`、`POST /file-upload-sessions/{id}/complete`、`POST /chapters/{id}/coursewares` | File/Content | 上传和草稿归属 | T-CONTENT-02 |
| 提交内容审核 | 无完整闭环 | `POST /content-revisions/{revisionId}/submit-review` | Content：不可变修订/审核记录 | `content:manage`、归属 | T-CONTENT-03 |
| 直播创建 | 本地创建 | `POST /live-rooms` | Live | `live:manage`、归属 | T-LIVE-01 |
| 开始/结束直播 | 本地状态和随机人数 | `POST /live-rooms/{id}/start`、`POST /live-rooms/{id}/end` | Live | `live:manage`、状态机 | T-LIVE-02 |
| 作业发布 | Mock | `POST /courses/{id}/assignments`、`POST /assignments/{id}/publish` | Content | `assignment:manage` | T-ASG-01 |
| 提交列表 | Mock | `GET /assignments/{id}/submissions` | Content | 课程归属 | T-ASG-02 |
| 批改作业 | 本地分数/反馈 | `POST /submissions/{id}/grade` | Content | `assignment:grade`、版本 | T-ASG-03 |
| 考试创建 | 本地草稿 | `POST /courses/{id}/exams` | Content | `exam:manage` | T-EXAM-01 |
| 考试发布 | 按钮无真实处理 | `POST /exams/{id}/publish` | Content | 归属、分值/时间校验 | T-EXAM-02 |
| 主观题评分 | 未实现 | `POST /exam-attempts/{id}/grade` | Content | `exam:grade` | T-EXAM-03 |
| 学生列表 | Mock | `GET /courses/{id}/students` | Course | `course:student:read`、归属 | T-STUDENT-01 |
| 教学分析 | Mock | `GET /analytics/courses/{id}` | Analytics | 课程归属 | T-ANALYTICS-01 |

## 4. 管理端

| 页面/操作 | 当前 | 目标 API | 服务/数据 | 权限 | 验收 |
|---|---|---|---|---|---|
| 登录和保护 | localStorage Token 存在判断 | `POST /auth/login`、`GET /me` | User | 管理身份 | A-AUTH-01 |
| 仪表盘 | Mock | `GET /analytics/admin/overview` | Analytics | `analytics:admin:read` | A-DASH-01 |
| 用户列表 | Mock | `GET /users` | User | `user:read` | A-USER-01 |
| 禁用/恢复用户 | 内存修改 | `PATCH /users/{id}/status` | User | `user:status:update` | A-USER-02 |
| 删除用户 | 按钮无完整处理 | 无首期 API；入口移除或标记 `【后续规划】`，专项合规方案批准后另行设计 | User | 不可逆操作待双人复核方案 | A-USER-03 |
| 课程待审核 | Mock | `GET /course-audits`、`GET /course-audits/{id}` | Course | `course:audit` | A-CAUDIT-01 |
| 通过/驳回课程 | 本地处理 | `POST /course-audits/{id}/approve`、`POST /course-audits/{id}/reject` | Course | 不得自审、版本 | A-CAUDIT-02 |
| 内容待审核 | Mock | `GET /content-audits`、`GET /content-audits/{id}` | Content | `content:audit` | A-TAUDIT-01 |
| 通过/驳回内容 | UI 收集驳回原因但 API 丢失 | `POST /content-audits/{id}/approve`、`POST /content-audits/{id}/reject` | Content：不可变修订/审核记录 | 不得自审、版本；驳回原因必填 | A-TAUDIT-02 |
| 订单管理 | Mock | `GET /orders` | Order | `order:read:any` | A-ORDER-01 |
| 财务统计 | Mock | `GET /analytics/finance` | Analytics | 财务权限 | A-FIN-01 |
| 支付/退款 | Mock 只读 | `GET /payments`、`GET /payment-refunds`、`POST /refund-requests/{id}/approve`、`POST /refund-requests/{id}/reject` | Payment/Order | `payment:read`、`refund:review` | A-FIN-02 |
| 站点配置 | 本地保存 | `GET/PUT /platform-config/public` | User | `platform:config:update` | A-CONFIG-01 |
| SMTP/MinIO/JWT | 浏览器回显字段 | `GET /notification-channels/email/status`、`POST /notification-channels/email/tests`、`GET /files/storage-status`、`POST /files/storage-tests`、`GET /security/signing-key-status`；无 Secret 更新 API | Notification/File/User + Secret 运维面 | 分项状态/测试权限、审计 | A-CONFIG-02 |
| 服务状态 | 随机/静态 Mock | 无产品聚合 API；管理端跳转受保护的外部 Grafana，Actuator/Prometheus 仅监控网络可达 | Prometheus/Grafana 运维面 | 运维平台授权 | A-OPS-01 |
| 操作日志 | 生成数据 | `GET /audit-events` | Analytics 读模型 | `audit:read` | A-AUDIT-01 |

## 5. 跨服务关键验收场景

| ID | 场景 | 必须验证 |
|---|---|---|
| X-01 | 付费购课 | 服务端价格、回调验签、重复回调、订单已支付、唯一选课、通知 |
| X-02 | 退款 | 审核、渠道退款、订单状态、访问策略、历史保留、重复事件 |
| X-03 | 课程发布 | 教师归属、审核快照、禁止自审、Search/Notification 最终一致 |
| X-04 | 学习权限 | 未选课拒绝、免费预览、已选课访问、退款后限制 |
| X-05 | 作业 | 截止时间、迟交规则、重复提交、评分范围、并发评分 |
| X-06 | 考试 | 时间窗口、答卷快照、重复交卷、到期交卷、答案隐藏 |
| X-07 | 用户禁用 | 新登录失败、Refresh 撤销、已打开页面下次请求 401 |
| X-08 | 消息故障 | 来源业务成功、Outbox 保留、重试/死信、恢复后幂等消费 |
| X-09 | 敏感配置 | API、日志、前端构建产物均无原始密钥 |
| X-10 | 派生服务故障 | 搜索/分析/推荐失败不阻断核心业务 |

## 6. 完成标记规则

矩阵一行只有在以下证据齐全后才能从 `【前端 Mock】` 改为 `【已实现】`：

- 后端实现和数据库迁移存在。
- OpenAPI 契约与前端适配一致。
- 权限、资源归属、状态机和错误码经过测试。
- 页面已不再读取对应 Mock 数据。
- 相关前端 `typecheck/build` 通过。
- 日志、指标和审计可验证。
- 交付说明列出实际执行的验证命令和未完成边界。
