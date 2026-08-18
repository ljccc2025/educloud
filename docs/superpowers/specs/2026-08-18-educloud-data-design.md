# EduCloud 数据库与数据设计

> 状态：`【目标设计】`
>
> 数据库：MySQL 8.0.36；搜索索引：Elasticsearch 8.14；对象存储：MinIO；缓存：Redis 7.2。

## 1. 数据库拓扑

除 Gateway 外，每个服务拥有独立逻辑数据库：

```text
educloud_user             educloud_course
educloud_content          educloud_order
educloud_payment          educloud_live
educloud_file             educloud_notification
educloud_analytics        educloud_search
educloud_recommendation
```

本地开发允许这些数据库位于同一 MySQL 实例。生产账号只授予本服务数据库权限。禁止跨库 JOIN、跨库外键和共享 Mapper。

## 2. 通用建模规范

- 表名、字段名使用小写蛇形命名。
- 存储引擎使用 InnoDB，字符集使用 `utf8mb4`。
- 主键使用 `BIGINT` 和 MyBatis-Plus `ASSIGN_ID`，由后端生成；JSON 中以字符串输出，避免 JavaScript 精度损失。
- `requestId`、`eventId` 等跨服务技术标识使用 JDK 可生成的 UUID 字符串，不为示例格式额外引入 ID 库。
- 时间使用 `DATETIME(3)` 并按 UTC 存储；API 使用 ISO 8601。
- 金额使用 `DECIMAL(12,2)`，币种字段使用 ISO 4217 代码，当前默认为 `CNY`。
- 状态使用可演进的 `VARCHAR`，不使用 MySQL `ENUM`。
- 可编辑聚合包含 `version INT` 乐观锁字段。
- 允许恢复的普通数据可使用 `is_deleted`；订单、支付、答卷和审计事实不能以软删除掩盖历史。
- JSON 只保存审核快照、试卷快照等结构化历史，不替代可查询的核心关系表。
- 所有唯一性规则必须由数据库唯一索引做最终保护。

### 2.1 `ASSIGN_ID` Worker 分配

- 11 个业务服务使用固定 `datacenterId`：User 至 Recommendation 依次为 0 至 10；该映射写入集中配置并禁止同环境重复。
- 每个实例启动时以 Pod UID/实例 UUID 为持有者，在 Redis 的 `id-worker:{environment}:{service}:{workerId}` 键上通过 `SET NX` 租用 0 至 31 的 Worker 槽位。
- 租约按 TTL 的三分之一续约；启动时无法取得租约则拒绝就绪，租约丢失后立即停止生成新 ID并退出就绪状态。
- 本地 Compose 使用显式实例 UUID 执行相同租约协议，不手工让两个实例共用 Worker ID。
- 时钟回拨超过生成器容忍范围时拒绝生成 ID 并告警，不生成可能碰撞的值。
- 测试同时启动 32 个实例验证无碰撞，第 33 个实例必须安全失败；同时覆盖续约、租约丢失和时钟回拨。

Redis 在这里是 ID 分配协调器，不是业务事实来源；已生成的 ID 不依赖 Redis 查询。

通用审计字段按需要使用：

```text
created_by BIGINT NULL
created_at DATETIME(3) NOT NULL
updated_by BIGINT NULL
updated_at DATETIME(3) NOT NULL
version INT NOT NULL DEFAULT 0
```

## 3. User 数据库

### `sys_user`

| 字段 | 说明 |
|---|---|
| `id` | 用户 ID |
| `username` | 登录名，唯一 |
| `email`、`phone` | 可空；存在时唯一并按安全规范保护 |
| `password_hash` | 密码哈希，只写不返回 |
| `user_type` | `STUDENT/TEACHER/ADMIN` 等主体类别 |
| `status` | `ACTIVE/LOCKED/DISABLED` |
| `token_version` | 会话敏感变更时递增，用于在线撤销 |
| `email_verified` | 邮箱验证状态 |
| `failed_login_count`、`locked_until` | 登录保护 |
| `last_login_at` | 最近成功登录时间 |
| `version` | User 聚合乐观锁和 `UserStatusChanged` 连续事件版本 |

