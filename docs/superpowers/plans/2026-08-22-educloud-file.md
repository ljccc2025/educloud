# EduCloud M04 educloud-file 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。
>
> 规格：`docs/superpowers/specs/2026-08-22-educloud-file-design.md`（已批准）｜执行契约：`docs/superpowers/specs/2026-08-20-educloud-backend-module-execution.md`

**目标：** 交付 `educloud-file` 服务：文件上传会话、MinIO 对象管理、受控业务绑定、内部下载授权（单文件+批量）、删除与清理、存储状态探测；并与 User 服务联调完成三门户头像上传/展示闭环。

**架构：** Spring MVC 服务（端口 8087/8088），对外 API 经 Gateway（Bearer+权限码），内部 API 用服务令牌（aud=educloud-file）；MinIO presigned PUT 直传 + complete 服务端 SHA-256 校验；presigned GET 短期授权；Outbox 发布 FileUploaded/FileBound/FileUnbound/FileDeleted。

**技术栈：** Spring Boot 3.2.5、MyBatis-Plus 3.5.12（分页+乐观锁拦截器，含 mybatis-plus-jsqlparser）、minio-java 8.5.7、MySQL 8.0.36（`educloud_file`）、Redis/RabbitMQ/Nacos（复用 M03 模式）、Testcontainers。

---

## 文件结构（锁定分解）

```text
educloud-backend/educloud-file/
├── pom.xml
└── src/main/java/com/educloud/file/
    ├── FileApplication.java
    ├── config/FileProperties.java            # educloud.file.* 全部配置（storage/upload/grant/cleanup/internal/jwt）
    ├── config/MybatisPlusConfig.java         # 复制 user 模块同文件（分页+乐观锁）
    ├── controller/FileUploadSessionController.java   # POST /api/v1/file-upload-sessions(/complete)
    ├── controller/FileStorageController.java         # GET storage-status / POST storage-tests
    ├── controller/InternalFileController.java        # /internal/v1/files/**（bind/unbind/grant/batch/availability/delete）
    ├── dto/request/… dto/response/…
    ├── entity/FileUploadSessionEntity.java, FileObjectEntity.java, FileBindingEntity.java, FileAccessAuditEntity.java
    ├── mapper/（4 个 BaseMapper + OutboxEventMapper/OutboxSequenceMapper）
    ├── security/SecurityConfiguration.java   # Resource Server（User 公钥 JWKS）+ 方法安全
    ├── security/InternalApiFilter.java       # 服务令牌（aud=educloud-file + clientId 白名单）
    ├── security/JwtDecoderConfiguration.java # 复制 gateway 同文件（JWKS 静态加载、RS256）
    ├── service/UploadSessionService.java     # 会话状态机
    ├── service/FileObjectService.java        # complete/delete/AVAILABLE
    ├── service/FileBindingService.java       # bind/unbind（锁根+版本）
    ├── service/DownloadGrantService.java     # 单文件+批量 grant
    ├── service/FileCleanupService.java       # @Scheduled 未绑定/过期会话清理
    ├── storage/StorageGateway.java           # 接口：presignedPut/stat/downloadSha256/delete/probe
    ├── storage/MinioStorageGateway.java      # MinIO SDK 实现
    ├── messaging/RabbitConfiguration.java + FileEventPublisher.java   # 复制 user 模式
    ├── observability/FileDependenciesHealthIndicator.java + FileMetrics.java
    ├── support/ObjectKeyFactory.java, ContentTypePolicy.java, GrantPurposePolicy.java
    ├── exception/FileErrorCode.java, FileExceptionHandler.java
    └── support/FileAccessAuditWriter.java
deploy/sql/file/V000__technical_tables.sql
deploy/sql/file/V001__file.sql
deploy/scripts/provision-file-nacos.sh
deploy/scripts/start-dev.sh                  # 增加 educloud-file 启动段
deploy/scripts/bootstrap-service-clients.sh # 调用方增加 user-service 注册示例
deploy/docker-compose/.env / .env.example   # 增加 FILE 相关变量
educloud-backend/educloud-user/…             # 任务 15 修改（FileClient/avatarUrl/FileDeleted 消费者）
educloud-frontend/{student,teacher,admin}-portal/src/…  # 任务 16 修改
```

## 任务 0：模块骨架与上下文测试

