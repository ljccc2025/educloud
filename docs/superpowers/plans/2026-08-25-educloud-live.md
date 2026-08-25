# M09 educloud-live（直播互动中心与 WebSocket 课堂）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建并交付 EduCloud 直播互动中心微服务 `educloud-live`（端口 8095/8096），完成直播间全生命周期管理（创建/开启/下播/取消）、Stream Provider SPI 插件化推拉流控制面（含沙箱 Mock 与生产环境门控）、60s 一次性 WebSocket Ticket 握手鉴权与 Redis `GETDEL` 原子核销、基于 Spring WebSocket + Redis Pub/Sub 的实时课堂互动（弹幕/点赞/举手/白板信令/全员禁言/消息撤回）、在线人数与出勤时长统计、回放自动归档与受控 File 授权点播，以及对齐 M08 经验的 `repackage` 可执行 Jar 打包与全链路 E2E 自动化测试。

**架构：** 基于 Spring Boot 3.2.5 + MyBatis-Plus + Nacos + Redis + RabbitMQ 双端口微服务架构；业务数据存储在独立逻辑库 `educloud_live`；基于统一 SPI（`LiveStreamProvider`）实现流媒体供应商解耦；基于 Spring 原生 WebSocket (`TextWebSocketHandler`) + Redis Pub/Sub 实现多实例房间消息跨节点广播；基于 Redis `GETDEL` 实现短效 Ticket 安全握手防重放；联动 `educloud-course` 校验选课权益，联动 `educloud-file` 获取安全签名播放 URL。

**技术栈：** Java 17, Spring Boot 3.2.5, Spring Cloud Alibaba (Nacos), MyBatis-Plus 3.5.12, MySQL 8.0, Redis, Spring WebSocket (`spring-boot-starter-websocket`), OpenFeign, React 18, TypeScript, Tailwind CSS, Vite.

---

## 继承与吸纳 M08 支付中心核心经验

1. **可执行 Fat Jar 打包规范（`repackage`）**：
   - `educloud-live/pom.xml` 必须显式配置 `spring-boot-maven-plugin` 的 `<goal>repackage</goal>` 执行目标，确保输出含有 `Main-Class` 的完整引导 Jar，杜绝虚拟机部署时出现“没有主清单属性”报错。
2. **Mock 插件生产环境门控防护**：
   - `MockLiveStreamProvider` 严格判断 `EDUCLOUD_ENVIRONMENT` 与 Spring Profile，当处于 `prod` 生产环境且未显式开启沙箱模式时，拦截 Mock 推拉流地址生成并抛出明确的未配置提供方异常。
3. **外部 RPC 调用与本地事务边界隔离**：
   - 调用 `CourseClient`（选课校验）与 `FileClient`（回放签名）等远程 Feign 接口时，必须移出 MySQL 本地数据库事务，避免远程网络 I/O 长期占用数据库连接池引发连接耗尽。
4. **IDOR 水平越权与 CAS 状态流转**：
   - 所有单据/直播间操作严格比对 `userId` 与 `teacher_id` / `sender_id`；
   - 开播（`CREATED ➔ LIVING`）与下播（`LIVING ➔ ENDED`）必须采用带版本号或原状态前置条件的原子 CAS 更新。
5. **部署脚本自愈与探针顺序**：
   - 虚拟机 `start-dev.sh` 按照拓扑顺序拉起 `educloud-live`，绑定 `SERVER_PORT=8095` 与 `LIVE_MANAGEMENT_PORT=8096`，通过 `/actuator/health/readiness` 探针自愈阻塞直到服务完全就绪。

---

## 文件结构与模块规划

