# EduCloud M01 Common 模块实施计划

> **面向 AI 代理的工作者：** 使用 `superpowers:executing-plans` 在当前对话内逐任务执行；用户已明确禁止使用子智能体。步骤使用复选框（`- [ ]`）跟踪，未经本计划书面审阅确认不得创建模块或 Java 代码。

**目标：** 交付不可独立运行的 `educloud-common` Maven JAR，为后续模块提供稳定且无领域归属的响应、错误、请求、安全上下文、分布式 ID、事件和幂等契约，并以单元测试、自动配置测试及真实 Redis Testcontainers 集成测试固定行为。

**架构：** 父工程只新增 `educloud-common` 一个模块。公共能力分为纯 Java 契约层和条件自动配置层；Servlet、Spring Security、Redis 只在相应类型和运行环境存在时激活。ID 生成使用 63 位 Snowflake 布局，Worker 槽位由 Redis 原子租约分配，任何租约不确定性均失败关闭。Common 不拥有数据库、不启动服务、不发布消息、不实现领域逻辑。

**技术栈：** Java 17 字节码（JDK 17/21 构建）、Maven 3.9+、Spring Boot 3.2.5、Spring Framework 6.1、Jackson、Jakarta Validation、Spring Security Core（可选）、Spring Data Redis（可选）、JUnit 5、AssertJ、Mockito、ApplicationContextRunner、Testcontainers 1.19.7、Redis 7.2.5。

**批准规格：** [`2026-08-20-educloud-common-design.md`](../specs/2026-08-20-educloud-common-design.md)

---

## 执行规则

- 严格按任务 1～10 顺序执行；每个行为先写测试并看到预期红灯，再写最小实现。
- 每个任务的提交只包含该任务列出的文件；提交前运行该任务的绿灯命令和 `git diff --check`。
- 不新增 `Application`、`main()`、Controller、业务 Service、Entity、Mapper、业务 Repository、数据库迁移或服务端口。
- 不引入 `spring-boot-starter-web`、WebFlux Starter、MySQL、MyBatis-Plus、RabbitMQ、Nacos或任何业务服务依赖。
- `src/test` 可以使用测试 Starter 和 Redis 客户端；依赖边界检查只允许这些依赖出现在测试作用域。
- 默认构建不启动容器；`-Pintegration` 才运行 `*IT`。集成测试必须使用随机环境名和自己的 Redis Testcontainer，不能连接当前共享 Redis。
- 任何测试失败先按 `superpowers:systematic-debugging` 查明原因；宣称完成前使用 `superpowers:verification-before-completion`。

## 目标文件图

```text
educloud-backend/
├─ pom.xml
└─ educloud-common/
   ├─ pom.xml
   └─ src/
      ├─ main/java/com/educloud/common/
      │  ├─ api/{ApiResponse,ApiResponseFactory,PageResponse}.java
      │  ├─ error/{ErrorCode,CommonErrorCode,ErrorDetails,FieldViolation,
      │  │          ValidationErrorDetails,BusinessException}.java
      │  ├─ web/{RequestContext,RequestContextAccessor,RequestIdPolicy,
      │  │        ServletRequestContextAccessor,RequestContextFilter,
      │  │        GlobalExceptionHandler}.java
      │  ├─ security/{AuthenticatedUser,SecurityContextFacade,
      │  │             SpringSecurityContextFacade}.java
      │  ├─ messaging/EventEnvelope.java
      │  ├─ idempotency/IdempotencyKey.java
      │  ├─ id/{IdentifierGenerator,IdentifierUnavailableException,Sleeper,
      │  │       WorkerLeaseGuard,WorkerLeaseGrant,WorkerLeaseRepository,
      │  │       WorkerLeaseManager,WorkerLeaseIdentifierGenerator,
      │  │       RedisWorkerLeaseRepository}.java
      │  └─ config/{CommonProperties,CommonIdentifierProperties,
      │             CommonCoreAutoConfiguration,CommonServletWebAutoConfiguration,
      │             CommonSecurityAutoConfiguration,CommonIdentifierAutoConfiguration}.java
      ├─ main/resources/
      │  ├─ META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
      │  ├─ META-INF/additional-spring-configuration-metadata.json
      │  └─ com/educloud/common/id/{acquire-worker,renew-worker,release-worker}.lua
      └─ test/java/com/educloud/common/...
deploy/tests/common-module-contract-tests.sh
```

## 任务 1：建立模块骨架和依赖边界门禁

**文件：**

