# EduCloud API、事件与前后端联调规范

> 状态：`【目标设计】`
>
> 外部入口：`http(s)://<gateway>/api/v1`

## 1. HTTP 约定

- API 使用 JSON；文件二进制通过 MinIO 短期授权地址传输。
- 资源路径使用复数名词，命令型状态迁移使用明确动作子路径。
- ID 在 JSON 中使用字符串。
- 时间使用 UTC ISO 8601，例如 `2026-08-18T08:30:00.000Z`。
- 金额使用十进制字符串，例如 `"199.00"`，币种单独返回 `"CNY"`。
- 布尔值使用 JSON `true/false`，不使用 `0/1`。
- 状态值使用稳定的大写字符串。
- 请求和响应使用 `camelCase`；数据库使用 `snake_case`。

## 2. 通用请求头

| 请求头 | 使用场景 | 说明 |
|---|---|---|
| `Authorization: Bearer <token>` | 受保护接口 | Access Token |
| `X-Request-Id` | 可选 | 客户端可提供；缺失时由 Gateway 生成 |
| `Idempotency-Key` | 创建订单、支付、退款、提交和交卷 | 同一用户和操作范围内唯一 |
| `If-Match` | 可选并发更新 | 使用资源版本；不支持时在请求体传 `version` |
| `Accept-Language` | 可选 | 当前默认 `zh-CN` |

Gateway 必须移除浏览器传入的 `X-User-Id`、`X-Role` 等可伪造内部身份头。

### 2.1 Gateway 路由优先级

外部 API 使用业务资源路径，Course 与 Content 存在 `/courses/**`、多个服务存在 `/me/**`。Gateway 必须按“具体路径优先、通用路径最后”配置和测试：

| 顺序 | 路径模式 | 目标服务 |
|---:|---|---|
| 10 | `/api/v1/auth/**`、`/api/v1/users/**`、`/api/v1/roles/**`、`/api/v1/permissions/**`、`/api/v1/platform-config/**`、`/api/v1/security/**` | User |
| 20 | 精确 `/api/v1/me`、`/api/v1/me/profile` | User |
| 30 | `/api/v1/me/assignments`、`/api/v1/me/exams`、`/api/v1/me/course-progress`、`/api/v1/me/courses/*/progress` | Content |
| 40 | `/api/v1/me/enrollments` | Course |
| 50 | `/api/v1/courses/*/chapters/**`、`/api/v1/courses/*/assignments/**`、`/api/v1/courses/*/exams/**` | Content |
| 60 | `/api/v1/chapters/**`、`/api/v1/coursewares/**`、`/api/v1/content-revisions/**`、`/api/v1/assignments/**`、`/api/v1/submissions/**`、`/api/v1/exams/**`、`/api/v1/exam-attempts/**`、`/api/v1/community/**`、`/api/v1/content-audits/**` | Content |
| 65 | `/api/v1/teacher/courses/*/content-draft`、`/api/v1/courses/*/content-drafts` | Content |
| 70 | `/api/v1/categories/**`、`/api/v1/course-drafts/**`、`/api/v1/course-audits/**`、`/api/v1/courses/**`、`/api/v1/teacher/courses/*/draft` | Course |
| 80 | `/api/v1/cart/**`、`/api/v1/orders/**`、`/api/v1/refund-requests/**` | Order |
| 90 | `/api/v1/payments/**`、`/api/v1/payment-callbacks/**`、`/api/v1/payment-refunds/**`、`/api/v1/reconciliations/**` | Payment |
| 100 | `/api/v1/live-rooms/**`、`/ws/v1/live/**` | Live |
| 110 | `/api/v1/files/**`、`/api/v1/file-upload-sessions/**` | File |
| 120 | `/api/v1/notifications/**`、`/api/v1/notification-channels/**` | Notification |
| 130 | `/api/v1/analytics/**`、`/api/v1/audit-events/**` | Analytics |
| 140 | `/api/v1/search/**` | Search |
| 150 | `/api/v1/recommendations/**`、未来 `/api/v1/assistant/**` | Recommendation |

`/internal/v1/**` 不配置外部 Gateway 路由。Route Contract 测试必须逐条断言目标服务，特别覆盖 `/courses/{id}`、`/courses/{id}/chapters`、`/courses/{id}/content-drafts`、`/teacher/courses/{id}/draft`、`/teacher/courses/{id}/content-draft`，以及 `/me` 与 `/me/exams`。