```
educloud-backend/
├── pom.xml                                                 # 注册 educloud-live 模块
└── educloud-live/
    ├── pom.xml                                             # 模块依赖 (Web, WebSocket, Security, Redis, Feign, MyBatis-Plus, repackage)
    └── src/
        ├── main/
        │   ├── java/com/educloud/live/
        │   │   ├── LiveApplication.java                    # 启动类 (端口 8095/8096)
        │   │   ├── config/
        │   │   │   ├── IdentifierConfig.java               # 64 位雪花 ID 发生器
        │   │   │   ├── HealthIndicatorConfig.java          # MySQL/Redis/RabbitMQ/Nacos 深度健康检查
        │   │   │   ├── MybatisPlusConfig.java              # 分页与 @Version 乐观锁插件
        │   │   │   ├── RedisConfig.java                    # Redis 序列化与 Pub/Sub 容器配置
        │   │   │   ├── SecurityConfig.java                 # OAuth2 JWT 解码器与权限拦截
        │   │   │   ├── InternalApiFilter.java              # 内部 RPC Header 校验过滤器
        │   │   │   ├── WebSocketConfig.java                # 原生 WebSocket 端点与拦截器注册
        │   │   │   └── LiveProperties.java                 # 媒体配置与沙箱参数映射
        │   │   ├── controller/
        │   │   │   ├── LiveRoomController.java             # 直播间增删改查、开播、下播、Ticket申请、禁言
        │   │   │   ├── LiveMessageController.java          # 历史弹幕拉取、消息撤回
        │   │   │   └── LiveReplayController.java           # 录制回放查询与签名播放地址换取
        │   │   ├── dto/
        │   │   │   ├── request/                            # LiveRoomCreateRequest, LiveRoomUpdateRequest, MessageRecallRequest
        │   │   │   └── response/                           # LiveRoomDetailResponse, LiveTicketResponse, LiveReplayResponse
        │   │   ├── entity/                                 # LiveRoomEntity, LiveSessionEntity, LiveMessageEntity, LiveAttendanceEntity, LiveReplayEntity
        │   │   ├── enums/                                  # LiveRoomStatus, LiveSessionStatus, LiveMessageType, LiveReplayStatus, LiveProviderType
        │   │   ├── exception/                              # LiveErrorCode, LiveException, GlobalExceptionHandler
        │   │   ├── feign/
        │   │   │   ├── CourseClient.java                   # 校验学生课程选课 ACTIVE 状态
        │   │   │   ├── FileClient.java                     # 批量换取短期签名下载/点播 URL
        │   │   │   └── dto/
        │   │   ├── mapper/                                 # 各实体 MyBatis-Plus Mapper 接口
        │   │   ├── service/
        │   │   │   ├── LiveLifecycleService.java           # 直播间生命周期管理与 CAS 状态流转
        │   │   │   ├── LiveTicketService.java              # 60s 一次性 Ticket 签发与原子核销
        │   │   │   ├── LiveMessageService.java             # 消息持久化、敏感词与撤回
        │   │   │   ├── LiveAttendanceService.java          # 在线状态与观看时长统计
        │   │   │   ├── LiveReplayService.java              # 回放生成与受控签名授权
        │   │   │   └── impl/                               # 对应实现类
        │   │   ├── spi/                                    # 流媒体 Provider SPI 插件体系
        │   │   │   ├── LiveStreamProvider.java             # 统一契约接口
        │   │   │   ├── LiveStreamProviderFactory.java      # 插件工厂
        │   │   │   ├── model/                              # LiveStreamPushUrl, LiveStreamPlayUrls, StreamStatus
        │   │   │   └── plugins/
        │   │   │       └── MockLiveStreamProvider.java     # 本地自闭环沙箱推拉流插件 (带生产门控)
        │   │   └── websocket/                              # WebSocket 长连接与广播体系
        │   │       ├── LiveWebSocketHandler.java           # 长连接生命周期与消息处理
        │   │       ├── LiveWebSocketInterceptor.java       # Ticket 握手拦截与鉴权
        │   │       ├── LiveBroadcastService.java           # Redis Pub/Sub 广播发送
        │   │       ├── RedisMessageSubscriber.java         # Redis 广播监听与本机推送
        │   │       └── model/                              # WebSocketMessage (信令协议封装)
        │   └── resources/
        │       ├── application.yml                         # 端口 8095/8096、MySQL、Redis、Nacos 配置
        │       └── mapper/                                 # XML Mappers
        └── test/                                           # TDD 单元测试与切片测试

deploy/sql/live/
├── V000__technical_tables.sql                             # 雪花 ID 序列等技术表
└── V001__live_control_plane.sql                           # 直播间、场次、消息、出勤、回放表

deploy/sql/user/
└── V009__live_permissions.sql                             # 注入 live:create/manage/view/join/moderate 权限
```

---

### 任务 1：数据库脚本与权限迁移准备

**文件：**
- 创建：`deploy/sql/live/V000__technical_tables.sql`
- 创建：`deploy/sql/live/V001__live_control_plane.sql`
- 创建：`deploy/sql/user/V009__live_permissions.sql`
- 修改：`educloud-backend/pom.xml:20-30`（注册 `<module>educloud-live</module>`）