- 修改：`educloud-backend/pom.xml`
- 创建：`educloud-backend/educloud-common/pom.xml`
- 创建：`deploy/tests/common-module-contract-tests.sh`
- 修改：`educloud-backend/README.md`

- [ ] **步骤 1：先写模块契约测试**

脚本必须使用 `set -euo pipefail`，并检查：父 POM 的 `<modules>` 精确为 `educloud-common`；子模块 packaging 为 JAR；主源码没有 `main(`、`@SpringBootApplication`、`@Controller`、`@RestController`、`@Entity`、`Mapper`；依赖树的 compile/runtime 范围不含下列坐标：

```text
spring-boot-starter-web
spring-boot-starter-webflux
spring-boot-starter-jdbc
mysql-connector-j
mybatis-plus
spring-boot-starter-amqp
spring-cloud-starter-alibaba-nacos-discovery
```

依赖树检查命令固定为：

```bash
mvn -q -f educloud-backend/pom.xml \
  -pl educloud-common dependency:tree \
  -Dscope=runtime \
  -DoutputFile=target/runtime-dependencies.txt
```

运行：

```bash
bash deploy/tests/common-module-contract-tests.sh
```

预期：失败并明确指出父 POM 尚未声明 `educloud-common` 或子 POM 不存在。

- [ ] **步骤 2：创建最小模块 POM**

父 POM 只加入：

```xml
<modules>
    <module>educloud-common</module>
</modules>
```

父属性补充并锁定：

```xml
<maven-failsafe-plugin.version>3.2.5</maven-failsafe-plugin.version>
<mockito.version>5.11.0</mockito.version>
<testcontainers.version>1.19.7</testcontainers.version>
```

子 POM 的正式依赖只允许：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-web</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-webmvc</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-core</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.data</groupId>
        <artifactId>spring-data-redis</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
    <dependency>
        <groupId>jakarta.validation</groupId>
        <artifactId>jakarta.validation-api</artifactId>
    </dependency>
    <dependency>
        <groupId>jakarta.servlet</groupId>
        <artifactId>jakarta.servlet-api</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

测试作用域加入 `spring-boot-starter-test`、`spring-boot-starter-web`、`spring-boot-starter-security`、`spring-boot-starter-data-redis`、`org.testcontainers:junit-jupiter`、`org.testcontainers:testcontainers` 和 `org.awaitility:awaitility`。Mockito 和 Testcontainers 使用上面的锁定版本。配置 Surefire 跑 `*Test`，Failsafe 跑 `*IT`；属性 `skipITs=true`，`integration` profile 将其改为 `false`。

- [ ] **步骤 3：更新 README 的事实状态**

把当前状态改为 `【M01 计划已批准，实施中】`，明确 Common 是库而非服务；保留 M02～M13 尚未实现的说明，不写“后端已完成”。

- [ ] **步骤 4：运行绿灯**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common help:effective-pom
bash deploy/tests/common-module-contract-tests.sh
mvn -f educloud-backend/pom.xml -pl educloud-common test
git diff --check
```

预期：全部返回 0；测试输出为 `No tests to run` 或 0 tests，模块仅产生普通 JAR 生命周期，不出现可运行应用。

- [ ] **步骤 5：提交**

```bash
git add -- educloud-backend/pom.xml educloud-backend/educloud-common/pom.xml educloud-backend/README.md deploy/tests/common-module-contract-tests.sh
git commit -m "build(common): add module boundary"
```

## 任务 2：实现统一响应和分页值对象

**文件：**

- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/api/ApiResponseTest.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/api/PageResponseTest.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/api/ApiResponse.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/api/PageResponse.java`

- [ ] **步骤 1：写失败测试**

测试必须固定以下调用和断言：

```java
var instant = Instant.parse("2026-08-20T08:00:00Z");
var response = new ApiResponse<>("SUCCESS", "OK", Map.of("id", "42"), "req-1", instant);
assertThat(objectMapper.writeValueAsString(response))
    .isEqualTo("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"id\":\"42\"},\"requestId\":\"req-1\",\"timestamp\":\"2026-08-20T08:00:00Z\"}");

var source = new ArrayList<>(List.of("a", "b"));
var page = PageResponse.of(source, 2, 2, 5);
source.add("c");
assertThat(page.items()).containsExactly("a", "b");
assertThat(page.totalPages()).isEqualTo(3);
assertThatThrownBy(() -> page.items().add("c")).isInstanceOf(UnsupportedOperationException.class);
```

另测 `total=0`、整除、`page<1`、`pageSize<1`、`total<0` 和 `items=null`。