## 3. 统一响应

### 成功

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "id": "1960000000000000001"
  },
  "requestId": "2bb3f1b0-bc83-4c89-bff1-86d91589d9c1",
  "timestamp": "2026-08-18T08:30:00.000Z"
}
```

### 失败

```json
{
  "code": "ORDER_STATUS_CONFLICT",
  "message": "当前订单状态不允许取消",
  "data": {
    "currentStatus": "PAID"
  },
  "requestId": "2bb3f1b0-bc83-4c89-bff1-86d91589d9c1",
  "timestamp": "2026-08-18T08:30:00.000Z"
}
```

生产错误响应不包含堆栈、SQL、主机名、内部 URL 或第三方密钥。

## 4. HTTP 状态与业务错误码

| HTTP | 使用规则 | 示例错误码 |
|---:|---|---|
| 200 | 查询、幂等更新成功 | `SUCCESS` |
| 201 | 资源创建成功 | `SUCCESS` |
| 204 | 无响应体的删除或注销 | 无 |
| 400 | JSON、类型或字段校验失败 | `VALIDATION_FAILED` |
| 401 | 缺失、过期或被撤销的认证 | `UNAUTHENTICATED`、`TOKEN_EXPIRED` |
| 403 | 缺少权限或资源归属 | `ACCESS_DENIED` |
| 404 | 资源不存在或无权感知其存在 | `COURSE_NOT_FOUND` |
| 409 | 状态、版本或幂等冲突 | `VERSION_CONFLICT`、`ORDER_STATUS_CONFLICT` |
| 422 | 格式合法但违反业务规则 | `EXAM_NOT_OPEN` |
| 429 | 触发限流 | `RATE_LIMITED` |
| 500 | 未预期错误 | `INTERNAL_ERROR` |
| 503 | 依赖暂时不可用 | `DEPENDENCY_UNAVAILABLE` |

禁止只返回业务码 `200` 而使用 HTTP 200 表示所有结果。

## 5. 分页、筛选和排序

请求示例：

```text
GET /api/v1/courses?page=1&pageSize=20&categoryId=1001&level=BEGINNER&sort=publishedAt,desc
```

响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20,
    "total": 0,
    "totalPages": 0
  },
  "requestId": "2bb3f1b0-bc83-4c89-bff1-86d91589d9c1",
  "timestamp": "2026-08-18T08:30:00.000Z"
}
```

- `page` 从 1 开始。
- 默认 `pageSize=20`，最大值由服务配置，建议不超过 100。
- 排序字段必须来自服务端白名单，不能直接拼接客户端字段到 SQL。
- 时间区间使用 `from` 和 `to`，含义在接口文档中说明。

## 6. 幂等与并发

- `Idempotency-Key` 与当前用户、业务操作和请求摘要绑定。
- 同一键和相同请求返回首次结果；同一键但请求内容不同返回 409。
- 订单、支付和退款还必须依赖业务唯一号与数据库唯一索引。
- 状态更新携带 `version`，版本不匹配返回 `VERSION_CONFLICT`。
- 前端收到冲突后重新读取最新状态，不在本地强行覆盖。

