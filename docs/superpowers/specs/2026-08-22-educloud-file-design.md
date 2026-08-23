# EduCloud M04 File 模块设计规格

> 日期：2026-08-22
>
> 状态：设计草案（待用户审查）
>
> 模块：M04 `educloud-file`
>
> 交付类型：可独立运行的 Spring MVC 业务服务（文件元数据、上传会话、MinIO 对象映射与受控业务绑定）

## 1. 目的与前置条件

M04 交付文件生命周期及受控业务绑定：创建上传会话、浏览器直传 MinIO、确认上传完成、业务绑定/解绑、内部下载授权（单文件与有界批量）、删除与未绑定清理、存储状态与受限探测。完成后与 User 服务联调真实头像上传闭环。

前置条件（均已满足）：

- M01 `educloud-common`：统一响应、错误基类、requestId/traceId、EventEnvelope、Outbox 基础设施（`OutboxWriter`/`OutboxEventDispatcher` 复用 M03 模式）。
- M02 `educloud-gateway`：`RouteGroups` 已预留 `FILE` 组（`/api/v1/files/**`、`/api/v1/file-upload-sessions/**`）；网关消费侧会话契约（Redis session + JWT claims）已冻结。
- M03 `educloud-user`：服务令牌签发（`client_credentials`）、`InternalApiFilter`（aud + clientId 白名单）、`user_profile.avatar_file_id` 字段、Inbox 表均已就绪；User 侧头像 URL 组装契约明确为 M04 后接线。
- 共享依赖 Compose：VM 上 `educloud-minio-1`（API 9000 / console 9001）healthy，`MINIO_ROOT_USER/PASSWORD` 来自 `.env`；MySQL/Redis/RabbitMQ/Nacos 均 healthy。
- 2026-08-18 文档集（领域/数据/API/安全/可靠性/架构）与 2026-08-20 模块执行契约为批准基线。

## 2. 已比较方案与决定

| 方案 | 内容 | 决定 |
|---|---|---|
| 上传链路 | A. Presigned PUT 直传：File 创建会话返回短期 PUT 授权 URL，浏览器直传 MinIO，complete 时服务端 `statObject` + 下载计算 SHA-256 校验；B. 后端代理上传；C. 前端持 Access Key 直传 | **采用 A**：符合 2026-08-18 架构选型（带宽不经后端）；B 违背选型、C 密钥暴露浏览器 |
| 下载链路 | A. Presigned GET 短期授权：领域服务业务授权后以服务令牌调 File 内部 `download-grants`；B. 网关代理流式下载 | **采用 A**：私有 bucket 下存储层强制过期，知道 fileId 不等于有权限；B 长期占用服务连接 |
| 完整性校验 | complete 时 File 服务端下载对象计算 SHA-256（10MB 上限内可接受，限频+超时） | **采用**：防止会话与对象错配，与 `file_object.sha256` 对齐 |
| 对象键 | 服务端生成 `{bucket}/{ownerService}/{yyyyMMdd}/{uuid}.{ext}`，文件名不参与路径 | **采用**：客户端不可指定任意 MinIO 路径（安全文档 11 节） |
| Bucket 规划 | 单 bucket `educloud-files`，对象键前缀区分用途；配置可扩展多 bucket | **采用**：YAGNI，`file_object.bucket` 字段保留扩展位 |
| 未绑定清理 | 定时任务：保留期（默认 24h）后二次查绑定再删除对象与记录 | **采用**：数据设计 442 行 |
| MinIO SDK | minio-java 8.5.7 | **采用**：架构文档钦定 |
| 服务间认证 | User 以 `user-service` 客户端凭据向自己的 `ServiceTokenService.issue` 换取 aud=educloud-file 的服务令牌，File 以 `InternalApiFilter` 校验 | **采用**：复用 M03 模式，不做新认证体系 |

## 3. 范围与边界

### 3.1 交付范围（M04 内）