- [ ] **步骤 1：编写技术表与基础数据 Flyway 脚本（雪花 ID 分配序列）**
- [ ] **步骤 2：编写直播控制面五大核心表结构脚本（`V001__live_control_plane.sql`）**
- [ ] **步骤 3：编写权限注入脚本（`V009__live_permissions.sql`）为管理员、教师、学生赋予对应 `live:*` 权限（141~145）**
- [ ] **步骤 4：在根工程 `pom.xml` 中注册 `educloud-live` 模块**
- [ ] **步骤 5：Commit**

```bash
git add deploy/sql/ educloud-backend/pom.xml
git commit -m "feat(live): add live control plane database migration scripts and permissions"
```

---

### 任务 2：`educloud-live` 模块骨架与安全上下文配置

**文件：**
- 创建：`educloud-backend/educloud-live/pom.xml`
- 创建：`educloud-backend/educloud-live/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/LiveApplication.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/config/SecurityConfig.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/config/IdentifierConfig.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/config/RedisConfig.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/config/HealthIndicatorConfig.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/config/MybatisPlusConfig.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/config/InternalApiFilter.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/exception/LiveErrorCode.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/exception/LiveException.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/exception/GlobalExceptionHandler.java`

- [ ] **步骤 1：配置 `educloud-live/pom.xml` 包含 WebSocket、Web、Security、Redis、Feign、MyBatis-Plus 依赖，并配置 `spring-boot-maven-plugin` 的 `repackage` 目标**
- [ ] **步骤 2：编写 `application.yml`，设置 `server.port: 8095`、`management.server.port: 8096`、MySQL、Redis、Nacos 配置**
- [ ] **步骤 3：编写 `LiveApplication.java` 启动类，配置 `@EnableDiscoveryClient`、`@EnableFeignClients`**
- [ ] **步骤 4：编写 `SecurityConfig.java`，加载 JWKS、放行 `/actuator/health/**` 与内部接口、校验 `live:*` 权限**
- [ ] **步骤 5：编写错误码枚举 `LiveErrorCode` 与全局异常处理器 `GlobalExceptionHandler`**
- [ ] **步骤 6：运行 `mvn clean test` 验证模块骨架编译**
- [ ] **步骤 7：Commit**

```bash
git add educloud-backend/educloud-live/
git commit -m "feat(live): scaffold educloud-live module with security, redis, and error handling"
```

---

### 任务 3：领域实体、枚举与 Mapper 仓储层

**文件：**
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/enums/LiveRoomStatus.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/enums/LiveSessionStatus.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/enums/LiveMessageType.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/enums/LiveReplayStatus.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/enums/LiveProviderType.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/entity/LiveRoomEntity.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/entity/LiveSessionEntity.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/entity/LiveMessageEntity.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/entity/LiveAttendanceEntity.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/entity/LiveReplayEntity.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/mapper/LiveRoomMapper.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/mapper/LiveSessionMapper.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/mapper/LiveMessageMapper.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/mapper/LiveAttendanceMapper.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/mapper/LiveReplayMapper.java`

- [ ] **步骤 1：编写全部枚举类与 MyBatis-Plus 实体类（包含 `@Version` 乐观锁版本号、逻辑删除与审计字段）**
- [ ] **步骤 2：编写 Mapper 接口，包含 CAS 更新方法（`updateStatusCas`）**
- [ ] **步骤 3：编写 Mapper 单元测试验证实体映射**
- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-live/src/main/java/com/educloud/live/entity/ educloud-backend/educloud-live/src/main/java/com/educloud/live/enums/ educloud-backend/educloud-live/src/main/java/com/educloud/live/mapper/
git commit -m "feat(live): add domain entities, enums and mybatis-plus mappers"
```

---

### 任务 4：Stream Provider SPI 插件化推拉流控制面（含生产门控）