运行：

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dtest=ApiResponseTest,PageResponseTest test
```

预期：测试编译失败，缺少两个生产类型。

- [ ] **步骤 2：实现不可变记录**

公共 API 固定为：

```java
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        Instant timestamp) {
    public ApiResponse {
        code = requireText(code, "code");
        message = Objects.requireNonNull(message, "message");
        requestId = requireText(requestId, "requestId");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }
}

public record PageResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        long totalPages) {
    public PageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (page < 1) throw new IllegalArgumentException("page must be at least 1");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        if (total < 0) throw new IllegalArgumentException("total must not be negative");
        var expected = total == 0 ? 0 : 1 + ((total - 1) / pageSize);
        if (totalPages != expected) throw new IllegalArgumentException("totalPages does not match total and pageSize");
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long total) {
        if (page < 1) throw new IllegalArgumentException("page must be at least 1");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        if (total < 0) throw new IllegalArgumentException("total must not be negative");
        long totalPages = total == 0 ? 0 : 1 + ((total - 1) / pageSize);
        return new PageResponse<>(items, page, pageSize, total, totalPages);
    }
}
```

`requireText` 是各 record 内部的私有静态方法，不新增万能字符串工具类。

- [ ] **步骤 3：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dtest=ApiResponseTest,PageResponseTest test
git diff --check
git add -- educloud-backend/educloud-common/src/main/java/com/educloud/common/api educloud-backend/educloud-common/src/test/java/com/educloud/common/api
git commit -m "feat(common): add response contracts"
```

预期：目标测试全部通过。

## 任务 3：实现请求上下文、请求 ID 和响应工厂

**文件：**

- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/web/RequestContext.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/web/RequestContextAccessor.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/web/RequestIdPolicy.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/web/ServletRequestContextAccessor.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/web/RequestContextFilter.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/api/ApiResponseFactory.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/web/RequestIdPolicyTest.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/web/RequestContextFilterTest.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/api/ApiResponseFactoryTest.java`

- [ ] **步骤 1：写请求 ID 和 MDC 红灯测试**

覆盖合法值保留、非法字符/空/超长值替换、生成值符合 UUID；过滤器测试覆盖正常和抛异常两条路径：

```java
assertThat(response.getHeader("X-Request-Id")).isEqualTo("client.req-1");
assertThat(request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE)).isEqualTo("client.req-1");
assertThat(MDC.get("requestId")).isNull();
```

还要先向 MDC 写入 `outer-request`，验证过滤器结束后恢复原值而不是一律删除。Trace 测试只接受显式提供的真实 trace supplier；无值时保持空。

响应工厂使用固定 `Clock`，断言成功响应为 `SUCCESS/OK`，`requestId` 来自 accessor，时间精确等于固定值。

运行目标测试，预期因类型缺失编译失败。

- [ ] **步骤 2：实现上下文契约和过滤器**

固定接口：

```java
public record RequestContext(String requestId, @Nullable String traceId) {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = RequestContext.class.getName() + ".requestId";
}

public interface RequestContextAccessor {
    String requestId();
    Optional<String> traceId();
}
```

`RequestIdPolicy` 使用预编译正则 `[A-Za-z0-9._-]{1,64}`，构造器接收 `Supplier<UUID>`。`RequestContextFilter` 继承 `OncePerRequestFilter`，在调用 filter chain 前写 request attribute、MDC 和 response header，并在 `finally` 恢复进入过滤器前的 MDC 值。不得读取或生成身份头，不得将 requestId 写入 traceId。

`ServletRequestContextAccessor` 只从当前请求属性读取 requestId；无请求时返回由 policy 生成的值但不保存到静态变量。Trace 通过可选的 Micrometer `Tracer` 读取 `currentSpan().context().traceId()`；无 Tracer 或无当前 Span 时返回空。测试用 mock Tracer 固定真实 trace 值，禁止回退到 requestId。

- [ ] **步骤 3：实现响应工厂**

```java
public final class ApiResponseFactory {
    private final RequestContextAccessor requestContext;
    private final Clock clock;

    public <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "OK", data,
                requestContext.requestId(), clock.instant());
    }

    public <T> ApiResponse<T> error(ErrorCode code, String message, T details) {
        return new ApiResponse<>(code.code(), message, details,
                requestContext.requestId(), clock.instant());
    }
}
```

构造器参数非空；错误消息必须由调用方显式传入安全消息。

- [ ] **步骤 4：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dtest=RequestIdPolicyTest,RequestContextFilterTest,ApiResponseFactoryTest test
git diff --check
git add -- educloud-backend/educloud-common/src/main/java/com/educloud/common/web educloud-backend/educloud-common/src/main/java/com/educloud/common/api/ApiResponseFactory.java educloud-backend/educloud-common/src/test/java/com/educloud/common/web educloud-backend/educloud-common/src/test/java/com/educloud/common/api/ApiResponseFactoryTest.java
git commit -m "feat(common): add request context"
```