- 上传会话生命周期：创建（返回 presigned PUT）、浏览器直传、确认完成（校验存在性/大小/类型/SHA-256）、过期清理。
- 文件对象：`file_object` 权威记录（状态 UPLOADING/AVAILABLE/QUARANTINED/DELETED，`version` 乐观锁）。
- 业务绑定：内部 bind/unbind 接口（ownerService 由已认证 clientId 推导），`FileBound`/`FileUnbound` 事件。
- 下载授权：内部单文件与有界批量 grant（≤100），purpose 白名单，TTL 服务端上限；不提供凭 fileId 的公共下载。
- 删除：内部删除接口（先查绑定；未绑定/已解绑才可删），`FileDeleted` 事件。
- 存储状态：`storage-status`（脱敏）与 `storage-tests`（限频、可审计、最小读写探测、无密钥）。
- 前端头像闭环：学生端资料页头像上传；三门户展示真实 `avatarUrl`（User `/me` 经批量 grant 组装短期地址）。
- User 侧配合改造：`updateProfile` 绑定 fileId 时调 File bind；`/me`、用户详情/分页 DTO 组装 `avatarUrl`（不落库、不 N+1）；订阅 `FileDeleted` 清理头像引用。

### 3.2 不交付（后续模块）

- 课程封面（M05 Course）、课件/作业/考试附件（M06 Content）接入：仅保证内部 grant/bind 接口契约冻结。
- 病毒扫描/内容审核（QUARANTINED 状态预留，机制 M06+ 评审）。
- 文件版本管理、秒传/分片断点续传（YAGNI）。

## 4. 架构总览

```text
educloud-file（Spring MVC，端口 8087，management 8088）
├── config/      FileProperties、MybatisPlusConfig（分页+乐观锁，复用 M04 前的审查修复模式）
├── controller/  对外：FileUploadSessionController、FileStorageController；内部：InternalFileController
├── dto/request dto/response
├── entity/ mapper/
├── service/     UploadSessionService、FileObjectService、FileBindingService、DownloadGrantService、
│               FileStorageService（MinIO 读写）、FileCleanupService
├── security/    SecurityConfiguration（Resource Server）、InternalApiFilter（服务令牌）、
│               JwtDecoderConfiguration（User 公钥 JWKS）、StorageTestRateLimitFilter
├── messaging/   RabbitConfiguration、OutboxWriter（复用 common）、FileEventPublisher
├── observability/ FileMetrics、FileDependenciesHealthIndicator（mysql/redis/rabbit/minio 就绪组）
├── support/     ObjectKeyFactory、ContentTypePolicy、GrantPurposePolicy、StorageStatus
└── exception/   FileErrorCode、FileExceptionHandler
```

依赖：M01 common；MinIO SDK 8.5.7；MyBatis-Plus；Spring Boot 3.2.5（web/security/oauth2-resource-server/data-redis/amqp/actuator/validation）。

## 5. 数据模型（`educloud_file` 逻辑库）

```sql
-- V000__technical_tables.sql：与各服务一致的 outbox/inbox/audit/幂等技术表（参照 M03 同构文件）

-- V001__file.sql
CREATE TABLE file_upload_session (
  id BIGINT PRIMARY KEY, uploader_id BIGINT NOT NULL, object_key VARCHAR(255) NOT NULL,
  bucket VARCHAR(64) NOT NULL, original_name VARCHAR(255) NOT NULL, content_type VARCHAR(128) NOT NULL,
  expected_size_bytes BIGINT NULL, status VARCHAR(16) NOT NULL, -- PENDING/COMPLETED/EXPIRED/ABORTED
  put_url_expires_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL, version INT NOT NULL,
  UNIQUE KEY uk_upload_session_object_key (object_key)
) ENGINE=InnoDB;

CREATE TABLE file_object (
  id BIGINT PRIMARY KEY, object_key VARCHAR(255) NOT NULL, original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL, size_bytes BIGINT NOT NULL, sha256 CHAR(64) NOT NULL,
  bucket VARCHAR(64) NOT NULL, status VARCHAR(16) NOT NULL, -- UPLOADING/AVAILABLE/QUARANTINED/DELETED
  uploader_id BIGINT NOT NULL, uploaded_at DATETIME(6) NOT NULL, deleted_at DATETIME(6) NULL,
  version INT NOT NULL, UNIQUE KEY uk_file_object_key (object_key),
  KEY idx_file_object_sha256_status (sha256, status)
) ENGINE=InnoDB;

CREATE TABLE file_binding (
  id BIGINT PRIMARY KEY, file_id BIGINT NOT NULL, owner_service VARCHAR(32) NOT NULL,
  owner_type VARCHAR(64) NOT NULL, owner_id VARCHAR(128) NOT NULL,
  bound_at DATETIME(6) NOT NULL, unbound_at DATETIME(6) NULL,
  UNIQUE KEY uk_file_binding (file_id, owner_service, owner_type, owner_id),
  KEY idx_file_binding_owner (owner_service, owner_type, owner_id)
) ENGINE=InnoDB;

CREATE TABLE file_access_audit (
  id BIGINT PRIMARY KEY, file_id BIGINT NOT NULL, user_id BIGINT NULL,
  action VARCHAR(32) NOT NULL, result VARCHAR(16) NOT NULL,
  ip VARCHAR(64) NULL, request_id VARCHAR(36) NOT NULL, occurred_at DATETIME(6) NOT NULL,
  KEY idx_file_access_audit_file (file_id, occurred_at)
) ENGINE=InnoDB;
```