**文件：**
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/spi/model/LiveStreamPushUrl.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/spi/model/LiveStreamPlayUrls.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/spi/model/StreamStatus.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/spi/LiveStreamProvider.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/spi/LiveStreamProviderFactory.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/spi/plugins/MockLiveStreamProvider.java`
- 测试：`educloud-backend/educloud-live/src/test/java/com/educloud/live/spi/LiveStreamProviderTest.java`

- [ ] **步骤 1：编写 `LiveStreamProviderTest` 失败的单测（验证 Mock 插件生成合规签名与推拉流地址、测试生产环境门控拦截）**
- [ ] **步骤 2：运行测试验证失败**
- [ ] **步骤 3：编写 SPI 接口 `LiveStreamProvider` 与 `MockLiveStreamProvider` 实现（带生产门控与防盗链 MD5 计算）**
- [ ] **步骤 4：编写 `LiveStreamProviderFactory` 工厂，按类型自动路由**
- [ ] **步骤 5：运行测试验证通过**
- [ ] **步骤 6：Commit**

```bash
git add educloud-backend/educloud-live/src/main/java/com/educloud/live/spi/ educloud-backend/educloud-live/src/test/java/com/educloud/live/spi/
git commit -m "feat(live): implement stream provider SPI and mock sandbox plugin with prod gating"
```

---

### 任务 5：直播间生命周期核心服务（`LiveLifecycleService`）

**文件：**
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/service/LiveLifecycleService.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/service/impl/LiveLifecycleServiceImpl.java`
- 测试：`educloud-backend/educloud-live/src/test/java/com/educloud/live/service/LiveLifecycleServiceTest.java`

- [ ] **步骤 1：编写 `LiveLifecycleServiceTest` 失败的单测（覆盖创建、IDOR 教师归属拦截、开播 CAS 状态变更、生成推流地址、下播结课归档）**
- [ ] **步骤 2：运行测试验证失败**
- [ ] **步骤 3：编写 `LiveLifecycleServiceImpl` 实现创建、修改、开播、下播、状态机校验逻辑，确保外部调用隔离在事务外**
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-live/src/main/java/com/educloud/live/service/ educloud-backend/educloud-live/src/test/java/com/educloud/live/service/LiveLifecycleServiceTest.java
git commit -m "feat(live): implement live lifecycle service with strict CAS state transitions"
```

---

### 任务 6：选课权益联动与 60s 一次性 Ticket 签发/核销

**文件：**
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/feign/CourseClient.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/feign/dto/CourseEnrollmentStatusResponse.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/service/LiveTicketService.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/service/impl/LiveTicketServiceImpl.java`
- 测试：`educloud-backend/educloud-live/src/test/java/com/educloud/live/service/LiveTicketServiceTest.java`

- [ ] **步骤 1：编写 `LiveTicketServiceTest` 失败单测（测试未选课拦截、有效选课签发 60s Ticket、Redis GETDEL 原子核销、防重放）**
- [ ] **步骤 2：运行测试验证失败**
- [ ] **步骤 3：实现 `CourseClient` Feign 契约与 `LiveTicketServiceImpl`（外部 Feign 调用置于事务外）**
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-live/src/main/java/com/educloud/live/feign/ educloud-backend/educloud-live/src/main/java/com/educloud/live/service/LiveTicketService* educloud-backend/educloud-live/src/test/java/com/educloud/live/service/LiveTicketServiceTest.java
git commit -m "feat(live): implement single-use websocket connection ticket with course enrollment check"
```

---

### 任务 7：WebSocket 长连接 Handler 与 Redis 跨节点广播

**文件：**
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/websocket/model/WebSocketMessage.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/websocket/LiveWebSocketInterceptor.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/websocket/LiveWebSocketHandler.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/websocket/LiveBroadcastService.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/websocket/RedisMessageSubscriber.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/config/WebSocketConfig.java`
- 测试：`educloud-backend/educloud-live/src/test/java/com/educloud/live/websocket/LiveWebSocketSecurityTest.java`

- [ ] **步骤 1：编写 `LiveWebSocketSecurityTest` 失败单测（握手验票拦截非法请求、心跳维持、弹幕/禁言广播、消息撤回）**
- [ ] **步骤 2：运行测试验证失败**
- [ ] **步骤 3：编写 WebSocket 握手拦截器 `LiveWebSocketInterceptor`，使用 `LiveTicketService` 原子核销 Ticket**
- [ ] **步骤 4：编写 `LiveWebSocketHandler` 管理客户端连接、心跳、在线人数维护（`SADD/SREM`）**
- [ ] **步骤 5：编写 `LiveBroadcastService` 与 `RedisMessageSubscriber` 完成基于 Redis Pub/Sub 的全节点广播**
- [ ] **步骤 6：运行测试验证通过**
- [ ] **步骤 7：Commit**

```bash
git add educloud-backend/educloud-live/src/main/java/com/educloud/live/websocket/ educloud-backend/educloud-live/src/main/java/com/educloud/live/config/WebSocketConfig.java educloud-backend/educloud-live/src/test/java/com/educloud/live/websocket/
git commit -m "feat(live): implement websocket handler with redis pub-sub multi-instance broadcast"
```