**文件：**
- 修改：`educloud-backend/pom.xml`（modules 增加 `educloud-file`；dependencyManagement 增加 `io.minio:minio:8.5.7`）
- 创建：`educloud-backend/educloud-file/pom.xml`（依赖：common、web、validation、security、oauth2-resource-server、data-redis、amqp、actuator、micrometer-prometheus、micrometer-tracing-brave、zipkin-brave、mybatis-plus-spring-boot3-starter、mybatis-plus-jsqlparser、mysql runtime、lombok、minio；test：starter-test、security-test、testcontainers junit-jupiter/testcontainers/mysql/rabbitmq、awaitility；`<skipITs>true</skipITs>` 属性与 failsafe 配置复制 user 模块）
- 创建：`educloud-backend/educloud-file/src/main/java/com/educloud/file/FileApplication.java`
- 创建：`educloud-backend/educloud-file/src/main/resources/application.yml`（参照 user 模块骨架，端口 `${SERVER_PORT:8087}`，management `${FILE_MANAGEMENT_PORT:8088}`，nacos 注册 `educloud-file`，`educloud.file.*` 配置按规格第 10 节全量声明）
- 创建：`educloud-backend/educloud-file/src/main/java/com/educloud/file/config/FileProperties.java`（`@ConfigurationProperties("educloud.file")` record，字段与规格第 10 节一一对应）
- 测试：`educloud-backend/educloud-file/src/test/java/com/educloud/file/FileApplicationContextTest.java`（复制 `UserApplicationContextTest` 模式：排除 DataSource/Redis/Rabbit/Nacos 自动配置，mock `StringRedisTemplate`/`ConnectionFactory`/`MinioClient`/`OutboxEventMapper`/`OutboxSequenceMapper`，`--educloud.file.storage.endpoint=http://127.0.0.1:9000` 等测试参数）

- [x] **步骤 1：写上下文测试**（复制 user 的 `UserApplicationContextTest`，替换为 File 的 mapper 与 `MinioClient` mock；断言 `context.isActive()`）
- [x] **步骤 2：创建 pom/骨架/application.yml/FileProperties**（此步后 `mvn -pl educloud-file -am test-compile` 可编译）
- [x] **步骤 3：运行上下文测试确认失败**
运行：`cd educloud-backend && mvn -pl educloud-file -am test -Dtest=FileApplicationContextTest -Dsurefire.failIfNoSpecifiedTests=false`
预期：FAIL——缺 bean（MinioStorageGateway 未实现/配置属性缺失）
- [x] **步骤 4：实现让上下文可启动的最小配置**：注册 `MinioClient` bean（`FileStorageConfiguration`，用 FileProperties 构建，懒连接）；`FileDependenciesHealthIndicator` 临时返回 mock（任务 14 替换）。
- [x] **步骤 5：运行测试确认通过**（预期 PASS）
- [x] **步骤 6：Commit**
```bash
git add educloud-backend/pom.xml educloud-backend/educloud-file
git commit -m "feat(file): 模块骨架与最小上下文启动"
```

## 任务 1：SQL 迁移（V000 + V001）与 Schema 集成测试

**文件：**
- 创建：`deploy/sql/file/V000__technical_tables.sql`（复制 user 的 V000：schema_migration_history、outbox_event、outbox_sequence、inbox_event、audit_event、idempotency_record；GRANT 全部改为 `file_app`/`file_migration`）
- 创建：`deploy/sql/file/V001__file.sql`（规格第 5 节 4 张表 + 索引 + `file_app` 表级 GRANT：SELECT/INSERT/UPDATE/DELETE；`file_access_audit` 只授 SELECT/INSERT）
- 测试：`educloud-file/src/test/java/com/educloud/file/mapper/FileSchemaIT.java`（复制 user 的 `SessionSchemaIT` 模式：Testcontainer MySQL，以 file_migration 执行 V000+V001，断言表存在、唯一键、列类型、file_app 可 SELECT/INSERT）

- [x] **步骤 1：写失败测试**（FileSchemaIT 断言 `file_object` 存在且 `uk_file_object_key` 唯一、`file_binding` 唯一键、`file_upload_session` 状态列）
- [x] **步骤 2：运行确认失败**（`mvn -pl educloud-file -am verify -Pintegration -Dtest=FileSchemaIT` 预期 FAIL：表不存在）
- [x] **步骤 3：编写 V000/V001 SQL**（对照规格第 5 节 DDL；注意 `file_binding.owner_id VARCHAR(128)`、`file_access_audit.request_id VARCHAR(36)`）
- [x] **步骤 4：运行 IT 确认通过**
- [x] **步骤 5：Commit**
```bash
git add deploy/sql/file educloud-backend/educloud-file/src/test
git commit -m "feat(file): educloud_file 库迁移与 Schema 集成测试"
```