## 任务 4：实现稳定错误契约和 Servlet 异常映射

**文件：**

- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/error/ErrorCode.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/error/CommonErrorCode.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/error/ErrorDetails.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/error/FieldViolation.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/error/ValidationErrorDetails.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/error/BusinessException.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/web/GlobalExceptionHandler.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/error/BusinessExceptionTest.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/web/GlobalExceptionHandlerTest.java`

- [ ] **步骤 1：先写异常映射红灯测试**

使用 `@WebMvcTest` 的测试 Controller 触发 Bean Validation、不可读 JSON、七类 `BusinessException` 和未知异常。必须断言 HTTP 状态、业务码、`X-Request-Id` 与响应体 requestId 一致；500 的 JSON 不含原异常消息、类名、SQL、主机名或堆栈。使用测试 Logback appender 断言未知异常恰好记录一次 ERROR。

状态矩阵固定为：

| 错误码 | HTTP |
|---|---:|
| `VALIDATION_FAILED` | 400 |
| `UNAUTHENTICATED` | 401 |
| `ACCESS_DENIED` | 403 |
| `VERSION_CONFLICT` | 409 |
| `RATE_LIMITED` | 429 |
| `DEPENDENCY_UNAVAILABLE` | 503 |
| `INTERNAL_ERROR` | 500 |

运行目标测试，预期编译失败。

- [ ] **步骤 2：实现错误类型**

```java
public interface ErrorCode {
    String code();
    int httpStatus();
    String defaultMessage();
}

public interface ErrorDetails {}

public record FieldViolation(String field, String code, String message) {}

public record ValidationErrorDetails(List<FieldViolation> violations)
        implements ErrorDetails {
    public ValidationErrorDetails {
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
    }
}
```

`CommonErrorCode` 枚举实现上表。`BusinessException` 只保存 `ErrorCode`、面向客户端的安全 message 和 `@Nullable ErrorDetails`；不得把 arbitrary Object、Throwable 字段或堆栈放进响应详情。cause 只保留在异常链供日志使用。

- [ ] **步骤 3：实现异常处理器**

`GlobalExceptionHandler` 使用 `@RestControllerAdvice`：

- `MethodArgumentNotValidException` 和 `HandlerMethodValidationException` 转为排序稳定的 `ValidationErrorDetails`。
- `HttpMessageNotReadableException` 返回 400，不回显解析器内部消息。
- `BusinessException` 按自身 ErrorCode 返回，不重复记录完整堆栈。
- `Exception` 只调用一次 `log.error("Unhandled request failure requestId={}", requestId, exception)`，客户端固定 `INTERNAL_ERROR/Internal server error`。
- 所有分支以 `ResponseEntity.status(code.httpStatus())` 返回，并写相同 `X-Request-Id`。

- [ ] **步骤 4：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dtest=BusinessExceptionTest,GlobalExceptionHandlerTest test
git diff --check
git add -- educloud-backend/educloud-common/src/main/java/com/educloud/common/error educloud-backend/educloud-common/src/main/java/com/educloud/common/web/GlobalExceptionHandler.java educloud-backend/educloud-common/src/test/java/com/educloud/common/error educloud-backend/educloud-common/src/test/java/com/educloud/common/web/GlobalExceptionHandlerTest.java
git commit -m "feat(common): add error contracts"
```

## 任务 5：实现只读安全上下文

**文件：**

- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/security/AuthenticatedUser.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/security/SecurityContextFacade.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/security/SpringSecurityContextFacade.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/security/AuthenticatedUserTest.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/security/SpringSecurityContextFacadeTest.java`

- [ ] **步骤 1：写红灯测试**

测试防御性复制、不可修改、空白字段拒绝；认证 principal 为 `AuthenticatedUser` 时返回该值，匿名、未认证、字符串 principal 均返回 `Optional.empty()`。带伪造 `X-User-Id`/`X-Role` 的 Mock request 不能影响结果。

- [ ] **步骤 2：实现最小接口**

```java
public record AuthenticatedUser(
        String userId,
        String sessionId,
        Set<String> roles,
        Set<String> permissions) {
    public AuthenticatedUser {
        userId = requireText(userId, "userId");
        sessionId = requireText(sessionId, "sessionId");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }
}