要点：

- 绑定/解绑/删除在事务内先 `SELECT ... FOR UPDATE` 锁 `file_object` 根并递增 `version`，防止旧 `FileBound` 在 `FileDeleted` 后复活投影（数据设计 317 行）。
- `file_binding` 唯一键 `(file_id, owner_service, owner_type, owner_id)`；解绑用 `unbound_at` 软标记，历史绑定可审计。
- `file_access_audit` 记录敏感/受保护文件访问：GRANT_SINGLE、GRANT_BATCH_DENIED、DELETE、DELETE_FORCE、STORAGE_TEST。
- 数据库账号：`file_app`（业务读写）/`file_migration`（迁移，GRANT OPTION），init 脚本与 M03 同构。

## 6. API 契约

### 6.1 对外 API（经 Gateway，Bearer + 权限码）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/v1/file-upload-sessions` | `file:upload` | 创建上传会话：`{originalName?, contentType, expectedSizeBytes?}` → `{sessionId, uploadUrl, expiresInSeconds}`；presigned PUT 有效期默认 5 分钟 |
| POST | `/api/v1/file-upload-sessions/{id}/complete` | `file:upload` | 确认完成：服务端 `statObject` 校验存在与大小，下载对象计算 SHA-256，白名单校验类型；成功 → `{fileId, objectKey, sizeBytes, sha256}`，对象置 AVAILABLE，发 `FileUploaded` |
| GET | `/api/v1/files/storage-status` | `file:storage:status:read` | `{provider: MINIO, connected, endpointMasked, checkedAt, lastErrorCategory}`，不返回密钥 |
| POST | `/api/v1/files/storage-tests` | `file:storage:test` | 限频（默认 1 次/分钟/用户）的最小读写探测：put 临时对象 → stat → delete；写 `file_access_audit`（action=STORAGE_TEST）；请求与响应均无密钥 |
|
> 权限码 `file:read`、`file:delete` 随权限目录 seed 注册，供后续模块（M05/M06 领域服务下载/删除场景）使用，M04 对外面不留公共下载/删除端点。

不提供任何凭 fileId 的公共下载端点。

### 6.2 内部 API（服务令牌：aud=educloud-file + clientId 白名单；`ownerService` 从已认证 clientId 推导，不接受请求体伪造）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/v1/files/{id}/availability` | `{exists, status, contentType, sizeBytes}`；调用方须对该文件有绑定（按 clientId 推导 ownerService 查询） |
| POST | `/internal/v1/files/{id}/bind` | `{ownerType, ownerId}` → FileBound；重复绑定幂等；锁根+版本递增 |
| POST | `/internal/v1/files/{id}/unbind` | `{ownerType, ownerId}` → FileUnbound；未绑定幂等返回 |
| POST | `/internal/v1/files/{id}/download-grants` | `{subjectType, subjectUserId?, ownerType, ownerId, purpose, requestedTtlSeconds?}` → `{status: GRANTED\|UNAVAILABLE, url, expiresAt}`；校验精确绑定 + purpose 白名单 + TTL 上限（默认 5 分钟，上限 15 分钟）；subjectType=ANONYMOUS 仅限登记的公开展示 purpose |
| POST | `/internal/v1/file-download-grants/batch` | `{subjectType, subjectUserId?, purpose, requestedTtlSeconds?, items[≤100]}` → `items[{requestKey, fileId, status, url, expiresAt}]`；任一项伪造/错配/越权 purpose → 整批 403 + 写 `GRANT_BATCH_DENIED` 审计；URL 不落库、不进事件/日志 |
| POST | `/internal/v1/files/{id}/delete` | `{ownerType, ownerId, reason}` → 先查绑定：存在未解绑绑定 → 409 `FILE_BOUND`；否则删除对象 + 行置 DELETED + `FileDeleted` |