## 任务 2：实体、Mapper 与 MybatisPlusConfig

**文件：**
- 创建：`entity/FileUploadSessionEntity.java`、`FileObjectEntity.java`（`@Version`）、`FileBindingEntity.java`、`FileAccessAuditEntity.java`（Lombok @Data + @TableName，字段对齐 V001）
- 创建：`mapper/FileUploadSessionMapper.java`、`FileObjectMapper.java`、`FileBindingMapper.java`、`FileAccessAuditMapper.java`（BaseMapper，@Mapper）
- 创建：`config/MybatisPlusConfig.java`（复制 user 模块同文件：PaginationInnerInterceptor + OptimisticLockerInnerInterceptor）
- 测试：`mapper/FileMapperContractTest.java`（Mockito：`selectOne`/`insert` 冒烟——验证 Mapper 可被 mock 使用）；真实 CRUD 由任务 1 的 FileSchemaIT 覆盖

- [x] **步骤 1：写失败测试**：`FileMapperContractTest` 断言 4 个 Mapper 接口存在且继承 BaseMapper（编译期契约）
- [x] **步骤 2：运行确认失败**（类不存在编译失败）
- [x] **步骤 3：实现实体/Mapper/MybatisPlusConfig**
- [x] **步骤 4：运行确认通过**；**步骤 5：Commit**（`feat(file): 领域实体与数据访问层`）

## 任务 3：StorageGateway 抽象与 MinIO 实现

**文件：**
- 创建：`storage/StorageGateway.java`（接口）：
```java
public interface StorageGateway {
    String presignedPutUrl(String bucket, String objectKey, String contentType, Duration ttl);
    ObjectStat stat(String bucket, String objectKey);            // exists,size,contentType
    byte[] download(String bucket, String objectKey, int maxBytes);  // 超限抛 FileTooLargeException
    String sha256(String bucket, String objectKey, int maxBytes);    // 下载后计算
    void deleteObject(String bucket, String objectKey);
    StorageProbeResult probe();   // 最小读写探测：put 临时对象→stat→delete
}
```
- 创建：`storage/MinioStorageGateway.java`（minio-java：`getPresignedObjectUrl(PUT)`、`statObject`、`getObject`、`removeObject`、`bucketExists`；probe 用随机键）
- 创建：`storage/FileStorageConfiguration.java`（`MinioClient` bean，endpoint/accessKey/secretKey 来自 FileProperties）
- 测试：`storage/MinioStorageGatewayTest.java`（mock MinioClient：断言 presigned 参数 bucket/key/method=PUT、stat 映射、download 超限抛异常）；`storage/MinioStorageGatewayIT.java`（Testcontainer minio/minio：真实 presigned PUT→stat→sha256 与上传内容一致→delete）

- [x] **步骤 1：写失败测试**（单测断言接口签名与 mock 行为）
- [x] **步骤 2：运行确认失败**
- [x] **步骤 3：实现 MinioStorageGateway**（注意：sha256 用 `DigestInputStream` 流式计算，maxBytes 超限抛异常并 abort）
- [x] **步骤 4：单测通过；步骤 5：IT 通过**（`-Pintegration`）
- [x] **步骤 6：Commit**（`feat(file): MinIO 存储网关（presigned/校验/探测）`）

## 任务 4：上传会话服务（状态机）

**文件：**
- 创建：`dto/request/CreateUploadSessionRequest.java`（`@NotBlank contentType`、`expectedSizeBytes` 可选、`originalName` 可选）
- 创建：`dto/response/UploadSessionResponse.java`（sessionId、uploadUrl、expiresInSeconds）
- 创建：`support/ObjectKeyFactory.java`（`{bucket}/{owner}/{yyyyMMdd}/{uuid}.{ext}`；ext 从 contentType 映射，白名单外拒绝）
- 创建：`support/ContentTypePolicy.java`（白名单+大小上限校验，抛 `FILE_TYPE_NOT_ALLOWED`/`FILE_TOO_LARGE`）
- 创建：`service/UploadSessionService.java`：
```java
public UploadSessionResponse create(Long uploaderId, CreateUploadSessionRequest req);
public FileObjectEntity complete(Long uploaderId, Long sessionId);  // PENDING→校验→COMPLETED
public void expireOverdue(Duration maxAge);  // 供清理任务调用
```
- 测试：`service/UploadSessionServiceTest.java`（mock gateway/mapper：create 生成对象键+presigned URL+落库 PENDING；类型/大小拒绝；complete 时 stat 缺失→`UPLOAD_NOT_VERIFIED`、会话过期→`UPLOAD_SESSION_EXPIRED`、sha256 落对象）