public interface SecurityContextFacade {
    Optional<AuthenticatedUser> currentUser();
}
```

`SpringSecurityContextFacade` 只读取 `SecurityContextHolderStrategy.getContext().getAuthentication()`，要求 `isAuthenticated()`、不是 `AnonymousAuthenticationToken` 且 principal 类型为 `AuthenticatedUser`。它不解析 JWT、请求头或权限字符串。

- [ ] **步骤 3：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dtest=AuthenticatedUserTest,SpringSecurityContextFacadeTest test
git diff --check
git add -- educloud-backend/educloud-common/src/main/java/com/educloud/common/security educloud-backend/educloud-common/src/test/java/com/educloud/common/security
git commit -m "feat(common): add security context"
```

## 任务 6：实现事件信封和幂等值语义

**文件：**

- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/messaging/EventEnvelope.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/idempotency/IdempotencyKey.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/messaging/EventEnvelopeTest.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/idempotency/IdempotencyKeyTest.java`

- [ ] **步骤 1：写红灯测试**

`EventEnvelopeTest` 固定 JSON 字段名和顺序，验证必填文本、`eventVersion>=1`、`sourceSequence>=0`、`aggregateVersion>=0`、非空 data 和 UTC Instant。另证明确保三个版本字段互不推导。

`IdempotencyKeyTest` 验证：完全相同为同一请求；actor/operation/key 相同但 digest 不同为冲突；任一 scope 字段不同不冲突；所有字段 trim 后仍不能为空。

- [ ] **步骤 2：实现纯值对象**

```java
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        String sourceService,
        long sourceSequence,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String requestId,
        @Nullable String traceId,
        T data) { /* compact constructor performs only the validations above */ }

public record IdempotencyKey(
        String actorId,
        String operation,
        String key,
        String requestDigest) {
    public boolean sameScope(IdempotencyKey other) { /* actorId + operation + key */ }
    public boolean representsSameRequest(IdempotencyKey other) {
        return sameScope(other) && requestDigest.equals(other.requestDigest);
    }
    public boolean conflictsWith(IdempotencyKey other) {
        return sameScope(other) && !requestDigest.equals(other.requestDigest);
    }
}
```

两个类型不得依赖 Spring、Redis、RabbitMQ、Repository 或 I/O。

- [ ] **步骤 3：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dtest=EventEnvelopeTest,IdempotencyKeyTest test
git diff --check
git add -- educloud-backend/educloud-common/src/main/java/com/educloud/common/messaging educloud-backend/educloud-common/src/main/java/com/educloud/common/idempotency educloud-backend/educloud-common/src/test/java/com/educloud/common/messaging educloud-backend/educloud-common/src/test/java/com/educloud/common/idempotency
git commit -m "feat(common): add message value contracts"
```

## 任务 7：实现租约感知的 63 位 ID 算法

**文件：**

- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/id/IdentifierGenerator.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/id/IdentifierUnavailableException.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/id/Sleeper.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/id/WorkerLeaseGuard.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/id/WorkerLeaseIdentifierGenerator.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/id/WorkerLeaseIdentifierGeneratorTest.java`

- [ ] **步骤 1：写算法红灯测试**

用可变 `Clock`、记录等待时间的 fake `Sleeper` 和 fake guard 覆盖：

1. ID 为正数，解码后时间 41 位、worker 5 位、sequence 17 位正确。
2. 同一毫秒序列从 0 单调增加；下一毫秒归零。
3. 第 131072 个序列后等待下一毫秒，禁止回绕。
4. 回拨 1～5ms 等待恢复；回拨 6ms 抛 `IdentifierUnavailableException`。
5. worker 小于 0 或大于 31 被拒绝。
6. guard 在每一次 `nextId()` 前被调用；失效后不再发号。
7. 每次成功发号把 timestamp 写回 guard，供释放水位使用。

运行目标测试，预期编译失败。

- [ ] **步骤 2：实现接口和位布局**

```java
public interface IdentifierGenerator {
    long nextId();
}

@FunctionalInterface
public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
}