### 6.3 错误码（`FileErrorCode`）

`UPLOAD_SESSION_EXPIRED(410)`、`UPLOAD_SESSION_NOT_FOUND(404)`、`UPLOAD_NOT_VERIFIED(409)`、`FILE_TYPE_NOT_ALLOWED(415)`、`FILE_TOO_LARGE(413)`、`FILE_NOT_FOUND(404)`、`FILE_BOUND(409)`、`FILE_ACCESS_DENIED(403)`、`GRANT_PURPOSE_NOT_ALLOWED(403)`、`STORAGE_TEST_RATE_LIMITED(429)`；通用错误复用 CommonErrorCode。

## 7. 核心流程

### 7.1 上传

1. 前端 POST 创建会话（Bearer + `file:upload`）→ File 生成对象键、校验类型白名单与大小上限、建 `file_upload_session(PENDING)`、返回 presigned PUT URL（默认 5 分钟）。
2. 浏览器 PUT 直传 MinIO（不经网关/File）。
3. 前端 POST complete → File `statObject` 校验存在与大小 → 下载对象（10MB 上限、限频、超时 30s）计算 SHA-256 → 二次校验 content-type 白名单 → 会话 COMPLETED、`file_object` AVAILABLE、发 `FileUploaded`。
4. 失败路径：对象缺失 → 409 `UPLOAD_NOT_VERIFIED`；类型/大小不符 → 415/413；会话过期 → 410，会话置 EXPIRED。

### 7.2 绑定（M04 内以头像为例）

1. User `PUT /api/v1/me/profile {avatarFileId}` → User 以 `user-service` 服务令牌调 File `bind {ownerType=USER_PROFILE, ownerId=userId}`（bind 失败 → 回滚返回 503，不落脏绑定）。
2. File 锁 `file_object` 根、写 `file_binding`、递增版本、发 `FileBound`。
3. User 更新 `user_profile.avatar_file_id`。

### 7.3 下载授权（展示）

1. User `/me`、用户详情/分页组装 DTO 时，以 `user-service` 服务令牌调 File 批量 grant（`purpose=PROFILE_AVATAR`，`subjectType=USER`，`subjectUserId=当前用户`，每页一次 ≤100）。
2. File 逐项校验 `(ownerService=user, ownerType, ownerId)` 精确绑定 + 文件 AVAILABLE → 返回 presigned GET（5 分钟）。
3. User DTO 携带 `avatarUrl`，不落库、不缓存、不进事件/日志；前端下次请求重新获取。
4. 未绑定/不可用 → 该项 `UNAVAILABLE`（不返回 URL）；伪造 owner → 整批 403。

### 7.4 删除与清理

- 内部 delete：先查绑定，未解绑 → 409；删除 MinIO 对象 + 行 DELETED + `FileDeleted`。
- 定时任务：未绑定 AVAILABLE 文件超过保留期（默认 24h）→ 二次查绑定后删除；过期上传会话（默认 15 分钟）→ EXPIRED/ABORTED；两者均限批处理、幂等、可观测。

## 8. 事件与 Outbox

`FileUploaded`（complete）、`FileBound`、`FileUnbound`、`FileDeleted`；aggregateType=FileObject、aggregateId=file_object.id、根版本递增；经 Outbox 发布到 `educloud.events`（复用 M03 RabbitConfiguration/OutboxWriter 模式）。User 订阅 `FileDeleted`：匹配 `user_profile.avatar_file_id` 的行置 NULL（Inbox 消费者，M03 已建 Inbox 表）。

## 9. 安全设计