索引：`uk_user_username`、非空邮箱唯一、非空手机号唯一、`idx_user_type_status`。

### `user_profile`

字段：`user_id`、`display_name`、`avatar_file_id`、`bio`、`locale`、审计字段。`user_id` 唯一并关联本库 `sys_user`。

### `sys_role`

字段：`id`、`code`、`name`、`description`、`status`、`built_in`、审计字段。`code` 唯一。

### `sys_permission`

字段：`id`、`code`、`name`、`resource`、`action`、`description`。`code` 唯一。

### `sys_user_role`

字段：`id`、`user_id`、`role_id`、`assigned_by`、`assigned_at`。唯一索引 `(user_id, role_id)`。

### `sys_role_permission`

字段：`id`、`role_id`、`permission_id`。唯一索引 `(role_id, permission_id)`。

### `refresh_session`

每个 Refresh Token 保存一行，字段：`id`、`family_id`、`token_id`、`parent_token_id`、`replaced_by_token_id`、`user_id`、`session_token_hash`、`status`、`client_type`、`client_fingerprint_hash`、`issued_at`、`consumed_at`、`expires_at`、`revoked_at`、`revoke_reason`。只保存 Token 哈希；`family_id` 对应 Access Token 的 `sid`。

索引：唯一 `token_id`、唯一 `session_token_hash`、`(family_id, status)`、`(user_id, expires_at)`。状态至少包含 `ACTIVE/ROTATED/REVOKED/EXPIRED`。轮换事务对当前 Token 行加锁，原子标记 `ROTATED` 并插入子 Token；已消费 Token 在并发宽限窗口外再次出现时撤销整个 `family_id`。

### `service_client`

字段：`id`、`client_id`、`status`、`allowed_audiences_json`、`allowed_scopes_json`、`token_version`、审计字段。`client_id` 唯一；递增 `token_version` 可立即撤销该客户端已签发的全部服务 Token。

### `service_client_credential`

字段：`id`、`service_client_id`、`credential_version`、`secret_hash`、`status`、`not_before`、`expires_at`、`revoked_at`、审计字段。唯一索引 `(service_client_id, credential_version)`；原始 Secret 只在创建/轮换时通过受控渠道提供一次，不存明文。轮换先创建新 `ACTIVE` 凭据，将旧凭据置为短期 `GRACE`，宽限结束后置为 `REVOKED`；服务层和定时任务保证每客户端最多一个 `ACTIVE` 和一个 `GRACE`，并用客户端行锁串行化轮换。

### `platform_public_config`

字段：`id`、`config_key`、`config_value`、`value_type`、`description`、`version`、审计字段。只允许站点名称、Logo、备案号等非敏感配置；`config_key` 唯一。

### `login_audit`

字段：`id`、`user_id`、`login_name_masked`、`result`、`failure_code`、`ip`、`user_agent`、`request_id`、`occurred_at`。按用户和时间建立索引。

## 4. Course 数据库

### `course_category`

字段：`id`、`parent_id`、`name`、`slug`、`sort_order`、`status`、审计字段。`slug` 唯一；`parent_id` 为本库自关联。

### `course`

字段：`id`、`owner_teacher_id`、`lifecycle_status`、`published_version_id`、`draft_version_id`、`published_at`、`rating_avg`、`rating_count`、`enrollment_count`、审计和乐观锁字段。公开读取只跟随 `published_version_id`；教师编辑只操作 `draft_version_id`。

索引：`idx_course_owner_status`、`idx_course_published_at`。`rating_avg` 等为服务内维护的展示汇总，评价表仍是事实来源。

### `course_version`

字段：`id`、`course_id`、`version_no`、`category_id`、`title`、`subtitle`、`description`、`cover_file_id`、`level`、`price`、`currency`、`version_status`、`content_hash`、`created_by`、`created_at`。唯一索引 `(course_id, version_no)`；状态为 `DRAFT/PENDING_REVIEW/REJECTED/PUBLISHED/SUPERSEDED/WITHDRAWN`。版本提交审核后不可原地修改，审核通过与发布在同一本地事务完成，不保留无业务语义的中间 `APPROVED` 状态。