public interface WorkerLeaseGuard {
    int requireActiveWorkerId();
    void recordIssuedTimestamp(long epochMillis);
}
```

生成器常量固定为：

```java
static final long EPOCH_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
static final int SEQUENCE_BITS = 17;
static final int WORKER_BITS = 5;
static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
static final int WORKER_SHIFT = SEQUENCE_BITS;
static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS;
```

同步区内先 `requireActiveWorkerId()`，再读取 clock。小幅回拨调用 sleeper 逐毫秒等待；中断时恢复中断标记并失败关闭。组合前校验 timestamp 未早于 epoch、未溢出 41 位。成功组合后调用 `recordIssuedTimestamp(currentMillis)`；禁止用随机 worker 或本地 fallback。

- [ ] **步骤 3：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dtest=WorkerLeaseIdentifierGeneratorTest test
git diff --check
git add -- educloud-backend/educloud-common/src/main/java/com/educloud/common/id educloud-backend/educloud-common/src/test/java/com/educloud/common/id/WorkerLeaseIdentifierGeneratorTest.java
git commit -m "feat(common): add lease aware identifiers"
```

## 任务 8：实现 Redis Worker 租约和生命周期

**文件：**

- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/id/WorkerLeaseGrant.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/id/WorkerLeaseRepository.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/id/RedisWorkerLeaseRepository.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/id/WorkerLeaseManager.java`
- 创建：`educloud-backend/educloud-common/src/main/resources/com/educloud/common/id/acquire-worker.lua`
- 创建：`educloud-backend/educloud-common/src/main/resources/com/educloud/common/id/renew-worker.lua`
- 创建：`educloud-backend/educloud-common/src/main/resources/com/educloud/common/id/release-worker.lua`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/id/WorkerLeaseManagerTest.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/id/RedisWorkerLeaseScriptsTest.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/id/RedisWorkerLeaseRepositoryIT.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/id/RedisIdentifierConcurrencyIT.java`

- [ ] **步骤 1：写 Manager 红灯测试**

使用 fake repository、可控 `LongSupplier nanoTime` 和直接执行的 fake scheduler，覆盖：获取 0～31、无槽位时 start 失败、10 秒续租、30 秒本地截止、续租失败立即失效、owner 变化失效、每次发号前截止检查、正常 stop 带最后发号水位释放、未启动和 stop 后拒绝发号。断言 owner 是每实例唯一 UUID。

同时先创建两个真实 Redis `*IT`。容器固定为 `redis:7.2.5-alpine`，每个测试使用 `"it-" + UUID.randomUUID()` 环境名；覆盖 32 槽、第 33 个失败、owner 保护、续租、释放水位、TTL、两个 manager 并发 100,000 个 ID 及 Redis 中断后失败关闭。

运行：

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dtest=WorkerLeaseManagerTest,RedisWorkerLeaseScriptsTest test
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dit.test=RedisWorkerLeaseRepositoryIT,RedisIdentifierConcurrencyIT \
  verify -Pintegration
```

预期：测试编译失败，缺少租约仓储和 Manager 类型；必须确认 Failsafe 列出了两个 IT，不能把 skip 当成红灯。

- [ ] **步骤 2：定义仓储边界**

```java
public record WorkerLeaseGrant(
        int workerId,
        String ownerId,
        long redisTimeMillis) {}

public interface WorkerLeaseRepository {
    Optional<WorkerLeaseGrant> tryAcquire(
            String environment, String ownerId, Duration leaseTtl);
    Optional<WorkerLeaseGrant> renew(
            String environment, int workerId, String ownerId, Duration leaseTtl,
            long lastIssuedTimestamp);
    boolean release(
            String environment, int workerId, String ownerId,
            long lastIssuedTimestamp);
}
```

`WorkerLeaseManager` 实现 `WorkerLeaseGuard`、`SmartLifecycle`、`AutoCloseable`。成功获取或续租时把本地确认截止设为 `nanoTime + ttl`；每次发号读取 monotonic time，不以可回拨 wall clock 判断租约。任何 Redis 异常、空续租结果或截止超时都原子切换为失效状态。正常 stop 尝试 owner-checked release；释放异常只记录告警且本地立即失效。

- [ ] **步骤 3：实现 Lua 原子语义**

所有 key 使用同一 Redis Cluster hash tag：

```text
educloud:{<environment>:id-workers}:lease:<0..31>
educloud:{<environment>:id-workers}:watermark:<0..31>
```

`acquire-worker.lua` 接收 32 个 lease key 和 32 个 watermark key，使用 Redis `TIME`：只在 lease 不存在且 `redisNowMillis > watermark` 时执行 `SET key owner NX PX ttl`，返回 `{workerId, redisNowMillis}`；无槽位返回 `{-1, redisNowMillis}`。

`renew-worker.lua` 先比较 `GET leaseKey == owner`；匹配时将 watermark 更新为 `max(existing,lastIssuedTimestamp)`、执行 `PEXPIRE` 并返回 `{1, redisNowMillis}`，否则 `{0, redisNowMillis}`。

`release-worker.lua` 只在 owner 匹配时原子更新最大 watermark 并删除 lease。错误 owner 返回 0，不得续租、释放或改写水位。

`RedisWorkerLeaseRepository` 用 `DefaultRedisScript<List>` 执行脚本，严格校验返回元素数量、worker 范围和数值类型；脚本或返回协议异常统一抛 `IdentifierUnavailableException`，不吞 Redis 错误。

- [ ] **步骤 4：写脚本文本契约测试并运行绿灯**

文本契约至少断言三个脚本都校验 owner；acquire 使用 `TIME`、`NX`、`PX` 和 32 槽循环；renew/release 更新水位；不存在 `math.random`。

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dtest=WorkerLeaseManagerTest,RedisWorkerLeaseScriptsTest test
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dit.test=RedisWorkerLeaseRepositoryIT,RedisIdentifierConcurrencyIT \
  verify -Pintegration
git diff --check
git add -- educloud-backend/educloud-common/src/main/java/com/educloud/common/id educloud-backend/educloud-common/src/main/resources/com/educloud/common/id educloud-backend/educloud-common/src/test/java/com/educloud/common/id
git commit -m "feat(common): add redis worker leases"
```