---

### 任务 8：录制回放与 File 内部授权点播

**文件：**
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/feign/FileClient.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/feign/dto/FileBatchGrantRequest.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/feign/dto/FileGrantResponse.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/service/LiveReplayService.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/service/impl/LiveReplayServiceImpl.java`
- 测试：`educloud-backend/educloud-live/src/test/java/com/educloud/live/service/LiveReplayServiceTest.java`

- [ ] **步骤 1：编写 `LiveReplayServiceTest` 失败单测（测试回放列表查询、选课鉴权校验、File 签名 URL 批量换取、已撤销选课拦截）**
- [ ] **步骤 2：运行测试验证失败**
- [ ] **步骤 3：编写 `FileClient` 与 `LiveReplayServiceImpl`**
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-live/src/main/java/com/educloud/live/feign/FileClient* educloud-backend/educloud-live/src/main/java/com/educloud/live/service/LiveReplayService* educloud-backend/educloud-live/src/test/java/com/educloud/live/service/LiveReplayServiceTest.java
git commit -m "feat(live): implement replay recording management and file access authorization"
```

---

### 任务 9：REST 控制层与 WebMvc 接口测试

**文件：**
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/controller/LiveRoomController.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/controller/LiveMessageController.java`
- 创建：`educloud-backend/educloud-live/src/main/java/com/educloud/live/controller/LiveReplayController.java`
- 测试：`educloud-backend/educloud-live/src/test/java/com/educloud/live/controller/LiveRoomControllerTest.java`
- 测试：`educloud-backend/educloud-live/src/test/java/com/educloud/live/controller/LiveReplayControllerTest.java`

- [ ] **步骤 1：编写 Controller 测试类（覆盖分页安全上限 `safeSize`、参数校验、权限码注解、IDOR 拦截）**
- [ ] **步骤 2：运行测试验证失败**
- [ ] **步骤 3：编写 `LiveRoomController`、`LiveMessageController`、`LiveReplayController`**
- [ ] **步骤 4：运行测试验证通过**
- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-live/src/main/java/com/educloud/live/controller/ educloud-backend/educloud-live/src/test/java/com/educloud/live/controller/
git commit -m "feat(live): implement live REST controllers with full parameter and IDOR validation"
```

---

### 任务 10：全链路 E2E 自动化集成测试与运维闭环

**文件：**
- 创建：`educloud-backend/educloud-live/src/test/java/com/educloud/live/LiveFlowIntegrationTest.java`
- 修改：`deploy/scripts/start-dev.sh`（添加 `[8/9] educloud-live` 模块启动与 8096 就绪探针等待）
- 创建：`scratch/test_live_e2e.py`（自动化 E2E 验证脚本）

- [ ] **步骤 1：编写 `LiveFlowIntegrationTest.java` 覆盖开播 ➔ 选课校验 ➔ Ticket 签发 ➔ WS 握手 ➔ 实时互动 ➔ 下播 ➔ 回放签名 7 大阶段**
- [ ] **步骤 2：在全量微服务（10 个模块）上执行 `mvn clean test`，确认 100% BUILD SUCCESS**
- [ ] **步骤 3：在 `deploy/scripts/start-dev.sh` 中注册 `educloud-live` 启动逻辑（8095/8096 端口与环境变量）并适配 8096 就绪探针**
- [ ] **步骤 4：编写并运行 `scratch/test_live_e2e.py` 验证全链路闭环**
- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-live/src/test/java/com/educloud/live/LiveFlowIntegrationTest.java deploy/scripts/start-dev.sh scratch/test_live_e2e.py
git commit -m "test(live): add end-to-end integration test and update start-dev script for educloud-live"
```

---

## 自检检查清单

1. **规格覆盖度：** 直播间生命周期、Stream Provider SPI、60s 一次性 Ticket、Spring WebSocket + Redis Pub/Sub、录制回放 File 授权、8095/8096 双端口全部有对应任务覆盖。
2. **占位符扫描：** 无任何 TODO、TBD 或未完成代码步骤。
3. **类型与接口一致性：** `LiveRoomEntity`、`LiveSessionEntity`、`LiveMessageEntity`、`LiveReplayEntity`、`LiveStreamProvider`、`LiveTicketService` 命名全链路严格一致。
4. **M08 经验吸收：** `pom.xml` 显式声明 `repackage` 目标、Mock 生产环境门控、外部 Feign 隔离在事务外、`start-dev.sh` 端口与就绪探针适配。