## 7. User API

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/auth/register` | 匿名 | 仅学生自助注册；受公开开关、限流、唯一约束和密码策略保护 |
| POST | `/auth/login` | 匿名 | 登录并设置 Refresh Cookie |
| POST | `/auth/refresh` | Refresh Cookie | 原子轮换会话并返回 Access Token；并发旧 Token 返回稳定冲突 |
| POST | `/auth/logout` | 已登录 | 撤销当前会话并清除 Cookie |
| POST | `/auth/password/change` | 已登录 | 修改密码并撤销其他会话 |
| GET | `/me` | 已登录 | 当前用户和权限摘要 |
| PATCH | `/me/profile` | 已登录 | 更新本人档案 |
| GET | `/users` | `user:read` | 管理端用户分页 |
| GET | `/users/{id}` | `user:read` | 用户详情 |
| PATCH | `/users/{id}/status` | `user:status:update` | 锁定、禁用或恢复 |
| PUT | `/users/{id}/roles` | `rbac:assign` | 分配角色 |
| GET | `/roles` | `rbac:read` | 角色列表 |
| POST/PUT | `/roles`、`/roles/{id}` | `rbac:manage` | 角色维护 |
| GET | `/permissions` | `rbac:read` | 权限目录 |
| GET | `/platform-config/public` | 匿名 | 站点公开配置 |
| PUT | `/platform-config/public` | `platform:config:update` | 更新非敏感配置 |
| GET | `/security/signing-key-status` | `security:key-status:read` | 仅返回活动 `kid`、公钥数量、更新时间和下次轮换时间，不返回私钥 |

登录请求：

```json
{
  "loginName": "student@example.com",
  "password": "<not-logged>",
  "portal": "STUDENT"
}
```

登录响应的 `data` 包含 `accessToken`、`expiresIn` 和用户摘要；Refresh Token 只通过 HttpOnly Cookie 返回。

`/me`、用户详情和用户分页响应中的 `avatarUrl` 是短期展示地址，不是对象永久地址。User 在完成本人/管理查询授权后，按当前页最多一次调用 File 批量下载授权；单条详情可以调用单文件授权。URL 不写入 User 数据库或长期缓存，过期后由前端重新请求业务 DTO。

## 8. Course API

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/categories` | 匿名 | 可见分类 |
| GET | `/courses` | 匿名 | 已发布课程列表；管理查询使用权限参数 |
| GET | `/courses/{id}` | 按可见性 | 课程详情 |
| POST | `/courses` | `course:create` | 教师创建草稿 |
| GET | `/teacher/courses/{id}/draft` | `course:update` 加课程归属 | 返回当前可编辑草稿，不影响公开版本 |
| POST | `/courses/{id}/drafts` | `course:update` 加课程归属 | 从发布/驳回版本复制新草稿 |
| PUT | `/course-drafts/{versionId}` | `course:update` 加课程归属 | 只更新 `DRAFT` 版本 |
| POST | `/course-drafts/{versionId}/submit-review` | `course:submit` 加课程归属 | 提交不可变版本审核 |
| GET | `/course-audits` | `course:audit` | 管理端待审核课程分页 |
| GET | `/course-audits/{id}` | `course:audit` | 审核快照和历史 |
| POST | `/course-audits/{id}/approve` | `course:audit` | 审批通过 |
| POST | `/course-audits/{id}/reject` | `course:audit` | 驳回，原因必填 |
| POST | `/course-audits/{id}/withdraw` | 提交教师 | 审核前撤回并使版本不可再审批 |
| POST | `/courses/{id}/offline` | `course:offline` | 下架 |
| POST | `/courses/{id}/republish` | `course:republish` | 仅将仍有有效发布版本和就绪内容投影的 `OFFLINE` 课程重新上架 |
| POST | `/courses/{id}/archive` | `course:archive` | 归档且不可重新销售；已发布课程必须先下架 |
| POST | `/courses/{id}/enrollments` | `course:enroll` | 免费课程选课 |
| GET | `/me/enrollments` | 学生 | 我的课程 |
| GET | `/courses/{id}/students` | `course:student:read` 加课程归属 | 教师学生列表 |
| POST | `/courses/{id}/reviews` | 已选课学生 | 创建/更新评价 |

课程列表项保留当前 UI 需要的标题、短期 `coverUrl`、教师、分类、难度、价格、评分、选课数和是否已选。Course 只为已通过当前查询可见性校验的课程组装封面：每页最多一次调用 File 批量下载授权，匿名目录请求使用 `subjectType=ANONYMOUS` 和 `PUBLIC_CATALOG` purpose，不能借此访问未发布课程。学习进度由 Content 批量进度接口返回，前端 API 适配层为“我的课程”卡片组合数据，Course 不复制学习进度权威事实。

## 9. Content API