## 任务 9：实现条件自动配置和配置元数据

**文件：**

- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/config/CommonProperties.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/config/CommonIdentifierProperties.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/config/CommonCoreAutoConfiguration.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/config/CommonServletWebAutoConfiguration.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/config/CommonSecurityAutoConfiguration.java`
- 创建：`educloud-backend/educloud-common/src/main/java/com/educloud/common/config/CommonIdentifierAutoConfiguration.java`
- 创建：`educloud-backend/educloud-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 创建：`educloud-backend/educloud-common/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/config/CommonAutoConfigurationTest.java`

- [ ] **步骤 1：写 ApplicationContextRunner 红灯测试**

用 `ApplicationContextRunner`、`WebApplicationContextRunner` 和 `ReactiveWebApplicationContextRunner` 覆盖：

- 非 Web 只加载 Core，不加载 Filter/Advice/Security。
- Servlet 加载 Filter、Accessor、Advice；Reactive 不出现任何 Servlet bean。
- classpath 移除 Spring Security 后不加载安全 facade。
- `educloud.common.id.enabled=false` 或没有 `StringRedisTemplate` 时不加载租约和生成器。
- ID 启用且 Redis bean 存在时加载完整链路。
- 用户自定义 `Clock`、`RequestContextAccessor`、`SecurityContextFacade`、`IdentifierGenerator` 时默认 bean 退让。
- environment 空白、TTL 非正、续租间隔不小于 TTL、回拨容忍为负时 context 启动失败。
- Imports 文件四个配置类各出现一次且能被 `AutoConfigurations.of(...)` 发现。

- [ ] **步骤 2：实现属性和自动配置**

属性绑定固定为：

```java
@ConfigurationProperties("educloud.common")
public class CommonProperties {
    @NotBlank private String environment = "local";
}

@ConfigurationProperties("educloud.common.id")
public class CommonIdentifierProperties {
    private boolean enabled = false;
    @NotNull private Duration leaseTtl = Duration.ofSeconds(30);
    @NotNull private Duration renewalInterval = Duration.ofSeconds(10);
    @NotNull private Duration clockBackwardTolerance = Duration.ofMillis(5);
    @AssertTrue(message = "durations must be non-negative and renewal-interval must be less than lease-ttl")
    public boolean isDurationConfigurationValid() {
        return !leaseTtl.isNegative() && !leaseTtl.isZero()
                && !renewalInterval.isNegative() && !renewalInterval.isZero()
                && !clockBackwardTolerance.isNegative()
                && renewalInterval.compareTo(leaseTtl) < 0;
    }
}
```

四个 `@AutoConfiguration` 的职责严格为：

- Core：UTC `Clock`、JavaTimeModule、`ApiResponseFactory`，均 `@ConditionalOnMissingBean`。
- Servlet Web：`@ConditionalOnWebApplication(SERVLET)` 且 Servlet/MVC 类存在时提供 request policy、accessor、filter、handler。
- Security：Servlet + Security 类存在时提供 `SpringSecurityContextFacade`。
- Identifier：`educloud.common.id.enabled=true`、`StringRedisTemplate` 存在时提供 repository、manager、generator；Manager 的生命周期由 Spring 关闭。

不得用 `@ComponentScan`；所有 bean 显式声明。Imports 文件每行一个全限定类名。元数据为五个属性提供类型、默认值和说明，并明确 environment 是 Redis ID 命名空间而不是租户。