- [x] **步骤 1：写失败测试**（create 成功路径 + 3 个拒绝路径 + complete 状态机）
- [x] **步骤 2：运行确认失败**
- [x] **步骤 3：实现**（create：校验→对象键→`file_upload_session(PENDING)`→presigned PUT；complete：锁会话行→校验状态/过期→gateway.stat→下载 sha256→`file_object(AVAILABLE)`+会话 COMPLETED）
- [x] **步骤 4：运行通过；步骤 5：Commit**（`feat(file): 上传会话状态机`）

## 任务 5：文件对象服务（complete 落对象 + 删除）

**文件：**
- 创建：`service/FileObjectService.java`：`completeUpload`（调 UploadSessionService.complete + 发 FileUploaded）、`deleteIfUnbound(fileId, reason)`（先查 file_binding 活跃绑定→`FILE_BOUND`；否则删对象+行 DELETED+FileDeleted）
- 创建：`service/FileBindingService.java`：`bind(fileId, ownerService, ownerType, ownerId)`、`unbind(...)`（幂等；`SELECT ... FOR UPDATE` 锁 file_object 根→写 binding→`version+1`；发 FileBound/FileUnbound）
- 测试：`service/FileObjectServiceTest.java`、`service/FileBindingServiceTest.java`（绑定幂等、重复 bind 同 owner 幂等、unbind 未绑定幂等、delete 有活跃绑定拒绝、delete 成功发事件、版本递增断言）

- [x] **步骤 1：写失败测试**
- [x] **步骤 2：运行确认失败**
- [x] **步骤 3：实现**（锁根用 `fileObjectMapper.selectByIdForUpdate(fileId)`，mapper 加 `@Select("SELECT * FROM file_object WHERE id=#{id} FOR UPDATE")`）
- [x] **步骤 4：运行通过；步骤 5：Commit**（`feat(file): 文件对象与绑定服务（锁根+事件）`）

## 任务 6：下载授权（单文件 + 有界批量）

**文件：**
- 创建：`support/GrantPurposePolicy.java`（purpose 白名单：`PROFILE_AVATAR`、`PUBLIC_CATALOG`；`PUBLIC_CATALOG` 允许 ANONYMOUS，其余仅 USER）
- 创建：`service/DownloadGrantService.java`：
```java
public GrantResult grantSingle(String ownerService, GrantSingleRequest req);
public BatchGrantResult grantBatch(String ownerService, GrantBatchRequest req); // items ≤100、requestKey 去重
```
- 创建：`support/FileAccessAuditWriter.java`（写 file_access_audit：GRANT_SINGLE/GRANT_BATCH_DENIED/DELETE/DELETE_FORCE/STORAGE_TEST）
- 测试：`service/DownloadGrantServiceTest.java`：精确绑定才 GRANTED；未绑定/文件不可用→UNAVAILABLE；owner 错配→整批 403+审计；purpose 越权拒绝；TTL 超过 max-ttl 被钳制；items>100 拒绝；ANONYMOUS 仅限 PUBLIC_CATALOG

- [x] **步骤 1：写失败测试**（7 个用例）
- [x] **步骤 2：运行确认失败**
- [x] **步骤 3：实现**（逐项校验 `file_binding` 活跃行 `(ownerService,ownerType,ownerId)` + `file_object.status=AVAILABLE`；任一伪造→抛 `FILE_ACCESS_DENIED` 整批失败；合法不可用项→UNAVAILABLE；presigned GET TTL=min(requested, max)）
- [x] **步骤 4：运行通过；步骤 5：Commit**（`feat(file): 内部下载授权（单文件+批量）`）

## 任务 7：错误码与异常处理