- 服务端生成对象键，文件名不参与路径拼接；`original_name` 仅存元数据。
- 类型白名单（默认 `image/jpeg`、`image/png`、`image/webp`、`image/gif`、`application/pdf`）+ 大小上限（默认 10MB），全部可配置；可执行文件/脚本天然拒绝。
- 私有 bucket：不提供匿名直读；下载仅经 presigned GET 且存储层强制过期。
- 服务令牌：File `InternalApiFilter` 校验 aud=educloud-file + clientId 白名单（默认 `user-service`，未来加 course/content/live）；`ownerService` 恒由 clientId 映射，不接受客户端输入。
- 用户令牌：File Resource Server 用 User 公钥 JWKS 验签（RS256、issuer/aud 校验），权限码驱动 `@PreAuthorize`。
- 错误响应不含 MinIO 路径、Access Key、内部主机信息。
- `storage-tests` 限频 + 审计；`file_access_audit` 记录敏感访问。
- 网关侧：`/api/v1/files/**` 保持 PROTECTED（不在匿名放行清单）；请求体缓存过滤器对会话创建/complete 的小 body 无影响。

## 10. 配置项（`educloud.file.*`，env 可覆盖）

```yaml
educloud:
  file:
    storage:
      endpoint: ${MINIO_ENDPOINT:http://127.0.0.1:9000}
      access-key: ${MINIO_ACCESS_KEY:${MINIO_ROOT_USER:}}
      secret-key: ${MINIO_SECRET_KEY:${MINIO_ROOT_PASSWORD:}}
      bucket: ${EDUCLOUD_FILE_BUCKET:educloud-files}
    upload:
      max-size-bytes: ${EDUCLOUD_FILE_MAX_SIZE:10485760}   # 10MB
      allowed-content-types: ${EDUCLOUD_FILE_ALLOWED_TYPES:image/jpeg,image/png,image/webp,image/gif,application/pdf}
      put-url-ttl: ${EDUCLOUD_FILE_PUT_URL_TTL:5m}
      session-ttl: ${EDUCLOUD_FILE_SESSION_TTL:15m}
    download-grant:
      default-ttl: ${EDUCLOUD_FILE_GRANT_TTL:5m}
      max-ttl: ${EDUCLOUD_FILE_GRANT_MAX_TTL:15m}
      purposes: ${EDUCLOUD_FILE_GRANT_PURPOSES:PROFILE_AVATAR,PUBLIC_CATALOG}
    cleanup:
      unbound-retention: ${EDUCLOUD_FILE_UNBOUND_RETENTION:24h}
      session-expiry: ${EDUCLOUD_FILE_SESSION_EXPIRY:15m}
      batch-size: ${EDUCLOUD_FILE_CLEANUP_BATCH:50}
    storage-test:
      rate-limit: ${EDUCLOUD_FILE_STORAGE_TEST_RATE:1}
      window: ${EDUCLOUD_FILE_STORAGE_TEST_WINDOW:1m}
    internal:
      bootstrap-key: ${EDUCLOUD_FILE_INTERNAL_BOOTSTRAP_KEY:}
      allowed-client-ids: ${EDUCLOUD_FILE_INTERNAL_ALLOWED_CLIENT_IDS:user-service}
      audience: ${EDUCLOUD_FILE_INTERNAL_AUDIENCE:educloud-file}
    jwt:
      jwks-location: ${FILE_JWKS_LOCATION:}   # User 公钥 JWKS（用户令牌验签）
      issuer: ${EDUCLOUD_FILE_JWT_ISSUER:https://issuer.educloud.local}
      audience: ${EDUCLOUD_FILE_JWT_AUDIENCE:educloud-api}
```

部署补充：开发/演示环境 File 服务直接使用 compose `.env` 的 `MINIO_ROOT_USER/PASSWORD`（`start-dev.sh` 负责初始化 `educloud-files` 私有 bucket）；生产环境经 Kubernetes Secret 提供独立最小权限凭据（`MINIO_ACCESS_KEY/MINIO_SECRET_KEY` 覆盖，仅该 bucket 读写），密钥不进普通 API 响应与日志。

## 11. 可观测性

- `FileDependenciesHealthIndicator`：mysql/redis/rabbit/minio 就绪组（MinIO `bucketExists` + 轻量探测）。
- `FileMetrics`：上传会话创建/完成/失败计数、对象大小分布、grant 成功/拒绝计数、storage-test 计数、MinIO 操作耗时与错误类别。
- 日志：不记录 Access Key/Secret、presigned URL（完整签名）、文件内容哈希之外的敏感元数据（哈希可记）。