- [ ] **步骤 3：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common \
  -Dtest=CommonAutoConfigurationTest test
git diff --check
git add -- educloud-backend/educloud-common/src/main/java/com/educloud/common/config educloud-backend/educloud-common/src/main/resources/META-INF educloud-backend/educloud-common/src/test/java/com/educloud/common/config
git commit -m "feat(common): add conditional auto configuration"
```

## 任务 10：真实 Redis 集成、全量门禁和文档收口

**文件：**

- 修改：`educloud-backend/README.md`
- 修改：`docs/superpowers/specs/2026-08-20-educloud-common-design.md`
- 修改：`docs/superpowers/plans/2026-08-20-educloud-common.md`

- [ ] **步骤 1：运行 M01 全部验证**

依次运行并保存退出码与测试摘要：

```bash
mvn -f educloud-backend/pom.xml -pl educloud-common test
bash deploy/tests/common-module-contract-tests.sh
mvn -f educloud-backend/pom.xml -pl educloud-common -am verify
mvn -f educloud-backend/pom.xml -pl educloud-common -am verify -Pintegration
mvn -f educloud-backend/pom.xml verify
git diff --check
git status --short
```

预期：所有命令返回 0；`-Pintegration` 明确显示两个 `*IT` 被执行；默认 `verify` 不启动容器；依赖边界无违规；没有任何服务启动或数据库迁移。

- [ ] **步骤 2：执行 JDK 17/21 构建矩阵和 Rocky 目标机复核**

分别显式切换到 JDK 17 和 JDK 21，两个 JDK 都运行 `-am verify`；在当前已验证的 Rocky 工作目录至少使用 JDK 21 再跑单元和集成门禁：

```bash
java -version
mvn --version
mvn -f educloud-backend/pom.xml -pl educloud-common -am verify
mvn -f educloud-backend/pom.xml -pl educloud-common -am verify -Pintegration
```

预期：JDK 17 和 JDK 21 的版本证据及构建退出码均为 0；Rocky 使用 JDK 21、Maven 3.9+。Testcontainers 启动独立随机命名 Redis，不修改现有 `educloud-redis-1`。若任一 JDK 尚不可用，明确标记未验证且不得完成 M01。数据库迁移和服务启动门禁标记 `N/A（Common 为普通 JAR 且无数据库）`。

- [ ] **步骤 3：范围审查和质量审查**

先逐条对照批准规格检查完整性和禁止项，再检查 API 可用性、并发安全、Redis Lua 原子性、异常信息泄露、自动配置条件、测试确定性和资源释放。修复任何问题后重跑步骤 1 和步骤 2 的相关命令；不得以“测试未运行”代替通过。

- [ ] **步骤 4：更新事实状态**

只有步骤 1～3 都通过后：

- README 改为 `【M01 已实现并验证，等待用户验收】`。
- 设计规格状态改为 `已实现并验证，等待用户验收`。
- 本计划所有实际完成项勾选；在末尾记录验证日期、命令、退出码、测试数量和 N/A 门禁。
- 不把 M02 Gateway 标记为开始或已实现。

- [ ] **步骤 5：提交 M01 收口**

```bash
git add -- educloud-backend/README.md docs/superpowers/specs/2026-08-20-educloud-common-design.md docs/superpowers/plans/2026-08-20-educloud-common.md
git commit -m "docs(common): record M01 verification"
git status --short
```

预期：提交成功且工作区干净。向用户报告 M01 的实际证据并等待明确确认；未经确认不进入 M02。

## 完成定义

M01 只有同时满足以下条件才算完成：

1. 父 POM 只新增 `educloud-common`，Java 17/21 构建均通过。
2. API、分页、错误、请求 ID、安全上下文、事件和幂等契约有确定性测试。
3. ID 位布局、序列耗尽、回拨、租约截止、32 槽限制和失败关闭均有单元测试。
4. Redis 7.2.5 Testcontainers 验证真实 Lua 获取、续租、释放、水位、TTL、并发唯一性和中断行为。
5. Servlet/Security/Redis 自动配置严格条件化，Reactive 环境不加载 Servlet bean。
6. 正式依赖不包含禁止项，没有启动类、业务领域代码、数据库或消息 I/O。
7. 默认和 integration Maven 门禁、依赖契约、范围审查、质量审查全部通过。
8. Rocky Linux 8.9 / JDK 21 证据通过，或被明确标记为尚未验证；后者不得宣称 M01 完成。
9. 变更仅限 M01，工作区干净，用户确认后才允许开始 M02。