### 9.1 目录与学习

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/courses/{courseId}/chapters` | 课程目录；只返回课件元数据，不内嵌长期对象地址 |
| GET | `/teacher/courses/{courseId}/content-draft` | 教师读取当前内容草稿修订 |
| POST | `/courses/{courseId}/content-drafts` | 从发布/驳回修订复制新草稿 |
| POST | `/courses/{courseId}/chapters` | 教师在请求指定的草稿修订新增章节 |
| PUT/DELETE | `/chapters/{id}` | 更新或删除未受保护章节 |
| POST | `/chapters/{id}/coursewares` | 绑定课件元数据 |
| PUT/DELETE | `/coursewares/{id}` | 更新或移除课件 |
| POST | `/content-revisions/{revisionId}/submit-review` | 提交不可变课程内容修订审核 |
| PUT | `/coursewares/{id}/progress` | 幂等上报位置和完成事件 |
| GET | `/coursewares/{id}/download-url` | Content 校验有效选课/教师归属/免费预览后，代表用户向 File 申请短期访问授权 |
| GET | `/me/courses/{courseId}/progress` | 当前学生课程进度 |
| GET | `/me/course-progress?courseIds={ids}` | 批量返回课程进度，避免课程卡片 N+1 请求 |

进度请求示例：

```json
{
  "positionSeconds": 320,
  "watchedDeltaSeconds": 30,
  "completed": false,
  "eventAt": "2026-08-18T08:30:00.000Z"
}
```

服务端校验最大增量和课件时长，不接受客户端直接提交课程百分比。

### 9.2 作业

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/courses/{courseId}/assignments` | 查询或创建作业 |
| PUT | `/assignments/{id}` | 更新草稿 |
| POST | `/assignments/{id}/publish` | 发布作业 |
| POST | `/assignments/{id}/close` | 关闭作业 |
| GET | `/me/assignments` | 学生作业投影，兼容当前筛选状态 |
| POST | `/assignments/{id}/submissions` | 学生提交或按规则重交 |
| GET | `/assignments/{id}/submissions` | 教师提交列表 |
| GET | `/submissions/{id}` | 学生本人或课程教师查看 |
| POST | `/submissions/{id}/grade` | 教师评分与反馈 |

评分请求包含 `score`、`feedback` 和 `version`。服务端校验分数范围和课程归属。

作业详情、提交列表中的附件仅在调用者通过学生本人或课程教师授权后返回短期地址；同一响应的附件通过一次 File 批量授权组装，不逐附件发起远端调用。退款或选课撤销后不得再刷新附件地址，历史提交元数据仍保留。

### 9.3 考试

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/courses/{courseId}/exams` | 查询或创建考试 |
| PUT | `/exams/{id}` | 更新考试草稿和题目 |
| POST | `/exams/{id}/publish` | 校验分值与时间后发布 |
| GET | `/me/exams` | 学生考试状态 |
| POST | `/exams/{id}/attempts` | 创建个人答卷快照 |
| GET | `/exam-attempts/{id}` | 读取答卷，不返回隐藏答案 |
| PUT | `/exam-attempts/{id}/answers/{questionId}` | 保存单题答案 |
| POST | `/exam-attempts/{id}/submit` | 幂等交卷 |
| POST | `/exam-attempts/{id}/grade` | 主观题批改 |

开始考试响应包含服务端 `startedAt`、`deadlineAt` 和题目快照；客户端倒计时不具备裁决权。

### 9.4 社区和内容审核

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/community/posts` | 信息流和发布帖子 |
| GET/PUT/DELETE | `/community/posts/{id}` | 详情、本人编辑和可审计删除 |
| POST | `/community/posts/{id}/comments` | 评论或回复 |
| PUT | `/community/targets/{type}/{id}/reactions/{reaction}` | 幂等添加点赞/收藏 |
| DELETE | 同上 | 移除点赞/收藏 |
| GET | `/content-audits` | 管理端待审核内容 |
| GET | `/content-audits/{id}` | 管理端读取提交时绑定的不可变内容修订和审核历史 |
| POST | `/content-audits/{id}/approve` | 审核通过 |
| POST | `/content-audits/{id}/reject` | 驳回且原因必填 |
| POST | `/content-audits/{id}/withdraw` | 提交教师在审核前撤回 |

## 10. Order 与 Payment API