**文件：**
- 创建：`exception/FileErrorCode.java`（规格 6.3 全量：UPLOAD_SESSION_EXPIRED 410、UPLOAD_SESSION_NOT_FOUND 404、UPLOAD_NOT_VERIFIED 409、FILE_TYPE_NOT_ALLOWED 415、FILE_TOO_LARGE 413、FILE_NOT_FOUND 404、FILE_BOUND 409、FILE_ACCESS_DENIED 403、GRANT_PURPOSE_NOT_ALLOWED 403、STORAGE_TEST_RATE_LIMITED 429）
- 创建：`exception/FileExceptionHandler.java`（@RestControllerAdvice：BusinessException→统一信封；AccessDeniedException→403；Exception→500 不泄漏细节）
- 测试：`exception/FileExceptionHandlerTest.java`（MockMvc 断言各错误码状态与响应体）

- [x] **步骤 1-4**：失败测试→实现→通过；**步骤 5：Commit**（`feat(file): 错误码与全局异常处理`）

## 任务 8：安全配置（Resource Server + 内部过滤器）

**文件：**
- 创建：`security/JwtDecoderConfiguration.java`（复制 gateway 同文件：JWKS 静态加载、RS256、issuer 校验 + `GatewayJwtValidator` 等价物——aud 校验 `educloud-api`、claims 契约；`JwksLoader`/`JwksState` 一并复制）
- 创建：`security/SecurityConfiguration.java`（上传需要 `file:upload` 权限，全部 authenticated；permitAll 仅 `/actuator/health/**`；oauth2ResourceServer + jwtAuthenticationConverter 权限码无前缀；@EnableMethodSecurity）
- 创建：`security/InternalApiFilter.java`（复制 user 模块：Bearer→jwtDecoder.decode→`aud.contains(internalAudience)`+`clientId` 白名单→request attribute；`/internal/v1/**` 生效）
- 创建：`security/StorageTestRateLimitFilter.java`（POST /api/v1/files/storage-tests 按用户 Redis 计数：默认 1 次/分钟，超限 429 STORAGE_TEST_RATE_LIMITED）
- 测试：`security/InternalApiFilterTest.java`（复制 user 版：aud=educloud-file 放行、白名单外 403、缺 token 401）；`security/SecurityConfigurationTest.java`（无 token 401、带权限码 token 放行）

- [x] **步骤 1：写失败测试**
- [x] **步骤 2：运行确认失败**
- [x] **步骤 3：实现**（注意：内部接口不经过 @PreAuthorize，控制器方法级鉴权由 InternalApiFilter 完成）
- [x] **步骤 4：通过；步骤 5：Commit**（`feat(file): 资源服务器与内部服务令牌过滤器`）

## 任务 9：对外 API 控制器

**文件：**
- 创建：`controller/FileUploadSessionController.java`：
```java
@RestController @RequestMapping("/api/v1/file-upload-sessions")
public class FileUploadSessionController {
    @PostMapping @PreAuthorize("hasAuthority('file:upload')")
    public ApiResponse<UploadSessionResponse> create(@Valid @RequestBody CreateUploadSessionRequest req,
            @AuthenticationPrincipal Jwt jwt) { … }
    @PostMapping("/{id}/complete") @PreAuthorize("hasAuthority('file:upload')")
    public ApiResponse<FileObjectResponse> complete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) { … }
}
```
- 创建：`controller/FileStorageController.java`（`GET /api/v1/files/storage-status` 需 `file:storage:status:read`；`POST /api/v1/files/storage-tests` 需 `file:storage:test` + 限频过滤器 + 审计 STORAGE_TEST）
- 测试：`controller/FileUploadSessionControllerTest.java`（MockMvc + @WithMockJwt：无权限 403、成功路径、complete 调用服务）；`controller/FileStorageControllerTest.java`（status 脱敏、test 限频 429）

- [x] **步骤 1-4**：TDD 循环；**步骤 5：Commit**（`feat(file): 对外上传会话与存储状态 API`）

## 任务 10：内部 API 控制器