### `course_teacher`

字段：`id`、`course_id`、`teacher_id`、`teacher_role`、`joined_at`。唯一索引 `(course_id, teacher_id)`。

### `course_audit_submission`

字段：`id`、`course_id`、`course_version_id`、`status`、`submitted_by`、`submitted_at`、`withdrawn_at`、`reviewed_by`、`reviewed_at`、`reason`。`course_version_id` 唯一；状态为 `PENDING/APPROVED/REJECTED/WITHDRAWN`。审批时原子切换 `course.published_version_id`，旧发布版本变为 `SUPERSEDED`。

### `course_enrollment`

字段：`id`、`course_id`、`student_id`、`source`、`source_order_id`、`status`、`enrolled_at`、`access_ended_at`、`revoke_reason`、`version`。唯一索引 `(course_id, student_id)`；索引 `(student_id, status)`。`EnrollmentCreated/EnrollmentRevoked` 使用 `aggregateType=Enrollment`、`aggregateId=course_enrollment.id` 和该行递增 `version`。

### `course_content_readiness_projection`

字段：`id`、`course_id`、`content_root_id`、`published_revision_id`、`ready`、`source_event_id`、`last_aggregate_version`、`updated_at`。`course_id` 和 `source_event_id` 唯一。Course 消费 `CourseContent` 聚合完整事件流：`ContentRevisionPublished` 更新就绪事实，其他事件信封作为 no-op 只推进版本；缺失/有版本间隙时课程首次提交与审批失败关闭。

### `course_review`

字段：`id`、`course_id`、`student_id`、`rating`、`content`、`status`、审计字段。`rating` 范围 1 至 5；唯一索引 `(course_id, student_id)`。

## 5. Content 数据库

### `course_content`

字段：`id`、`course_id`、`published_revision_id`、`draft_revision_id`、`lifecycle_status`、`version`、审计字段。`course_id` 唯一；公开目录只读取发布修订，教师编辑只写草稿修订。

### `content_revision`

字段：`id`、`course_content_id`、`course_id`、`revision_no`、`revision_status`、`content_hash`、`created_by`、`created_at`。唯一索引 `(course_id, revision_no)`；状态为 `DRAFT/PENDING_REVIEW/REJECTED/PUBLISHED/SUPERSEDED/WITHDRAWN`。提交后修订及其章节、课件不可修改。

### `course_access_projection`

字段：`id`、`course_id`、`student_id`、`enrollment_id`、`access_status`、`source_event_id`、`last_aggregate_version`、`updated_at`。唯一索引 `(course_id, student_id)`，`source_event_id` 唯一。该表是由 Course 选课事件维护的访问投影，不是选课权威事实；只应用连续且更高的聚合版本。教师课程归属通过受认证的 Course 内部权限查询确认。

### `course_chapter`

字段：`id`、`content_revision_id`、`course_id`、`title`、`description`、`sort_order`、`status`、审计字段。唯一索引 `(content_revision_id, sort_order)`。

### `courseware`

字段：`id`、`content_revision_id`、`course_id`、`chapter_id`、`title`、`courseware_type`、`file_id`、`external_url`、`duration_seconds`、`size_bytes`、`free_preview`、`sort_order`、`status`、审计字段。文件和外链二者按类型互斥；唯一索引 `(chapter_id, sort_order)`。

### `learning_progress`

字段：`id`、`student_id`、`course_id`、`courseware_id`、`position_seconds`、`watched_seconds`、`completed`、`completed_at`、`last_learned_at`、`version`。唯一索引 `(student_id, courseware_id)`；索引 `(student_id, course_id)`。

### `assignment`

字段：`id`、`course_id`、`title`、`description`、`total_score`、`due_at`、`allow_late_submission`、`max_attempts`、`status`、`published_at`、审计字段。状态为 `DRAFT/PUBLISHED/CLOSED`。