### 10.1 Order

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/cart/items` | 当前用户购物车查询或添加 |
| PATCH/DELETE | `/cart/items/{id}` | 更新选择状态或删除 |
| POST | `/orders` | 根据服务端课程销售快照创建付费订单；免费课程使用选课接口 |
| GET | `/orders` | 学生本人或管理端按权限查询 |
| GET | `/orders/{id}` | 订单详情和支付摘要 |
| POST | `/orders/{id}/cancel` | 取消待支付订单 |
| POST | `/orders/{id}/refund-requests` | 发起退款申请 |
| POST | `/refund-requests/{id}/approve` | 财务审核退款 |
| POST | `/refund-requests/{id}/reject` | 驳回退款并记录原因 |

首期不建设优惠券领域。创建订单请求只包含课程 ID 列表，不包含可信总金额或任何折扣金额：

```json
{
  "courseIds": ["1960000000000000001"]
}
```

多课程订单退款请求必须指定 `orderItemIds` 和原因；服务端按订单项权威金额计算退款额。整单退款可以提交全部订单项，客户端不能直接指定可信退款总额。

### 10.2 Payment

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/payments` | 为有效待支付订单创建支付单 |
| GET | `/payments/{id}` | 当前用户或财务查询支付状态 |
| POST | `/payment-callbacks/{channel}` | 渠道回调；不使用用户 JWT，必须验签 |
| GET | `/payments` | 财务管理分页 |
| GET | `/payment-refunds` | 财务退款查询 |
| POST | `/reconciliations` | 发起对账任务 |
| GET | `/reconciliations/{id}` | 查询差异和处理状态 |

支付回调不能返回统一业务包装后再改变渠道要求；具体 HTTP 响应由渠道适配器定义并测试。

`POST /payments` 只接受 `orderId` 和渠道。Payment 对 `orderId` 建唯一支付聚合：存在活动尝试时返回同一尝试，只有失败/过期后才创建下一尝试。返回包含 `paymentId`、`attemptId`、`status` 和渠道跳转信息；客户端金额不参与裁决。

## 11. Live、File 与 Notification API

### 11.1 Live

- `GET/POST /live-rooms`
- `GET/PUT /live-rooms/{id}`
- `POST /live-rooms/{id}/start`
- `POST /live-rooms/{id}/end`
- `POST /live-rooms/{id}/join`
- `POST /live-rooms/{id}/leave`
- `POST /live-rooms/{id}/connection-ticket`：签发一次性、短期 WebSocket 票据
- `GET /live-rooms/{id}/messages`
- `GET /live-rooms/{id}/replays`
- WebSocket：`/ws/v1/live/{roomId}?ticket=<one-time-ticket>`。浏览器不能为原生 WebSocket 自定义 `Authorization` 头，因此票据必须短期、一次性、绑定用户和直播间，并避免写入访问日志。

回放列表先校验课程访问权，再按当前页一次调用 File 批量下载授权组装短期 `replayUrl`；回放对象不可用时返回明确状态而不是伪造 URL，选课撤销后不能刷新地址。

### 11.2 File

1. `POST /file-upload-sessions` 创建会话并返回短期上传信息。
2. 浏览器直接上传至授权对象地址。
3. `POST /file-upload-sessions/{id}/complete` 确认对象、大小和类型。
4. 领域创建/更新接口提交 `fileId` 完成业务绑定。
5. 不提供“只凭 `fileId`”的公共下载接口。User/Course/Content/Live 先按业务资源授权，再使用服务 Token 调用 File 内部下载授权；例如课件公开入口为 `GET /coursewares/{id}/download-url`。
6. `GET /files/storage-status` 需要 `file:storage:status:read`，只返回提供方类型、连通状态、脱敏端点标识、检查时间和最近错误类别。
7. `POST /files/storage-tests` 需要 `file:storage:test`，执行限频、可审计的最小读写探测；请求与响应都不携带 Access Key 或 Secret Key。

### 11.3 Notification

- `GET /notifications`
- `GET /notifications/unread-count`
- `PUT /notifications/{id}/read`
- `PUT /notifications/read-all`
- `DELETE /notifications/{id}` 只删除当前用户收件箱视图
- 管理端模板与投递结果接口需要独立权限。
- `GET /notification-channels/email/status` 需要 `notification:channel:status:read`，只返回启用状态、脱敏发件人、检查时间和最近错误类别。
- `POST /notification-channels/email/tests` 需要 `notification:channel:test`，向当前管理员已验证邮箱发送限频测试邮件并记录审计；请求与响应不携带 SMTP 密码。

SMTP、MinIO、JWT 签名密钥等 Secret 不提供产品侧读取或更新 API。生产更新由 Kubernetes Secret/CI 受控流程完成，本地由未提交的 `.env` 完成；上述接口只用于读取非敏感状态或执行受限连接测试。