**文件：**
- 创建：`controller/InternalFileController.java`：
```java
@RestController @RequestMapping("/internal/v1")
public class InternalFileController {
    // GET  /files/{id}/availability
    // POST /files/{id}/bind            {ownerType, ownerId}
    // POST /files/{id}/unbind
    // POST /files/{id}/download-grants {subjectType, subjectUserId?, ownerType, ownerId, purpose, requestedTtlSeconds?}
    // POST /file-download-grants/batch {subjectType, subjectUserId?, purpose, requestedTtlSeconds?, items[≤100]}
    // POST /files/{id}/delete          {ownerType, ownerId, reason}
    // 全部先 InternalApiFilter.requireClientId(request) → ownerService = clientId 映射表（user→user, course→course, …）
}
```
- 创建：`support/OwnerServiceRegistry.java`（clientId→ownerService 映射，未知 clientId 拒绝）
- 测试：`controller/InternalFileControllerTest.java`（模拟已认证 clientId attribute：bind 幂等、grant 精确绑定、batch 整批拒绝、delete 前置绑定检查、未知 clientId 403）

- [x] **步骤 1-4**：TDD 循环；**步骤 5：Commit**（`feat(file): 内部文件接口（绑定/授权/删除）`）

## 任务 11：Outbox 事件发布

**文件：**
- 创建：`messaging/RabbitConfiguration.java`（复制 user：DirectExchange `educloud.events`、Jackson converter、placeholder 队列；模板 setExchange）
- 创建：`messaging/FileEventPublisher.java`（包装 OutboxWriter：FileUploaded/FileBound/FileUnbound/FileDeleted，aggregateType=FileObject、aggregateId=file_object.id、版本递增）
- 测试：`messaging/FileEventPublisherTest.java`（mock OutboxWriter 断言事件名/聚合/版本/载荷字段）；`messaging/OutboxEventDispatcherIT.java` 复用 user 版（真实 RabbitMQ Testcontainer：PENDING→PUBLISHED）

- [x] **步骤 1-4**：TDD；**步骤 5：Commit**（`feat(file): Outbox 事件发布（FileUploaded/FileBound/FileUnbound/FileDeleted）`）

## 任务 12：清理任务

**文件：**
- 创建：`service/FileCleanupService.java`：
```java
@Scheduled(fixedDelayString = "${educloud.file.cleanup.interval:60s}")
public void cleanup() {
    // 1) 未绑定 AVAILABLE 文件超 unbound-retention：二次查活跃绑定→无→删除对象+行 DELETED+FileDeleted
    // 2) 过期上传会话（> session-expiry）：EXPIRED；对应 object_key 无 file_object 时删除 MinIO 对象
    // 分批（batch-size）处理，幂等，失败记日志不中断
}
```
- 测试：`service/FileCleanupServiceTest.java`（保留期内不删、超期且二次确认无绑定才删、有绑定不删、会话过期清理）

- [x] **步骤 1-4**：TDD；**步骤 5：Commit**（`feat(file): 未绑定文件与过期会话清理任务`）

## 任务 13：可观测性

**文件：**
- 创建：`observability/FileDependenciesHealthIndicator.java`（mysql/redis/rabbit/minio 四组；MinIO 用 `bucketExists`+轻量 probe；参照 user 版结构）
- 创建：`observability/FileMetrics.java`（Counter/Histogram：upload_sessions_created/completed/failed、object_bytes、grants_granted/denied、storage_tests_total、minio_operation_duration）
- 测试：`observability/FileDependenciesHealthIndicatorTest.java`（各依赖 UP/DOWN 聚合、MinIO 异常降级）；`observability/FileMetricsTest.java`（计数行为）

- [x] **步骤 1-4**：TDD；**步骤 5：Commit**（`feat(file): 依赖健康与业务指标`）

## 任务 14：User 侧接线（bind + avatarUrl + FileDeleted 消费者）

**文件：**
- 创建：`educloud-user/src/main/java/com/educloud/user/support/FileClient.java`：
```java
@Component
public class FileClient {
    // 内部 HTTP（RestClient）：调 File /internal/v1/**
    // 令牌：注入 ServiceTokenService.issue("user-service", secret, "educloud-file", List.of("file:internal"))
    // 配置：educloud.user.file.endpoint / client-id / client-secret / enabled
    public void bindAvatar(Long userId, Long fileId);
    public List<AvatarGrant> grantAvatars(List<Long> fileIds, Long subjectUserId); // 批量≤100
    public void unbindAvatar(Long userId, Long fileId);
}
```
- 修改：`educloud-user/.../service/ProfileService.java`（updateProfile：`avatarFileId` 变更时先 `fileClient.bindAvatar` 成功→更新本地；失败抛 DEPENDENCY_UNAVAILABLE 回滚；`me()` 返回 `avatarUrl` 字段）
- 修改：`educloud-user/.../service/UserAdminService.java`（detail/page：批量 grant 组装 `avatarUrl`，一次调用）
- 修改：`educloud-user/.../dto/response/UserSummary.java`/`UserAdminItem.java`（加 `avatarUrl`）
- 创建：`educloud-user/.../messaging/FileDeletedInboxConsumer.java`（`@Scheduled` 轮询 inbox_event `processStatus=PENDING and eventType=FileDeleted`：解析 payload.fileId→`user_profile.avatar_file_id` 匹配则置 NULL；按业务效果幂等；失败计数+退避，达阈值标记 FAILED——复用 M03 Inbox 表）
- 修改：`educloud-user/src/main/resources/application.yml`（`educloud.user.file.*` 配置段）
- 测试：`FileClientTest`（mock ServiceTokenService/RestClient：bind 失败传播、grant 响应解析）；`ProfileServiceTest` 增补（avatar 绑定顺序、失败回滚）；`FileDeletedInboxConsumerTest`（匹配置空/不匹配跳过/失败重试）；`UserAdminServiceTest` 增补（avatarUrl 批量组装一次调用）