### `assignment_submission`

字段：`id`、`assignment_id`、`student_id`、`attempt_no`、`content`、`submitted_at`、`late`、`status`、`score`、`feedback`、`graded_by`、`graded_at`、`version`。唯一索引 `(assignment_id, student_id, attempt_no)`；`score` 由服务校验范围。

### `assignment_submission_attachment`

字段：`id`、`submission_id`、`file_id`、`sort_order`、`created_at`。唯一索引 `(submission_id, file_id)`；File 绑定和解除绑定逐行处理，不使用 JSON 数组维护核心关系。

### `exam`

字段：`id`、`course_id`、`title`、`description`、`total_score`、`duration_minutes`、`start_at`、`end_at`、`attempt_limit`、`status`、`result_publish_mode`、审计字段。状态为 `DRAFT/PUBLISHED/ONGOING/ENDED`。

### `exam_question`

字段：`id`、`exam_id`、`question_type`、`stem`、`analysis`、`score`、`sort_order`、`correct_answer_json`、审计字段。主观题答案字段可空且不对考生接口暴露。

### `exam_question_option`

字段：`id`、`question_id`、`option_key`、`content`、`sort_order`。唯一索引 `(question_id, option_key)`。

### `exam_attempt`

字段：`id`、`exam_id`、`student_id`、`attempt_no`、`paper_snapshot_json`、`started_at`、`deadline_at`、`submitted_at`、`status`、`objective_score`、`subjective_score`、`total_score`、`graded_at`、`version`。唯一索引 `(exam_id, student_id, attempt_no)`。

### `exam_answer`

字段：`id`、`attempt_id`、`question_id`、`answer_json`、`answered_at`、`auto_score`、`manual_score`、`grader_feedback`、`graded_by`。唯一索引 `(attempt_id, question_id)`。

### `community_post`

字段：`id`、`author_id`、`course_id`、`title`、`content`、`status`、`comment_count`、`like_count`、`bookmark_count`、审计字段。状态为 `PUBLISHED/HIDDEN/DELETED`。

### `community_comment`

字段：`id`、`post_id`、`parent_comment_id`、`author_id`、`content`、`status`、`like_count`、审计字段。

### `community_reaction`

字段：`id`、`user_id`、`target_type`、`target_id`、`reaction_type`、`created_at`。唯一索引 `(user_id, target_type, target_id, reaction_type)`；类型至少包含 `LIKE/BOOKMARK`。

### `content_audit_submission`

字段：`id`、`target_type`、`target_id`、`content_revision_id`、`target_version`、`snapshot_json`、`status`、`submitted_by`、`reviewed_by`、`submitted_at`、`withdrawn_at`、`reviewed_at`、`reason`。课程目录审核必须引用不可变 `content_revision_id`；社区等单对象审核使用 `target_version` 和快照。拒绝时 `reason` 必填。

## 6. Order 数据库

### `cart_item`

字段：`id`、`student_id`、`course_id`、`selected`、`created_at`、`updated_at`。唯一索引 `(student_id, course_id)`。

### `trade_order`

字段：`id`、`order_no`、`student_id`、`status`、`original_amount`、`payable_amount`、`currency`、`expires_at`、`paid_at`、`cancelled_at`、`idempotency_key_hash`、审计和乐观锁字段。首期不支持优惠券，`original_amount` 与 `payable_amount` 应相等。

索引：唯一 `order_no`、唯一 `(student_id, idempotency_key_hash)`、`idx_order_student_status`、`idx_order_created_at`。

### `trade_order_item`

字段：`id`、`order_id`、`course_id`、`course_title_snapshot`、`cover_file_id_snapshot`、`unit_price`、`quantity`、`line_amount`、`refund_reserved_amount`、`refunded_amount`、`fulfillment_status`。当前课程商品 `quantity` 固定为 1；唯一索引 `(order_id, course_id)`。创建退款申请时锁定订单项并原子增加预留额，始终满足 `refund_reserved_amount + refunded_amount <= line_amount`；驳回/取消释放预留，成功时预留转为已退款。