## 12. Analytics、Search 与 Recommendation API

| 服务 | 主要接口 |
|---|---|
| Analytics | `/analytics/teacher/overview`、`/analytics/courses/{id}`、`/analytics/admin/overview`、`/analytics/finance`、`/audit-events` |
| Search | `/search/courses`、`/search/content`；管理接口 `/search/index-tasks`、`/search/rebuild` |
| Recommendation | `/recommendations/courses`、`/recommendations/{courseId}/feedback` |

`POST /assistant/questions` 属于 `【后续规划】`。在真实后端未实现前，学生端必须继续显示演示模式或已连接外部服务的真实状态。

## 13. 领域事件契约

统一事件信封：

```json
{
  "eventId": "e548f425-c153-4679-bca8-98b9bc24f018",
  "eventType": "OrderPaid",
  "eventVersion": 1,
  "sourceService": "educloud-order",
  "sourceSequence": 18422,
  "aggregateType": "Order",
  "aggregateId": "1960000000000000001",
  "aggregateVersion": 7,
  "occurredAt": "2026-08-18T08:30:00.000Z",
  "requestId": "2bb3f1b0-bc83-4c89-bff1-86d91589d9c1",
  "traceId": "abc123",
  "data": {}
}
```

核心事件目录：

- User：`UserRegistered`、`UserStatusChanged`、`RoleAssignmentChanged`。
- Course：`CoursePublished`、`CourseChanged`、`CourseOffline`、`EnrollmentCreated`、`EnrollmentRevoked`。
- Content：`CoursewareChanged`、`LearningProgressChanged`、`AssignmentSubmitted`、`AssignmentGraded`、`ExamSubmitted`、`ExamGraded`、`CommunityContentChanged`。
- Content 发布审批：`ContentRevisionPublished`；事件携带课程 ID、内容根/修订 ID、可索引安全快照和 `CourseContent` 聚合版本，驱动 Course 就绪投影与 Search 内容索引。
- Order：`OrderCreated`、`OrderCancelled`、`OrderPaid`、`PaymentCompensationRequested`、`RefundRequested`、`OrderRefunded`。
- Payment：`PaymentSucceeded/PaymentFailed` 使用 `PaymentOrder` 聚合；`RefundSucceeded` 使用 `PaymentRefund` 聚合并携带订单项分摊；另有 `ReconciliationMismatchDetected`。
- Live：`LiveStarted`、`LiveEnded`、`LiveReplayAvailable`。
- File：以 `FileObject` 为聚合发布 `FileUploaded`、`FileBound`、`FileUnbound`、`FileDeleted`。

`eventVersion` 表示消息结构版本，`aggregateVersion` 表示同一聚合完整事件流的业务顺序，`sourceSequence` 表示来源服务按提交顺序分配的重建水位，三者不能混用。一个投影只要依赖某类聚合，其队列就必须接收该聚合的全部事件信封：相关事件执行业务投影，无关事件不解析 `data`、只在 Inbox 同事务推进最后聚合版本。不得只绑定事件子集后仍要求 `last+1`。首次接入且第一条版本大于 1 时先从来源快照引导，再接续后续事件。事件字段只允许向后兼容地增加；删除、改名或改变语义需要提升 `eventVersion` 并提供并行消费期。

## 14. 内部服务接口

内部接口统一位于 `/internal/v1`，只在内部网络可达并要求服务身份；浏览器不能通过 Gateway 普通路由访问：