- [x] **步骤 1：写失败测试**（ProfileService：avatarFileId 变更先 bind 后落库；bind 失败抛依赖异常）
- [x] **步骤 2：运行确认失败**
- [x] **步骤 3：实现 FileClient + ProfileService/UserAdminService 改造**
- [x] **步骤 4：实现 FileDeleted 消费者**（先测试：匹配置空幂等）
- [x] **步骤 5：全量 user 单测通过；步骤 6：Commit**（`feat(user): 头像绑定与展示（File 联调）+ FileDeleted 引用清理`）

## 任务 15：前端头像上传与展示

**文件：**
- 创建（三门户各自）：`src/services/file.ts`：
```ts
export async function uploadAvatar(file: File): Promise<number> {
  // 1) POST /file-upload-sessions {contentType, originalName, expectedSizeBytes} → {sessionId, uploadUrl}
  // 2) fetch(uploadUrl, { method: 'PUT', headers: { 'Content-Type': file.type }, body: file })
  // 3) POST /file-upload-sessions/{id}/complete → fileId
  // 失败：调用方 try { const f = await uploadAvatar(f); await http.put('/me/profile', { avatarFileId: f }) }
}
```
- 修改：`student-portal/src/pages/Profile.tsx`（头像区域：文件选择（accept=image/*，≤10MB 前端预检）→ uploadAvatar → `PUT /me/profile {avatarFileId}` → 刷新 `/me` 显示新头像；错误用 apiErrorText 映射）
- 修改：三门户 `Navbar.tsx`/`TeacherLayout.tsx`/`AdminLayout.tsx` 头像 `<img src>`：`user?.avatarUrl ?? 兜底`（student `useAuthStore.user` 增加 `avatarUrl`；teacher/admin 的 store user 类型加 `avatarUrl`，`/me` 映射带出）
- 修改：`student-portal/src/services/api.ts` 的 `AuthUser`/`mapAuthUser` 与 `useAuthStore` 传递 avatarUrl
- 验证：三门户 `npx tsc --noEmit && npx vite build`

- [x] **步骤 1：实现 file.ts + student Profile 头像上传**（先手工验证 401/类型拒绝路径，再 tsc）
- [x] **步骤 2：三门户展示 avatarUrl**（store 类型+布局）
- [x] **步骤 3：tsc+build 通过**；**步骤 4：Commit**（`feat(frontend): 三门户头像上传与真实头像展示`）

## 任务 16：部署脚本与 VM 环境

**文件：**
- 创建：`deploy/scripts/provision-file-nacos.sh`（复制 provision-user-nacos.sh：账号 `educloud_file`、权限 `naming/educloud-file`；.env 增加 `NACOS_FILE_USERNAME/NACOS_FILE_PASSWORD`）
- 修改：`deploy/docker-compose/.env.example`（增加：`EDUCLOUD_FILE_DB_PASSWORD`、`EDUCLOUD_FILE_MIGRATION_PASSWORD`、`EDUCLOUD_FILE_INTERNAL_BOOTSTRAP_KEY`、`MINIO_ENDPOINT/MINIO_ACCESS_KEY/MINIO_SECRET_KEY/EDUCLOUD_FILE_BUCKET`、`EDUCLOUD_USER_FILE_CLIENT_ID/SECRET/ENDPOINT`）；VM 实际 `.env` 同步生成随机值
- 修改：`deploy/scripts/start-dev.sh`（新增 `[3/5] educloud-file` 段：端口 8087/8088、MYSQL/REDIS/RABBIT/NACOS/MINIO 环境变量、`FILE_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json`、`EDUCLOUD_FILE_JWT_ISSUER/AUDIENCE`、`EDUCLOUD_FILE_INTERNAL_*`；`wait_ready http://127.0.0.1:8088/actuator/health/readiness`；末尾打印 File 信息）
- 修改：`deploy/scripts/bootstrap-service-clients.sh` 用法注释增加示例；`run-migrations.sh --service file` 无需改动（通用）
- MinIO bucket 初始化：`MinioStorageGateway` 启动时 `bucketExists` 不存在则 `makeBucket`（实现放网关初始化）
- 修改：`educloud-user/src/main/resources/application.yml` 增加 `educloud.user.file.*`（任务 14 已含）

- [x] **步骤 1：本地验证**：`mvn -pl educloud-file -am package -DskipTests` 成功
- [x] **步骤 2：Commit**（`chore(deploy): educloud-file 部署脚本与环境变量`）

## 任务 17：VM 端到端验证

**文件：** 无（执行验证）

- [x] **步骤 1：同步**：tar（排除 .git/target/node_modules/dist/secrets/.env）→ ssh_upload → 解压覆盖
- [x] **步骤 2：迁移**：`MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 EDUCLOUD_FILE_MIGRATION_PASSWORD=… bash deploy/scripts/run-migrations.sh --service file`
- [x] **步骤 3：Nacos provision**：`bash deploy/scripts/provision-file-nacos.sh --env-file deploy/docker-compose/.env`
- [x] **步骤 4：bootstrap user-service 客户端**：`CLIENT_ID=user-service AUDIENCES='["educloud-file"]' SCOPES='["file:internal"]' BOOTSTRAP_KEY=… printf '%s' "$SECRET" | bash deploy/scripts/bootstrap-service-clients.sh`
- [x] **步骤 5：构建**：`/opt/maven/bin/mvn -pl educloud-file -am package -DskipTests` + 三门户 `tsc && vite build`
- [x] **步骤 6：启动**：`bash deploy/scripts/start-dev.sh`（file 8087/8088 监听；gateway 日志确认 file-core 路由可达）
- [x] **步骤 7：链路验证**（curl 走网关）：
```bash
# 登录拿 token（demo_teacher）→ 创建会话 → 用 presigned URL PUT 一个小图 → complete →
# PUT /api/v1/me/profile {avatarFileId} → GET /api/v1/me 断言 avatarUrl 存在且为 5 分钟 URL
# 越权：他人 fileId grant → 403；未绑定文件 grant → UNAVAILABLE
```
- [x] **步骤 8：浏览器验证**：三门户登录 → 学生端资料页换头像 → 三门户导航栏头像同步更新
- [x] **步骤 9：向用户汇报并等待确认**（契约门禁 9）

## 任务 18：全量门禁与独立代码审查

- [x] **步骤 1：全量**：`mvn -pl educloud-common,educloud-gateway,educloud-user,educloud-file -am verify`（-Pintegration 跑 IT）
- [x] **步骤 2：规格审查**：对照 2026-08-22-educloud-file-design.md 逐节核对实现与测试覆盖
- [x] **步骤 3：独立代码审查**：按 chinese-code-review 覆盖六维度；重点：越权（伪造 owner/批量探测）、类型伪造、超限、未绑定访问、presigned URL 泄露、清理竞态、事件版本一致性；自动修复可确定项，其余列待确认
- [x] **步骤 4：修复验证 + 汇报**（含 BUG 风险表）→ 等待用户确认后进入 M05

---

## 自检记录（编写时已核对）

- 规格覆盖：上传会话→任务 4/9；对象与删除→任务 5；绑定→任务 5；下载授权→任务 6/10；存储状态/探测→任务 9；事件→任务 11；清理→任务 12；安全→任务 8；配置→任务 0/16；前端头像→任务 15；User 接线→任务 14；门禁→任务 17/18。
- 类型一致性：`StorageGateway` 签名在任务 3 定义并被任务 4/5/12 使用；`DownloadGrantService` 请求/响应记录在任务 6 定义并被任务 10 引用；`FileClient` 在任务 14 定义并被任务 17 验证。
- 无占位符：所有任务含具体文件、命令与测试断言。