### `refund_request`

字段：`id`、`refund_no`、`order_id`、`student_id`、`requested_amount`、`reason`、`status`、`reviewed_by`、`review_reason`、`requested_at`、`reviewed_at`、`completed_at`、`version`。`refund_no` 唯一。

### `refund_request_item`

字段：`id`、`refund_request_id`、`order_item_id`、`course_id`、`requested_amount`、`approved_amount`。唯一索引 `(refund_request_id, order_item_id)`；同一订单项累计已退款金额不得超过其成交金额。

## 7. Payment 数据库

### `payment_order`

字段：`id`、`payment_no`、`order_id`、`order_no_snapshot`、`student_id`、`amount`、`currency`、`status`、`expires_at`、`succeeded_at`、`version`。唯一 `payment_no`，并对 `order_id` 建唯一索引：一个业务订单只有一个支付聚合。

### `payment_order_item_snapshot`

字段：`id`、`payment_id`、`order_item_id`、`course_id`、`amount`。唯一 `(payment_id, order_item_id)`；各项金额之和必须等于支付聚合金额。该快照来自 Order 可支付摘要，只用于渠道退款分摊和对账，不成为订单商品权威来源。

### `payment_attempt`

字段：`id`、`payment_id`、`attempt_no`、`channel`、`channel_trade_no`、`active_slot`、`status`、`provider_created_at`、`expires_at`、`succeeded_at`、`failure_code`。唯一 `(payment_id, attempt_no)` 和非空 `(channel, channel_trade_no)`；唯一 `(payment_id, active_slot)`，活动尝试使用 `active_slot=1`，终态置为 `NULL`，利用 MySQL 多个 NULL 允许多个历史尝试但最多一个活动尝试。

### `payment_callback_record`

字段：`id`、`payment_attempt_id`、`channel`、`channel_notification_id`、`channel_trade_no`、`payload_hash`、`signature_valid`、`provider_paid_at`、`process_status`、`error_code`、`received_at`、`processed_at`。唯一 `(channel, channel_notification_id)`。

### `payment_refund`

字段：`id`、`refund_no`、`refund_request_id`、`payment_id`、`payment_attempt_id`、`order_id`、`channel`、`amount`、`refund_type`、`reason`、`dedup_key`、`status`、`channel_refund_no`、`requested_at`、`completed_at`、`version`。`refund_request_id` 仅用户退款使用；`refund_type` 包含用户退款、晚到支付自动退款、重复扣款自动退款和已取消订单竞态自动退款。`refund_no`、非空 `dedup_key`、非空 `(channel, channel_refund_no)` 唯一。稳定去重键分别使用 `user:{refundRequestId}`、`late:{channel}:{channelTradeNo}`、`duplicate:{channel}:{channelTradeNo}`、`cancel-race:{orderId}`，由服务端生成。

### `payment_refund_item`

字段：`id`、`payment_refund_id`、`order_item_id`、`course_id`、`amount`。唯一 `(payment_refund_id, order_item_id)`；各项金额之和必须等于退款主记录金额。分摊来自 `payment_order_item_snapshot` 或 Order 的明确部分退款/补偿范围，Payment 关系化保存并在 `RefundSucceeded` 原样返回，不能只保存无法对账到具体课程的总额。

### `reconciliation_record`

字段：`id`、`batch_no`、`channel`、`business_date`、`payment_no`、`expected_amount`、`actual_amount`、`difference_type`、`status`、`resolved_by`、`resolved_at`。索引 `(channel, business_date, status)`。

## 8. Live 数据库

### `live_room`

字段：`id`、`course_id`、`teacher_id`、`title`、`description`、`scheduled_start_at`、`scheduled_end_at`、`status`、`provider_room_ref`、`version`、审计字段。

### `live_session`

字段：`id`、`room_id`、`started_at`、`ended_at`、`started_by`、`ended_by`、`peak_viewers`、`status`。一个直播间允许多个历史场次，但同一时刻只能有一个 `LIVING` 场次。

### `live_message`