| 服务 | 接口 | 调用方 | 用途 |
|---|---|---|---|
| Course | `GET /internal/v1/courses/{id}/sales-snapshot` | Order | 获取已发布状态、可信价格、标题和封面快照 |
| Course | `GET /internal/v1/courses/{id}/access-context` | Content、Live | 校验当前用户的教师归属或课程访问上下文 |
| User | `GET /internal/v1/users/{id}/status-snapshot` | 已登记的 User 状态投影消费者 | `{userId,status,tokenVersion,aggregateVersion}`，用于首次接入/版本缺口校准 |
| Course | `GET /internal/v1/enrollments/{id}/access-snapshot` | Content | `{enrollmentId,courseId,studentId,status,aggregateVersion}`，用于学习访问投影校准 |
| Content | `GET /internal/v1/course-content/{contentRootId}/readiness-snapshot` | Course | `{contentRootId,courseId,publishedRevisionId,ready,aggregateVersion}`，只用于事件投影引导/缺口修复，正常提交审批不进行同步回调 |
| Order | `GET /internal/v1/orders/{id}/fulfillment-snapshot` | Course | `{orderId,status,items[{orderItemId,courseId,paidAmount,refundedAmount,fulfillmentStatus}],aggregateVersion}`，用于付费选课/退款投影校准 |
| Order | `GET /internal/v1/orders/{id}/payable-snapshot` | Payment | 校验订单所有人、状态、总额、币种、失效时间及订单项 ID/课程/金额分摊 |
| Payment | `GET /internal/v1/payments/{id}/status-snapshot` | Order | `{paymentId,orderId,status,providerPaidAt,aggregateVersion}`，用于订单支付投影校准 |
| Payment | `GET /internal/v1/payment-refunds/{id}/status-snapshot` | Order | `{refundId,orderId,status,items[{orderItemId,courseId,amount}],aggregateVersion}`，用于订单项退款投影校准 |
| File | `GET /internal/v1/files/{id}/availability` | User、Course、Content、Live | 校验文件可用、类型和调用方绑定权限 |
| File | `POST /internal/v1/files/{id}/download-grants` | User、Course、Content、Live | 单文件短期地址；调用方先完成终端用户授权，File 根据服务身份推导 ownerService 并校验精确绑定 |
| File | `POST /internal/v1/file-download-grants/batch` | User、Course、Content、Live | 最多 100 个绑定项的批量短期地址，用于头像、封面、附件和回放列表，禁止远端 N+1 |
| Course | `GET /internal/v1/courses/export` | Search 管理重建任务 | 分页导出可索引课程安全快照 |
| Content | `GET /internal/v1/content/export` | Search 管理重建任务 | 分页导出可索引内容安全快照 |
| User | `POST /internal/v1/service-tokens` | 已注册服务客户端 | 签发短期、指定受众和 scope 的服务 Token |
| 各权威服务 | `GET /internal/v1/rebuild-snapshots/{resource}` | Analytics/Recommendation 重建任务 | 游标分页导出当前快照和每行聚合版本 |
| 各事件源服务 | `GET /internal/v1/events/watermark` | 受授权重建任务 | 返回 `outbox_sequence` 最后已提交的 `committedHighWatermark` |
| 各事件源服务 | `GET /internal/v1/events/export?afterExclusive={w1}&toInclusive={w2}&cursor={cursor}&pageSize={n}` | 受授权重建任务 | 按 `sourceSequence` 升序、有上界地导出 `(W1,W2]` 已发布 Outbox 归档 |

服务凭据交换采用明确协议：调用方通过生产 HTTPS 向 `POST /internal/v1/service-tokens` 发送 `Authorization: Basic base64(client_id:client_secret)`，表单体为 `grant_type=client_credentials&audience=<target>&scope=<space-separated>`；成功响应只含 `access_token`、`token_type=Bearer` 和 `expires_in`。凭据禁止放入 URL、日志或追踪标签。后续内部请求使用 `Authorization: Bearer <service-token>`。User 校验哈希、状态、允许受众/scope 后签发，目标服务再校验签名、`aud/scope/clientId/tokenVersion` 和接口 ACL。

单文件下载授权请求为 `{subjectType,subjectUserId,ownerType,ownerId,purpose,requestedTtlSeconds}`；`subjectType=USER` 时 `subjectUserId` 必填，只有已登记的公开展示 purpose 才允许 `ANONYMOUS`。`ownerService` 不接收客户端输入，由 File 将已认证 `clientId` 映射为固定值。批量请求为 `{subjectType,subjectUserId,purpose,requestedTtlSeconds,items[{requestKey,fileId,ownerType,ownerId}]}`，`items` 去重且最多 100 个；响应为 `{items[{requestKey,fileId,status,url,expiresAt}]}`。只有精确绑定且文件可用的项返回 `GRANTED`；已正确绑定但对象暂不可用返回 `UNAVAILABLE`，任一伪造/错配的 owner、越权 purpose 或超限请求使整批失败并写安全审计。TTL 受服务端更小上限约束，URL 到期后存储层必须拒绝，grant 不产生可重复使用的公开 capability。