## 12. 测试策略

按契约门禁：先写失败测试 → 确认失败 → 最小实现 → 模块测试 → 集成测试 → 全量验证。

- 单测（mock MinioClient/Mapper）：对象键生成、白名单/大小校验、会话状态机、绑定幂等、grant 精确绑定判定与 TTL 上限、批量 ≤100 与整批拒绝、删除前置绑定检查、清理任务幂等。
- 集成测试（Testcontainers：MySQL + MinIO + Redis，模式复用 M03 `TestContainerImages`）：上传会话创建 → presigned PUT 直传 → complete 校验哈希 → 绑定 → 批量 grant → 解绑 → 删除 → FileDeleted 事件落 Outbox；伪造 owner 整批 403。
- 端到端（VM）：真实浏览器/脚本走 Gateway：创建会话 → PUT MinIO → complete → `/me/profile` 绑定 → `/me` 返回 avatarUrl → 展示。
- 全量 `mvn verify` + 前端三门户 `tsc`/`vite build`。

## 13. 前端接入（头像，已确认范围 A）

- 学生端 `Profile.tsx`：头像选择 → 创建会话 → PUT 直传 → complete → `PUT /me/profile {avatarFileId}` → 重新拉取 `/me` 展示新头像；失败（类型/大小/过期）映射中文提示。
- 三门户 Navbar/用户卡：头像 `src` 改用 `/me` 返回的 `avatarUrl`（原 mock 头像保留为无头像兜底）。
- 工具：各门户新增 `services/file.ts`（createSession/complete 封装 + presigned PUT 直传，put 用 fetch 并设置 Content-Type；put 失败清理会话）。
- 教师端/管理端无资料页，仅展示真实头像（`/me` 已联调）。

## 14. 门禁与验证清单

- [x] 读取 2026-08-18 领域/安全/数据/API/事件/测试/部署文档并审阅 diff
- [x] 先写失败测试并确认失败
- [x] 实现最小后端能力（模块测试绿）
- [x] 集成测试（Testcontainer MinIO）绿
- [x] 全量 `mvn verify` 绿
- [x] 规格审查与质量审查（含独立代码审查：越权/类型伪造/超限/未绑定访问测试通过）
- [x] VM 部署：迁移 `educloud_file`、bootstrap `user-service` 客户端、JWKS 配置、start-dev 拉起
- [x] 浏览器验证：三门户头像上传/展示闭环 + 越权用例（他人 fileId 不可访问）
- [x] 向用户汇报并等待确认后进入 M05

## 15. 已知决策点（实现中若遇冲突回本表）

| 决策点 | 默认选择 | 说明 |
|---|---|---|
| 头像绑定的事务边界 | User 先调 File bind 成功再更新本地 profile | bind 失败返回 503 并回滚，不落脏状态 |
| complete 的 SHA-256 计算 | File 服务端下载计算（10MB 上限内） | 大文件场景（M06 视频）再评估服务端流式校验替代方案 |
| 批量 grant 失败语义 | 任一项伪造 → 整批 403 | 防止逐一探测绑定关系；合法但不可用项返回 UNAVAILABLE |
| 未绑定清理保留期 | 24h | 可配置；清理前二次查绑定 |
| QUARANTINED 状态 | 仅保留状态位与迁移，不实现扫描 | M06+ 评审后启用 |

## 16. 参考文档

- [服务边界与领域模块](./2026-08-18-educloud-services-and-domains.md)（第 9 节 File 服务）
- [数据库与数据设计](./2026-08-18-educloud-data-design.md)（第 9 节 File 数据库、442 行清理）
- [API、事件与前后端联调](./2026-08-18-educloud-api-and-integration.md)（11.2 File、内部 grant 契约）
- [认证、权限与安全](./2026-08-18-educloud-security-and-permissions.md)（第 11 节文件安全）
- [异常、可靠性与可观测性](./2026-08-18-educloud-reliability-and-observability.md)（MinIO 失败语义、清理任务）
- [架构与栈](./2026-08-18-educloud-architecture-and-stack.md)（File 8087、MinIO SDK 8.5.7）
- [模块执行顺序与准备门禁](./2026-08-20-educloud-backend-module-execution.md)（M04 边界与门禁）