字段：`id`、`session_id`、`sender_id`、`message_type`、`content`、`status`、`sent_at`、`recalled_at`。索引 `(session_id, sent_at)`。

### `live_attendance`

字段：`id`、`session_id`、`student_id`、`joined_at`、`left_at`、`watched_seconds`。可按多段进入记录；索引 `(session_id, student_id)`。

### `live_replay`

字段：`id`、`session_id`、`file_id`、`status`、`duration_seconds`、`available_at`、`failure_reason`。状态为 `PENDING/PROCESSING/AVAILABLE/FAILED`。

## 9. File 数据库

### `file_upload_session`

字段：`id`、`uploader_id`、`object_key`、`expected_size`、`expected_content_type`、`status`、`expires_at`、`completed_at`。对象键唯一。

### `file_object`

字段：`id`、`object_key`、`original_name`、`content_type`、`size_bytes`、`sha256`、`bucket`、`status`、`uploader_id`、`uploaded_at`、`deleted_at`、`version`。唯一 `object_key`；按哈希和状态建立索引。`FileUploaded/FileBound/FileUnbound/FileDeleted` 均使用 `aggregateType=FileObject`、`aggregateId=file_object.id` 和根记录递增版本。

### `file_binding`

字段：`id`、`file_id`、`owner_service`、`owner_type`、`owner_id`、`bound_at`、`unbound_at`。一个业务引用唯一 `(file_id, owner_service, owner_type, owner_id)`。绑定、解绑和删除在事务内先锁 `file_object` 根并递增根版本，防止旧 `FileBound` 在 `FileDeleted` 后复活投影。

### `file_access_audit`

字段：`id`、`file_id`、`user_id`、`action`、`result`、`ip`、`request_id`、`occurred_at`。敏感或受保护文件访问才需持久化完整审计。

## 10. Notification 数据库

### `notification_template`

字段：`id`、`code`、`channel`、`title_template`、`body_template`、`status`、`version`、审计字段。唯一 `(code, channel, version)`。

### `notification`

字段：`id`、`source_event_id`、`business_type`、`business_id`、`title`、`content`、`action_url`、`created_at`。相同来源事件和业务通知类型保持幂等。

### `user_notification`

字段：`id`、`notification_id`、`user_id`、`read_at`、`deleted_at`、`created_at`。唯一 `(notification_id, user_id)`；索引 `(user_id, read_at, created_at)`。

### `delivery_task`

字段：`id`、`notification_id`、`user_id`、`channel`、`recipient_masked`、`status`、`attempt_count`、`next_attempt_at`、`last_error_code`、`delivered_at`。索引 `(status, next_attempt_at)`。

## 11. Analytics 数据库

### 主要表

- `analytics_event_inbox`：事件 ID、类型、来源、契约版本、聚合版本、消费状态和时间，事件 ID 唯一。
- `course_metric_daily`：日期、课程、浏览、选课、完成、作业和考试指标，唯一 `(metric_date, course_id)`。
- `teacher_metric_daily`：日期、教师、课程数、学生数、批改量等，唯一 `(metric_date, teacher_id)`。
- `platform_metric_daily`：日期、注册、活跃、课程、订单和支付汇总，日期唯一。
- `finance_metric_daily`：日期、支付、退款、净额和渠道维度，唯一 `(metric_date, channel)`。
- `audit_event_read_model`：来源事件 ID、服务、操作者、动作、资源、结果、原因、IP、追踪 ID和时间；来源事件 ID 唯一。
- `analytics_rebuild_job`：重建范围、来源服务、快照截止时间、游标、事件水位、状态、校验摘要和错误信息。

这些表都是派生视图，必须支持按事件重新计算或重建。

## 12. Search 数据与索引

MySQL 表：

- `search_event_inbox`：去重和消费水位。
- `index_task`：目标类型、目标 ID、操作、状态、重试次数、下次执行时间和错误码。

Elasticsearch 索引：

### `educloud-course-v1`

字段至少包含课程 ID、标题、摘要、分类、难度、价格、评分、教师展示名、发布时间、可见状态和更新时间。只索引已发布课程。