领域服务只能对已经通过本地业务授权的资源申请 grant：User 校验本人或用户管理范围，Course 校验公开课程/教师归属，Content 校验有效 Enrollment、教师归属或免费预览，Live 校验直播课程访问。列表/详情 DTO 中的地址仅在响应组装期生成，不落业务库、不进入事件、缓存或日志；批量客户端必须在一次有界调用中处理当前页，禁止循环调用单文件接口。

投影校准响应统一包含 `aggregateType/aggregateId/aggregateVersion/snapshot`。消费者在本地事务用快照替换单聚合投影并把最后版本设为响应版本；已排队的 `<= snapshotVersion` 事件幂等忽略，下一条必须为 `snapshotVersion+1`。快照接口只允许表中列明的 `clientId/aud/scope`，每次调用记录指标和安全审计；不得扩展为任意表查询。

内部接口设置超时和分页上限，只返回调用方所需快照，不能演变为跨服务通用数据库查询层。每个接口在 OpenAPI 中列出允许的 `clientId`、`aud` 和 scope；仅处于内网不是授权。开发 Compose 可在隔离网络使用专用开发凭据和 HTTP，任何非本地环境必须拒绝明文凭据交换。

水位响应为 `{sourceService, committedHighWatermark, capturedAt}`。归档导出响应为 `{items, afterExclusive, toInclusive, nextCursor, complete}`，参数固定后游标不可跨水位复用；服务端验证 `afterExclusive <= toInclusive <= committedHighWatermark`，每项包含完整事件信封和 `sourceSequence`。

重建快照采用不丢变更的协议：重建任务在取得 `W1` 前为每个来源预创建 durable 重建队列；该队列必须绑定来源服务全部事件，不关心的聚合也 no-op，才能声明连续 `sourceSequence`。随后分页导出带 `aggregateVersion` 的当前快照；快照结束后取得 `W2`，有界重放 `(W1, W2]` 归档。快照期间已经包含的较新状态由聚合版本去重，快照读取之后发生的变更由归档补齐。随后将 durable 队列中重复事件按 `eventId/aggregateVersion` 去重，继续把 `W2` 后事件应用到隔离目标；当目标追到新的已提交水位且队列无更早未确认消息时原子切换读别名/视图，原消费者继续处理，不在切换时重新建队列。`sourceSequence` 必须由来源数据库提交顺序序列分配，不能用应用时间或普通 Snowflake ID 充当水位。失败任务持久化每来源 W1/W2、快照/归档游标、durable 队列名和暂存目标后恢复；每页使用不透明 `nextCursor`，禁止 offset 扫描大表。

## 15. 前端适配策略

### 15.1 保留页面，替换数据源

- 优先在三套门户现有 `services/api.ts` 或功能适配器中替换 Mock 实现。
- 页面继续使用当前领域类型；适配层负责统一响应解包、ID、金额、时间和状态转换。
- 不在页面组件中散落 Gateway URL、Token 刷新或错误码判断。

### 15.2 Axios 处理

- 每个门户建立唯一 API Client。
- 请求拦截器添加 Access Token 和 `X-Request-Id`。
- 遇到 401 时使用单次刷新锁，避免并发刷新风暴。
- 刷新成功后只重试一次；失败则清理内存状态并跳转登录。
- 非幂等写请求不做无条件自动重试。

### 15.3 UI 状态

- 明确区分加载、空数据、字段校验、无权限、冲突、服务不可用和未知错误。
- 保存按钮在提交中禁用，防止重复点击，但服务端幂等仍是最终保证。
- 403 不显示为“数据不存在”；支付和考试冲突需要展示服务端当前状态。

### 15.4 开发代理

三套 Vite 应用将 `/api` 和 `/ws` 代理至 Gateway `8080`。生产环境由同一入口域名或经过审核的精确跨域白名单访问，不能使用携带凭据的 `*` CORS。

## 16. OpenAPI 与契约变更

- 每个服务维护自己的 OpenAPI，Gateway 或文档入口统一展示。
- 请求 DTO 使用 Bean Validation，并在 OpenAPI 中声明必填、长度、范围和枚举。
- 破坏性 REST 变更提升 URL 主版本；同一 `v1` 内只做向后兼容扩展。
- 前后端联调前冻结本期字段和错误码，并更新追踪矩阵。
- 接口实现、OpenAPI、测试和前端类型必须在同一任务中同步更新。