### `educloud-content-v1`

字段至少包含内容 ID、课程 ID、内容类型、标题、可检索文本、可见状态和更新时间。受保护课件只返回安全摘要，不在搜索结果泄露文件地址。

索引使用版本别名切换，重建新版本后原子替换别名。

## 13. Recommendation 数据库

- `recommendation_rule_config`：规则代码、权重、适用范围、状态和版本。
- `recommendation_snapshot`：用户、批次、课程 ID、排名、分数、理由、规则版本和过期时间。
- `recommendation_feedback`：用户、课程、反馈类型、来源位置和时间。
- `recommendation_event_inbox`：领域事件消费去重。
- `recommendation_rebuild_job`：来源快照游标、事件水位、状态和校验摘要。

首期只保存可解释规则结果。模型向量、提示词或供应商会话不属于当前表设计。

## 14. 事件与幂等通用表

下列结构不是只读示例：每个服务目录必须包含受 checksum 管理的 `V000__technical_tables.sql`，由迁移 runner 在任何领域 V001 前执行。`deploy/sql/common` 模板只用于保持结构一致，不能代替 11 个逻辑库中的实际迁移文件；空库验收逐库核对表、索引和账号权限。

需要发布事件的服务包含 `outbox_event`：

```text
id, event_id, aggregate_type, aggregate_id, event_type,
event_version, aggregate_version, payload_json, request_id, trace_id, occurred_at,
source_sequence, publish_status, attempt_count, next_attempt_at, published_at, archived_at
```

`event_id` 和单服务单调 `source_sequence` 唯一，待发布索引为 `(publish_status, next_attempt_at)`。每个来源库另有单行 `outbox_sequence(source_name, last_value)`；产生事件的业务事务锁定并递增该行后写入 Outbox，锁一直持有到提交，因此后一个水位不能先于前一个水位提交。不得以应用时间、普通 Snowflake ID 或数据库自增预分配但可乱序提交的值充当重建水位。发布成功记录保留为事件归档，至少保留到所有已登记消费者越过该水位和最长重建窗口；具体保留期上线前确认。

消费事件的服务包含 `inbox_event` 或领域专用 inbox：

```text
id, event_id, event_type, source_service, event_version,
source_sequence, aggregate_type, aggregate_id, aggregate_version,
process_status, business_effect, received_at, processed_at, error_code
```

`event_id` 唯一。`business_effect` 记录 `APPLIED/NO_OP/IGNORED_OLD`。投影队列必须收到其依赖聚合类型的完整事件信封；相关事件更新业务投影，无关事件不解析数据但以 `NO_OP` 推进版本。投影表保存每个聚合最后应用的 `aggregate_version`；旧版本事件直接忽略，发现版本间隙则暂停该聚合、重试并触发快照校准。业务更新或 no-op 水位推进与 Inbox 成功状态必须处于同一个本地事务。

只有宣称来源级 W1/W2 水位的派生/重建消费者才维护 `consumer_watermark(consumer_name, source_service, last_source_sequence, updated_at)`，唯一 `(consumer_name, source_service)`。这类队列必须绑定该 `source_service` 的全部事件，而不仅是某个 `aggregateType`；不关心的聚合类型同样写 Inbox `NO_OP`。只有下一连续 `source_sequence` 成功应用、no-op 或确认旧事件后才能推进，不能以“见过最大值”跳过缺口。普通 Course/Content 等事务投影只维护所依赖聚合类型的 `aggregateVersion`，不宣称全来源水位。

### 派生服务重建协议

Analytics、Search 和 Recommendation 不能把 RabbitMQ 当作永久事件日志。其重建队列对每个登记来源绑定全部事件，非业务相关聚合也 no-op 推进来源水位。重建前预绑定 durable 队列并记录已提交 `W1`，分页导出带聚合版本的当前权威快照，快照结束后记录 `W2`，再有界消费 `(W1, W2]` Outbox 归档并接续 durable 队列中的实时事件。快照已包含的较新聚合由版本去重，快照读取后的变化由归档补齐。重建完成前写入隔离的临时表/索引；追到新的已提交水位且队列无更早未确认消息后，完成行数、金额、最大版本、水位连续性和失败记录校验，再原子切换读流量。失败任务按来源保留 W1/W2、快照/归档游标、durable 队列名、暂存目标和错误，可从安全检查点继续或重新开始。

### `audit_event`

每个产生业务事实的服务数据库都包含只追加来源审计表：`id`、`audit_id`、`actor_type`、`actor_id`、`actor_roles_json`、`action`、`resource_type`、`resource_id`、`result`、`reason`、`before_summary_json`、`after_summary_json`、`ip`、`user_agent`、`request_id`、`trace_id`、`occurred_at`、`retention_class`。`audit_id` 唯一；应用账号仅允许 INSERT/SELECT，不允许 UPDATE/DELETE。Analytics 消费 `AuditEventPublished` 构建查询视图，但来源表仍是权威证据。

需要 HTTP 幂等的服务包含 `idempotency_record`：

```text
id, user_id, operation, idempotency_key_hash, request_hash,
response_status, response_body_json, expires_at, created_at
```

唯一索引 `(user_id, operation, idempotency_key_hash)`。同一键但请求摘要不同应返回冲突。

## 15. 缓存规则

| 数据 | 建议缓存 | 失效方式 |
|---|---|---|
| 平台公开配置 | Redis | 配置更新后主动删除 |
| 课程详情和分类 | Redis | 课程/分类事件删除或刷新 |
| 用户权限摘要 | Redis | 角色或权限变更后删除 |
| 热门课程 | Redis | Analytics/Recommendation 定期刷新 |
| 验证码和限流计数 | Redis | TTL 自动失效 |
| Refresh 会话撤销 | Redis 加数据库会话事实 | 登出、改密、禁用立即更新 |

订单、支付、作业成绩和考试答卷不得只存在 Redis。缓存键必须包含业务版本前缀，避免升级时读取旧结构。

## 16. 数据保留与清理

- 未绑定上传文件在可配置保留期后清理，清理前再次检查绑定关系。
- 过期购物车和幂等记录可按保留策略归档或删除。
- 支付、退款、考试、成绩和审计保留期由法规与项目方确认，未确认前不自动物理删除。
- Elasticsearch 派生索引可以重建，不作为备份替代品。
- 首期不提供用户删除/匿名化执行能力，只允许禁用/恢复。后续专项方案获批后，匿名化只能处理经数据依赖清单确认的字段，且不能破坏交易、成绩和审计引用。

## 17. 迁移规则

- 每个服务独立维护有序、不可修改的已发布 SQL 迁移文件。
- 采用扩展—迁移—收缩：先加兼容字段，再迁移数据和代码，最后删除旧字段。
- 大表增加索引和回填需评估锁表、批次和暂停策略。
- 每次发布验证空库升级、现有数据升级、重复执行保护和可执行回退/补偿。
- 数据库回退不可简单假设为反向执行 DDL；破坏性迁移必须有备份和恢复步骤。

### 17.1 迁移历史与并发保护

每个逻辑数据库包含 `schema_migration_history`：`version`、`description`、`script_name`、`checksum_sha256`、`status`、`installed_by`、`installed_at`、`execution_ms`、`error_summary`，`version` 和 `script_name` 唯一。

迁移执行器只依赖 MySQL Client 和仓库脚本：

1. 通过 MySQL `GET_LOCK('educloud_<service>_migration', timeout)` 获取单服务迁移锁。
2. 按 `VNNN__description.sql` 顺序读取未应用脚本并计算 SHA-256。
3. 已成功版本的 checksum 不一致时立即失败，禁止修改已发布脚本。
4. 成功后写 `SUCCESS`；DDL 部分执行失败时写 `FAILED` 并停止，后续运行必须先由受审计修复流程处理，不能假装事务已回滚。
5. Kubernetes 使用发布前 Migration Job，本地/CI 使用等价 PowerShell 脚本；应用实例启动时不并发执行 DDL。
