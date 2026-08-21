# EduCloud M02 Gateway 模块实施计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:executing-plans` 在当前对话内逐任务实现；用户已明确禁止使用子智能体。步骤使用复选框（`- [ ]`）跟踪，未经本计划书面审阅确认不得创建 Gateway 模块或 Java 代码。

> **状态：** M02 已实现并验证，等待用户验收（任务 13A 私有 Testcontainers 镜像覆盖已实现并验证）。

**目标：** 交付可独立运行的 `educloud-gateway` Reactive Spring Cloud Gateway，为 11 个未来业务服务建立静态路由、安全 JWT 消费、Redis 权威会话撤销、分布式入口限流、Web 防护、统一 Gateway 错误和可观测性基线。

**架构：** Gateway 使用 WebFlux 和显式版本化路由，Nacos 只承担配置与服务发现，不启用自动路由。所有携带 Bearer Token 的请求都先完成 RS256/JWKS 验签；受保护请求及携带 Token 的匿名请求逐次读取 Redis 会话，随后才允许转发。登录账号和客户端 IP 通过 HMAC 摘要进入 Redis Lua Token Bucket，Gateway 不拥有数据库、不签发 Token，也不执行领域授权。

**技术栈：** Java 17 字节码（JDK 17/21 构建）、Maven 3.9+、Spring Boot 3.2.5、Spring Cloud 2023.0.3、Spring Cloud Alibaba 2023.0.1.0、Spring Cloud Gateway WebFlux、Spring Security 6.2 Reactive Resource Server/JOSE、Reactive Redis、Nacos 2.3.2、Micrometer/Prometheus、JUnit 5、AssertJ、Mockito、WebTestClient、Testcontainers 1.19.7、Redis 7.2.5。

**批准规格：** [`2026-08-20-educloud-gateway-design.md`](../specs/2026-08-20-educloud-gateway-design.md)

---

## 执行规则

- 严格按任务 1～14 执行。每个行为先写测试并观察预期红灯，再写满足该测试的最小实现。
- 每个任务提交前运行该任务的局部测试、`git diff --check` 和明确列出的回归命令；提交只包含该任务文件。
- 所有命令从仓库根目录执行。Maven 模块命令固定使用 `-f educloud-backend/pom.xml -pl educloud-gateway -am`。
- 默认 `verify` 不启动容器；只有 `-Pintegration` 执行 `*IT`。IT 使用独立 Redis/Nacos Testcontainers、UUID namespace/key 前缀和临时下游，不连接共享依赖。
- Gateway 正式依赖禁止 MVC、JDBC、MyBatis-Plus、MySQL Driver、RabbitMQ、领域模块和数据库迁移。数据库门禁固定为 `N/A（Gateway 不持久化业务事实）`。
- 不提交私钥、真实 Token、HMAC Secret、Redis/Nacos 密码或 Rocky 临时材料。测试 RSA 密钥必须运行时生成。
- 不把 Gateway 描述为 Token 签发方，不伪造登录成功；真实登录、刷新、注销和业务权限联调属于 M03 门禁。
- 不修改任何前端代码；尤其不修改 worktree 内的 `educloud-frontend` 快照，也不修改主检出目录中的前端。
- 任何失败先使用 `superpowers:systematic-debugging` 查明根因；完成前使用 `superpowers:verification-before-completion`，最终按用户要求使用 `chinese-code-review` 在当前对话内审查。

## 官方实现依据

- Reactive Resource Server 通过显式 `ReactiveJwtDecoder` 替换默认远程发现，并由组合 `OAuth2TokenValidator<Jwt>` 固定 issuer、audience、时间和自定义 claims。
- Gateway 路由通过 `spring.cloud.gateway.routes` 声明，`discovery.locator.enabled=false`；HTTP 与 `lb:ws://` WebSocket 路由均由配置合同测试锁定。
- Nacos Config 使用 `spring.config.import=nacos:`，客户端账号与服务端 identity key/value 是不同安全边界。
- Reactor Netty 请求解码器显式设置 16 KiB header 和 8 KiB initial line 上限。

执行时以这些版本对应的官方文档为准：

- https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html
- https://docs.spring.io/spring-cloud-gateway/reference/4.1/spring-cloud-gateway/configuration.html
- https://sca.aliyun.com/en/docs/2023/user-guide/nacos/quick-start/
- https://nacos.io/en/docs/v2.3/guide/user/auth/
- https://projectreactor.io/docs/netty/1.1.28/api/reactor/netty/http/HttpDecoderSpec.html

## 目标文件图

```text
educloud-backend/
├─ pom.xml
├─ README.md
└─ educloud-gateway/
   ├─ pom.xml
   └─ src/
      ├─ main/java/com/educloud/gateway/
      │  ├─ GatewayApplication.java
      │  ├─ config/
      │  │  ├─ GatewayRuntimeProperties.java
      │  │  ├─ GatewaySecurityProperties.java
      │  │  ├─ GatewayRateLimitProperties.java
      │  │  ├─ GatewayWebProperties.java
      │  │  ├─ GatewayNacosClientProperties.java
      │  │  ├─ GatewayConfigurationValidator.java
      │  │  └─ NettyRequestBoundaryConfiguration.java
      │  ├─ route/
      │  │  ├─ AccessKind.java
      │  │  ├─ AccessDecision.java
      │  │  ├─ AccessPolicy.java
      │  │  ├─ RouteGroups.java
      │  │  └─ InternalPathWebFilter.java
      │  ├─ security/
      │  │  ├─ JwksLoader.java
      │  │  ├─ JwksState.java
      │  │  ├─ GatewayJwtValidator.java
      │  │  ├─ JwtDecoderConfiguration.java
      │  │  ├─ SessionCheckResult.java
      │  │  ├─ SessionVerifier.java
      │  │  ├─ RedisSessionVerifier.java
      │  │  ├─ SessionValidationWebFilter.java
      │  │  ├─ IdentityHeaderWebFilter.java
      │  │  └─ SecurityConfiguration.java
      │  ├─ ratelimit/
      │  │  ├─ BucketRule.java
      │  │  ├─ RateLimitDecision.java
      │  │  ├─ HmacKeyHasher.java
      │  │  ├─ LoginNameExtractor.java
      │  │  ├─ RedisTokenBucketLimiter.java
      │  │  └─ GatewayRateLimitWebFilter.java
      │  ├─ web/
      │  │  ├─ GatewayExchangeAttributes.java
      │  │  ├─ GatewayFilterOrders.java
      │  │  ├─ RequestIdWebFilter.java
      │  │  ├─ IpSubnet.java
      │  │  ├─ ClientIpResolver.java
      │  │  ├─ RequestBodyCachingWebFilter.java
      │  │  ├─ OriginPolicyWebFilter.java
      │  │  ├─ CorsConfiguration.java
      │  │  └─ SecurityHeadersWebFilter.java
      │  ├─ error/
      │  │  ├─ GatewayErrorCode.java
      │  │  ├─ GatewayFailure.java
      │  │  ├─ GatewayErrorWriter.java
      │  │  ├─ GatewayAuthenticationEntryPoint.java
      │  │  ├─ GatewayAccessDeniedHandler.java
      │  │  └─ GatewayWebExceptionHandler.java
      │  └─ observability/
      │     ├─ GatewayMetrics.java
      │     ├─ MicrometerGatewayMetrics.java
      │     ├─ GatewayObservationWebFilter.java
      │     └─ GatewayDependenciesHealthIndicator.java
      ├─ main/resources/
      │  ├─ application.yml
      │  ├─ META-INF/additional-spring-configuration-metadata.json
      │  └─ com/educloud/gateway/
      │     ├─ security/check-session.lua
      │     └─ ratelimit/token-bucket.lua
      └─ test/java/com/educloud/gateway/
         ├─ config/
         ├─ route/
         ├─ security/
         ├─ ratelimit/
         ├─ web/
         ├─ error/
         ├─ observability/
         └─ integration/
            ├─ RedisSessionVerifierIT.java
            ├─ RedisTokenBucketLimiterIT.java
            ├─ GatewaySecurityIT.java
            └─ NacosGatewayRoutingIT.java
deploy/
├─ docker-compose/.env.example
├─ scripts/
│  ├─ generate-local-env.sh
│  ├─ prepare-gateway-local-env.sh
│  ├─ provision-gateway-nacos.sh
│  └─ generate-gateway-test-material.sh
└─ tests/
   ├─ gateway-module-contract-tests.sh
   ├─ prepare-gateway-local-env-tests.sh
   ├─ provision-gateway-nacos-tests.sh
   ├─ generate-gateway-test-material-tests.sh
   └─ gateway-rocky-smoke-tests.sh
```

## 任务 1：建立可执行模块骨架和依赖边界

**文件：**

- 创建：`deploy/tests/gateway-module-contract-tests.sh`
- 修改：`educloud-backend/pom.xml`
- 创建：`educloud-backend/educloud-gateway/pom.xml`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/GatewayApplication.java`
- 创建：`educloud-backend/educloud-gateway/src/main/resources/application.yml`
- 修改：`educloud-backend/README.md`

- [ ] **步骤 1：先写失败的模块契约脚本**

脚本使用 `set -euo pipefail`，并定义以下硬门禁：

```bash
expected_modules=$'educloud-common\neducloud-gateway'
for forbidden_dependency in spring-boot-starter-web spring-boot-starter-jdbc mysql-connector-j mybatis-plus spring-boot-starter-amqp; do
  # 对 dependency:tree 的 compile/runtime 输出执行精确 artifactId 检查
done

for forbidden_source in '@Entity' '@Mapper' 'jakarta.persistence' 'javax.persistence'; do
  # 仅扫描 educloud-gateway/src/main
done

# 还必须检查：
# 1. 子模块 packaging=jar 且 spring-boot-maven-plugin 执行 repackage；
# 2. application.yml 明确 discovery.locator.enabled=false；
# 3. 模块内不存在 db/migration、*.pem、*.key、BEGIN PRIVATE KEY 或 JWK 私钥参数；
# 4. mvn package 后 JAR manifest 含 Start-Class=com.educloud.gateway.GatewayApplication。
```

运行：

```bash
bash deploy/tests/gateway-module-contract-tests.sh
```

预期：失败，并明确报告父 POM 尚未声明 `educloud-gateway` 或子模块不存在。

- [ ] **步骤 2：创建最小可执行模块**

父 POM 模块顺序必须精确为：

```xml
<modules>
    <module>educloud-common</module>
    <module>educloud-gateway</module>
</modules>
```

子 POM 正式依赖固定为：

```xml
<dependencies>
    <dependency>
        <groupId>com.educloud</groupId>
        <artifactId>educloud-common</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-brave</artifactId>
    </dependency>
    <dependency>
        <groupId>io.zipkin.reporter2</groupId>
        <artifactId>zipkin-reporter-brave</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

测试依赖加入 `spring-boot-starter-test`、`spring-security-test`、Testcontainers JUnit/Core 和 Awaitility。配置 Surefire 只跑 `*Test`，Failsafe 只跑 `*IT`；默认 `skipITs=true`，`integration` profile 设置为 `false`。Spring Boot Maven Plugin 使用 `${spring-boot.version}` 并执行 `repackage`。

主类只负责启动和配置属性扫描：

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

初始 `application.yml` 只写 `spring.application.name=educloud-gateway`、端口 8080、Reactive 类型、自动路由关闭和 Actuator 暴露边界；不添加认证关闭开关。

- [ ] **步骤 3：更新 README 的实施事实**

状态改为 `【M02 计划已批准，实施中】`。明确 M01 已完成、M02 正在实施、M03～M13 未实现，Gateway 不拥有数据库且不提供登录/Token 签发。三套前端仍使用 Mock/localStorage，不宣称已经完成真实认证联调。

- [ ] **步骤 4：运行骨架绿灯**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am help:effective-pom
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am package -DskipTests
bash deploy/tests/gateway-module-contract-tests.sh
git diff --check
```

预期：命令全部返回 0；生成可执行 JAR；契约脚本明确报告父模块顺序、Reactive 依赖边界、无数据库材料、自动路由关闭和 Boot manifest 均通过。

- [ ] **步骤 5：提交**

```bash
git add -- educloud-backend/pom.xml educloud-backend/educloud-gateway educloud-backend/README.md deploy/tests/gateway-module-contract-tests.sh
git commit -m "build(gateway): add reactive module boundary"
```

## 任务 2：建立强类型配置和启动失败门禁

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/config/GatewaySecurityProperties.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/config/GatewayRuntimeProperties.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/config/GatewayRateLimitProperties.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/config/GatewayWebProperties.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/config/GatewayNacosClientProperties.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/config/GatewayConfigurationValidator.java`
- 创建：`educloud-backend/educloud-gateway/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- 修改：`educloud-backend/educloud-gateway/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/config/GatewayConfigurationPropertiesTest.java`

- [ ] **步骤 1：为配置不变量写失败测试**

`GatewayConfigurationPropertiesTest` 使用 Jakarta Validator 直接验证属性对象，不连接 Nacos/Redis。必须覆盖：

```java
assertThat(validate(securityWithNoJwks())).contains("exactly one JWKS source");
assertThat(validate(securityWithJsonAndFile())).contains("exactly one JWKS source");
assertThat(validate(securityWithBlankIssuer())).contains("issuer");
assertThat(validate(securityWithSkew(Duration.ofSeconds(121)))).contains("clockSkew");
assertThat(validate(runtimeWithEnvironment("Prod_East"))).contains("environment");
assertThat(validate(rateLimitWithDecodedSecretBytes(31))).contains("at least 32 bytes");
assertThat(validate(rateLimitWithZeroRate())).contains("positive");
assertThat(validate(webWithWildcardOrigin())).contains("wildcard");
assertThatThrownBy(() -> crossValidate(runtime("prod"), webWithHttpOrigin()))
        .hasMessageContaining("HTTPS");
assertThatThrownBy(() -> crossValidateManagementAddress("0.0.0.0"))
        .hasMessageContaining("internal management address");
assertThat(validate(webWithTrustedHops(0))).contains("trustedHops");
assertThat(validate(nacosWithBlankCredentials())).contains("username", "password");
```

同时验证本地 Origin 精确列表、大小上限、超时上限和 CIDR 语法的合法样例。

运行：

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=GatewayConfigurationPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试编译失败，报告五个属性类型尚不存在。

- [ ] **步骤 2：实现安全与 Nacos 属性**

属性前缀和核心字段固定如下：

```java
@ConfigurationProperties("educloud.gateway.security")
@Validated
public final class GatewaySecurityProperties {
    private String jwksJson;
    private Resource jwksLocation;
    @NotBlank private String issuer;
    @NotBlank private String audience = "educloud-api";
    private Duration clockSkew = Duration.ofSeconds(30);

    @AssertTrue(message = "exactly one JWKS source must be configured")
    public boolean hasExactlyOneJwksSource() {
        boolean hasJson = StringUtils.hasText(jwksJson);
        boolean hasLocation = jwksLocation != null;
        return hasJson ^ hasLocation;
    }
}

@ConfigurationProperties("educloud.gateway")
@Validated
public record GatewayRuntimeProperties(
        @Pattern(regexp = "[a-z0-9-]{1,32}") String environment) {}

@ConfigurationProperties("educloud.gateway.nacos")
@Validated
public record GatewayNacosClientProperties(
        @NotBlank String serverAddr,
        @NotBlank String namespace,
        @NotBlank String configGroup,
        @NotBlank String discoveryGroup,
        @NotBlank String username,
        @NotBlank String password) {}
```

`clockSkew` 只接受 0～120 秒；issuer 不自动补斜杠。Nacos 密码字段的 `toString()` 必须输出 `[REDACTED]`，测试不得直接打印属性对象。

- [ ] **步骤 3：实现 Web 与限流属性**

```java
@ConfigurationProperties("educloud.gateway.ratelimit")
@Validated
public final class GatewayRateLimitProperties {
    @NotBlank private String hmacSecretBase64;
    private Bucket ordinary = new Bucket(20, Duration.ofSeconds(1), 40);
    private Bucket loginIp = new Bucket(10, Duration.ofMinutes(1), 10);
    private Bucket loginAccount = new Bucket(5, Duration.ofMinutes(5), 5);
    private Bucket paymentCallback = new Bucket(60, Duration.ofMinutes(1), 60);
}

@ConfigurationProperties("educloud.gateway.web")
@Validated
public final class GatewayWebProperties {
    private List<String> allowedOrigins;
    private List<String> trustedProxyCidrs = List.of();
    private int trustedProxyHops = 1;
    private DataSize globalBodyLimit = DataSize.ofMegabytes(1);
    private DataSize authBodyLimit = DataSize.ofKilobytes(16);
    private DataSize paymentCallbackBodyLimit = DataSize.ofKilobytes(256);
    private DataSize headerLimit = DataSize.ofKilobytes(16);
    private DataSize initialLineLimit = DataSize.ofKilobytes(8);
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration responseTimeout = Duration.ofSeconds(15);
}
```

Bucket 的 `requests`、`period`、`burst` 必须为正，且 burst 不小于 requests；HMAC Base64 解码失败或少于 32 字节时启动失败。`GatewayConfigurationValidator` 在所有属性绑定完成后执行跨 Bean/Environment 校验：environment 非 `local` 时 Origin 只允许精确 HTTPS，不允许 `*` 或 pattern；所有环境都拒绝空 Origin、userinfo、path、query 和 fragment；`management.server.address` 必须是 loopback 或明确的 RFC1918/ULA 内部地址，拒绝 wildcard 和公网地址。

- [ ] **步骤 4：写入非秘密默认配置和元数据**

`application.yml` 使用环境占位符，不提供可工作的秘密默认值：

```yaml
spring:
  application:
    name: educloud-gateway
  main:
    web-application-type: reactive
  config:
    import: "optional:nacos:educloud-gateway.yaml?group=${NACOS_GATEWAY_CONFIG_GROUP:EDUCLOUD_GATEWAY}&refreshEnabled=false"
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
      username: ${NACOS_GATEWAY_USERNAME:}
      password: ${NACOS_GATEWAY_PASSWORD:}
      config:
        namespace: ${NACOS_GATEWAY_NAMESPACE:}
        group: ${NACOS_GATEWAY_CONFIG_GROUP:EDUCLOUD_GATEWAY}
        username: ${NACOS_GATEWAY_USERNAME:}
        password: ${NACOS_GATEWAY_PASSWORD:}
      discovery:
        namespace: ${NACOS_GATEWAY_NAMESPACE:}
        group: ${NACOS_GATEWAY_DISCOVERY_GROUP:EDUCLOUD_SERVICES}
        username: ${NACOS_GATEWAY_USERNAME:}
        password: ${NACOS_GATEWAY_PASSWORD:}
        register-enabled: true
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2s

educloud:
  gateway:
    environment: ${EDUCLOUD_ENVIRONMENT:local}
    security:
      jwks-json: ${GATEWAY_JWKS_JSON:}
      jwks-location: ${GATEWAY_JWKS_LOCATION:}
      issuer: ${GATEWAY_JWT_ISSUER:}
      audience: ${GATEWAY_JWT_AUDIENCE:educloud-api}
      clock-skew: ${GATEWAY_JWT_CLOCK_SKEW:30s}
    ratelimit:
      hmac-secret-base64: ${GATEWAY_RATE_LIMIT_HMAC_SECRET:}
    web:
      allowed-origins: ${GATEWAY_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:5174,http://localhost:5175,http://127.0.0.1:5173,http://127.0.0.1:5174,http://127.0.0.1:5175}
      trusted-proxy-cidrs: []
      trusted-proxy-hops: 1
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
      namespace: ${NACOS_GATEWAY_NAMESPACE:}
      config-group: ${NACOS_GATEWAY_CONFIG_GROUP:EDUCLOUD_GATEWAY}
      discovery-group: ${NACOS_GATEWAY_DISCOVERY_GROUP:EDUCLOUD_SERVICES}
      username: ${NACOS_GATEWAY_USERNAME:}
      password: ${NACOS_GATEWAY_PASSWORD:}
```

`additional-spring-configuration-metadata.json` 为全部自定义字段写名称、类型、说明和默认值；Secret 字段说明必须包含“仅环境变量或挂载 Secret，不写入 Nacos/Git”。

- [ ] **步骤 5：运行配置绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=GatewayConfigurationPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false test
bash deploy/tests/gateway-module-contract-tests.sh
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "feat(gateway): validate runtime configuration"
```

预期：合法配置无 violation；所有缺失、互斥、越界和秘密长度场景产生确定性 violation；模块契约继续通过。

## 任务 3：固定静态路由和精确访问策略

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/AccessKind.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/AccessDecision.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/AccessPolicy.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/RouteGroups.java`
- 修改：`educloud-backend/educloud-gateway/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/route/AccessPolicyTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/route/GatewayRouteContractTest.java`

- [ ] **步骤 1：写访问矩阵红灯**

`AccessPolicyTest` 使用参数化测试锁定：

```java
record Case(HttpMethod method, String path, AccessKind kind, String routeGroup) {}

// PUBLIC_READ
GET|HEAD  /api/v1/platform-config/public
GET|HEAD  /api/v1/categories
GET|HEAD  /api/v1/courses
GET|HEAD  /api/v1/courses/{one-segment-id}
GET|HEAD  /api/v1/search/courses
GET|HEAD  /api/v1/recommendations/courses

// AUTH_SENSITIVE
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh

// PAYMENT_CALLBACK
POST /api/v1/payment-callbacks/**

// INTERNAL
ALL /internal/v1/**

// PROTECTED
所有其他 /api/v1/** 与 /ws/v1/**
```

反例必须包含：`POST /api/v1/courses`、`GET /api/v1/courses/a/b`、尾随额外段、大小写变化、双斜杠、编码斜杠和非白名单方法。

- [ ] **步骤 2：写路由配置红灯**

`GatewayRouteContractTest` 使用 `YamlPropertySourceLoader` 与 Spring `Binder` 读取真实 `application.yml`，断言：

```java
assertThat(routes).hasSize(17);
assertThat(routes).extracting(RouteDefinition::getOrder)
        .containsExactly(10, 20, 30, 40, 50, 60, 65, 70, 80, 90,
                100, 101, 110, 120, 130, 140, 150);
assertThat(targetServiceIds(routes)).containsExactlyInAnyOrder(
        "educloud-user", "educloud-course", "educloud-content",
        "educloud-order", "educloud-payment", "educloud-live",
        "educloud-file", "educloud-notification", "educloud-analytics",
        "educloud-search", "educloud-recommendation");
assertThat(discoveryLocatorEnabled).isFalse();
assertThat(routePaths).noneMatch(path -> path.contains("/internal/v1"));
```

还要逐项断言重叠路径先于宽路径、Live WebSocket URI 为 `lb:ws://educloud-live`。

运行两个测试，预期因类型和 17 条路由缺失而失败。

- [ ] **步骤 3：实现纯访问策略**

```java
public enum AccessKind {
    INTERNAL, ACTUATOR_HEALTH, PUBLIC_READ, AUTH_SENSITIVE,
    PAYMENT_CALLBACK, PROTECTED
}

public record AccessDecision(AccessKind kind, String routeGroup) {
    public boolean mayProceedWithoutBearer() {
        return kind == AccessKind.PUBLIC_READ
                || kind == AccessKind.AUTH_SENSITIVE
                || kind == AccessKind.PAYMENT_CALLBACK
                || kind == AccessKind.ACTUATOR_HEALTH;
    }
}

public interface AccessPolicy {
    AccessDecision classify(HttpMethod method, PathContainer path);
}
```

实现使用预编译 `PathPattern`，不使用字符串 `startsWith` 放宽匿名范围。`RouteGroups` 只返回固定低基数值：`auth`、`catalog`、`payment-callback`、`live-ws`、`user`、`course`、`content`、`order`、`payment`、`live`、`file`、`notification`、`analytics`、`search`、`recommendation`、`unmatched`。

- [ ] **步骤 4：写入完整静态路由**

`application.yml` 精确加入下列 route id、order 和目标：

```text
user-core(10)             -> lb://educloud-user
user-me(20)               -> lb://educloud-user
content-me(30)            -> lb://educloud-content
course-enrollments(40)    -> lb://educloud-course
content-course-scoped(50) -> lb://educloud-content
content-core(60)          -> lb://educloud-content
content-drafts(65)        -> lb://educloud-content
course-core(70)           -> lb://educloud-course
order-core(80)            -> lb://educloud-order
payment-core(90)          -> lb://educloud-payment
live-http(100)            -> lb://educloud-live
live-ws(101)              -> lb:ws://educloud-live
file-core(110)            -> lb://educloud-file
notification-core(120)    -> lb://educloud-notification
analytics-core(130)       -> lb://educloud-analytics
search-core(140)          -> lb://educloud-search
recommendation-core(150)  -> lb://educloud-recommendation
```

Path predicate 必须完整写为：

| route id | Path 参数 |
|---|---|
| `user-core` | `/api/v1/auth/**,/api/v1/users/**,/api/v1/roles/**,/api/v1/permissions/**,/api/v1/platform-config/**,/api/v1/security/**` |
| `user-me` | `/api/v1/me,/api/v1/me/profile` |
| `content-me` | `/api/v1/me/assignments,/api/v1/me/exams,/api/v1/me/course-progress,/api/v1/me/courses/*/progress` |
| `course-enrollments` | `/api/v1/me/enrollments` |
| `content-course-scoped` | `/api/v1/courses/*/chapters/**,/api/v1/courses/*/assignments/**,/api/v1/courses/*/exams/**` |
| `content-core` | `/api/v1/chapters/**,/api/v1/coursewares/**,/api/v1/content-revisions/**,/api/v1/assignments/**,/api/v1/submissions/**,/api/v1/exams/**,/api/v1/exam-attempts/**,/api/v1/community/**,/api/v1/content-audits/**` |
| `content-drafts` | `/api/v1/teacher/courses/*/content-draft,/api/v1/courses/*/content-drafts` |
| `course-core` | `/api/v1/categories/**,/api/v1/course-drafts/**,/api/v1/course-audits/**,/api/v1/courses/**,/api/v1/teacher/courses/*/draft` |
| `order-core` | `/api/v1/cart/**,/api/v1/orders/**,/api/v1/refund-requests/**` |
| `payment-core` | `/api/v1/payments/**,/api/v1/payment-callbacks/**,/api/v1/payment-refunds/**,/api/v1/reconciliations/**` |
| `live-http` | `/api/v1/live-rooms/**` |
| `live-ws` | `/ws/v1/live/**` |
| `file-core` | `/api/v1/files/**,/api/v1/file-upload-sessions/**` |
| `notification-core` | `/api/v1/notifications/**,/api/v1/notification-channels/**` |
| `analytics-core` | `/api/v1/analytics/**,/api/v1/audit-events/**` |
| `search-core` | `/api/v1/search/**` |
| `recommendation-core` | `/api/v1/recommendations/**,/api/v1/assistant/**` |

不使用宽泛 `/**` 代替精确 `/api/v1/me`，不配置 Retry。

- [ ] **步骤 5：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=AccessPolicyTest,GatewayRouteContractTest -Dsurefire.failIfNoSpecifiedTests=false test
bash deploy/tests/gateway-module-contract-tests.sh
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "feat(gateway): define static route policy"
```

## 任务 4：实现启动期 JWKS 与严格 JWT 验证

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/JwksLoader.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/JwksState.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/GatewayJwtValidator.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/JwtDecoderConfiguration.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/security/TestJwtKeys.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/security/JwksLoaderTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/security/GatewayJwtValidatorTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/security/JwtDecoderConfigurationTest.java`

- [ ] **步骤 1：写 JWKS 拒绝矩阵红灯**

`TestJwtKeys` 是 `public final` 测试工具，公开的方法只返回临时公钥 JWKS、签名 Token 和必要的非秘密 key id；每次测试使用 `KeyPairGenerator.getInstance("RSA")` 生成 2048 位 key，仅在内存构造公钥 JWKS 和短期 Token。测试覆盖：

```text
缺少两个来源 / 同时配置两个来源
非法 JSON / keys 为空
kty 非 RSA / use 非 sig / alg 非 RS256
kid 缺失、空白或重复
含 d、p、q、dp、dq、qi、oth 任一私钥参数
只有私钥或无法转换为 RSAPublicKey
两个合法 kid 可分别验证；未知 kid 和 alg=none/RS512 拒绝
```

仓库不得新增固定 PEM、JWK 私钥字符串或完整 Token fixture。

- [ ] **步骤 2：实现不可变公钥加载器**

```java
public record LoadedJwks(JWKSet jwkSet, Set<String> keyIds) {}

public final class JwksLoader {
    public LoadedJwks load(GatewaySecurityProperties properties) {
        String json = readExactlyOneSource(properties);
        JWKSet parsed = JWKSet.parse(json);
        validatePublicRs256Keys(parsed.getKeys());
        return new LoadedJwks(
                new JWKSet(parsed.getKeys().stream().map(JWK::toPublicJWK).toList()),
                Set.copyOf(keyIds));
    }
}
```

文件读取限制为 256 KiB，UTF-8，普通文件且可读；错误只包含安全类别和配置字段名，不回显 JWKS 正文或文件内容。`JwksState` 在 Bean 构造成功后持有 immutable key id 集合，仅暴露 `loaded()` 与 `keyCount()`。

- [ ] **步骤 3：写 claims/时间红灯**

`GatewayJwtValidatorTest` 注入固定 `Clock`，覆盖正确 token 以及：

```text
iss 不等；aud 不包含 educloud-api
exp/nbf/iat 缺失、类型错误、过期、过早、iat 超出允许偏差
sub/sid 缺失、空白、超过 128、包含控制字符
tokenVersion 缺失、非整数或负数
userType 不属于 STUDENT/TEACHER/ADMIN
roles/permissions 非集合、空项、单项超长、数量超界
clockSkew=0、30、120 秒边界
```

- [ ] **步骤 4：实现本地多 key ReactiveJwtDecoder**

```java
@Bean
ReactiveJwtDecoder gatewayJwtDecoder(
        JwksLoader loader,
        GatewaySecurityProperties properties,
        Clock clock,
        JwksState state) {
    LoadedJwks loaded = loader.load(properties);
    Function<SignedJWT, Flux<JWK>> source = signed -> Flux.fromIterable(
            new JWKSelector(JWKMatcher.forJWSHeader(signed.getHeader()))
                    .select(loaded.jwkSet()));
    NimbusReactiveJwtDecoder decoder =
            NimbusReactiveJwtDecoder.withJwkSource(source).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(properties.getClockSkew()),
            new JwtIssuerValidator(properties.getIssuer()),
            new GatewayJwtValidator(properties, clock)));
    state.markLoaded(loaded.keyIds());
    return decoder;
}
```

`GatewayJwtValidator` 固定 audience、iat、必填 claims、文本边界、userType 和有界 roles/permissions。`sub/sid` 只接受 `[A-Za-z0-9._:-]{1,128}`；roles 和 permissions 各最多 64 项，每项接受 `[A-Za-z0-9:._*-]{1,128}`，拒绝重复和空项。客户端统一看到 `UNAUTHENTICATED`；日志只能记录 `signature`、`kid`、`issuer`、`audience`、`timestamp`、`claims` 等失败类别。

- [ ] **步骤 5：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=JwksLoaderTest,GatewayJwtValidatorTest,JwtDecoderConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
bash deploy/tests/gateway-module-contract-tests.sh
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "feat(gateway): verify local jwks tokens"
```

预期：所有合法双 key Token 通过；拒绝矩阵全部返回稳定类别，测试输出和 Git diff 不含私钥。

## 任务 5：实现 requestId 与统一 Reactive 错误响应

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/web/GatewayExchangeAttributes.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/web/GatewayFilterOrders.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/web/RequestIdWebFilter.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/error/GatewayErrorCode.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/error/GatewayFailure.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/error/GatewayErrorWriter.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/error/GatewayAuthenticationEntryPoint.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/error/GatewayAccessDeniedHandler.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/InternalPathWebFilter.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/observability/GatewayMetrics.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/web/RequestIdWebFilterTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/error/GatewayErrorWriterTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/route/InternalPathWebFilterTest.java`

- [ ] **步骤 1：写 requestId 和错误 JSON 红灯**

测试断言合法 `[A-Za-z0-9._-]{1,64}` 原样保留，非法/缺失值由 Common `RequestIdPolicy` 替换为 UUID；exchange attribute、下游 request header、response header 和 JSON requestId 必须一致。

错误表固定为：

```java
GATEWAY_BAD_REQUEST(400, "Bad gateway request")
UNAUTHENTICATED(401, "Authentication required")
ACCESS_DENIED(403, "Access denied")
GATEWAY_ROUTE_NOT_FOUND(404, "Route not found")
GATEWAY_REQUEST_TOO_LARGE(413, "Request is too large")
GATEWAY_UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type")
RATE_LIMITED(429, "Too many requests")
DEPENDENCY_UNAVAILABLE(503, "Dependency unavailable")
GATEWAY_TIMEOUT(504, "Gateway timeout")
INTERNAL_ERROR(500, "Internal server error")
```

测试还要断言 `data=null`、`Content-Type=application/json`、429 的 `Retry-After`，以及正文不包含 exception class、内部 URI、Redis key、Token、claims 或凭据。

- [ ] **步骤 2：实现 GatewayErrorCode 和 writer**

`GatewayErrorCode implements ErrorCode`；`GatewayFailure` 只保存 error code、安全公开 message、可选 retryAfter 和低基数内部 category。writer 直接构造 Common `ApiResponse<Void>`：

```java
ApiResponse<Void> body = new ApiResponse<>(
        failure.code().code(),
        failure.publicMessage(),
        null,
        requestId,
        clock.instant());
```

`GatewayErrorWriter.write(ServerWebExchange, GatewayFailure)` 遇到已 committed 响应时只返回 `Mono.empty()`，不尝试二次写入；`GatewayWebExceptionHandler` 在调用 writer 前负责传播已经 committed 的原始异常。序列化失败只记录一次并关闭连接，不把底层异常写回客户端。

- [ ] **步骤 3：实现最前置 RequestId filter**

`GatewayFilterOrders` 固定：

```java
REQUEST_ID = -300;
SECURITY_HEADERS = -290;
INTERNAL_PATH = -280;
BODY_CACHE = -270;
CLIENT_IDENTITY = -260;
ORIGIN = -255;
CORS = -254;
RATE_LIMIT = -250;
```

`RequestIdWebFilter` order=-300，在 chain 前写 exchange/request/response，在 Reactor Context 写 `requestId`，但不修改 traceId。

- [ ] **步骤 4：接入 Security entry point 边界**

`GatewayAuthenticationEntryPoint` 始终写 401；`GatewayAccessDeniedHandler` 始终写 403。两者不记录 Authorization header，只把安全失败类别交给本任务定义的 `GatewayMetrics` 接口。接口只接受固定 `category/routeId/result`，任务 11 用 `MicrometerGatewayMetrics` 提供正式实现；单元测试使用 mock，不创建会泄漏动态值的临时标签 API。

- [ ] **步骤 5：先于认证阻断内部路径**

`InternalPathWebFilter` 依赖已经存在的 `AccessPolicy` 和 `GatewayErrorWriter`，order 固定为 `GatewayFilterOrders.INTERNAL_PATH=-280`。命中 `AccessKind.INTERNAL` 时直接写 404 `GATEWAY_ROUTE_NOT_FOUND`，不调用 chain；测试用 mock chain、JWT decoder、Redis 和 route locator 断言这些依赖的调用次数都为 0。

- [ ] **步骤 6：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=RequestIdWebFilterTest,GatewayErrorWriterTest,InternalPathWebFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f educloud-backend/pom.xml -pl educloud-common,educloud-gateway -am test
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "feat(gateway): add reactive error contract"
```

## 任务 6：实现 Redis 权威会话检查

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/SessionCheckResult.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/SessionVerifier.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/RedisSessionVerifier.java`
- 创建：`educloud-backend/educloud-gateway/src/main/resources/com/educloud/gateway/security/check-session.lua`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/security/RedisSessionVerifierTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/security/SessionScriptContractTest.java`

- [ ] **步骤 1：写 Redis 协议分类红灯**

```java
public enum SessionCheckResult {
    ACTIVE,
    MISSING,
    REVOKED,
    SUBJECT_MISMATCH,
    VERSION_MISMATCH,
    CORRUPT,
    DEPENDENCY_ERROR
}

public interface SessionVerifier {
    Mono<SessionCheckResult> verify(String subject, String sessionId, long tokenVersion);
}
```

测试 mock Reactive Redis 脚本结果并固定分类：

```text
{1, subject, ACTIVE, version, positivePttl} -> ACTIVE
key 不存在                              -> MISSING
status=REVOKED                          -> REVOKED
subject 不等                            -> SUBJECT_MISMATCH
version 不等                            -> VERSION_MISMATCH
字段缺失/未知状态/PTTL<=0/协议形状错误  -> CORRUPT
Redis timeout/connection/script error   -> DEPENDENCY_ERROR
```

- [ ] **步骤 2：先写 Lua 合同测试**

`SessionScriptContractTest` 读取真实资源并断言只使用 `HMGET`、`PTTL`，不出现 `HSET`、`DEL`、`EXPIRE`、`KEYS`。脚本返回稳定数组：

```lua
local values = redis.call('HMGET', KEYS[1], 'subject', 'status', 'tokenVersion')
local ttl = redis.call('PTTL', KEYS[1])
if ttl == -2 then
  return {0}
end
return {1, values[1] or '', values[2] or '', values[3] or '', ttl}
```

- [ ] **步骤 3：实现 key 与结果解析**

key 固定为：

```text
educloud:{<environment>:auth}:session:<sid>
```

`environment` 只能为配置验证后的 `[a-z0-9-]{1,32}`；sid 不进入日志/指标。Lua 使用 `DefaultRedisScript<List>`，命令超时后映射 DEPENDENCY_ERROR。实现不使用本地正缓存，不把 Redis 值存入静态字段。

- [ ] **步骤 4：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=RedisSessionVerifierTest,SessionScriptContractTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "feat(gateway): enforce redis session contract"
```

## 任务 7：组装 Reactive Security 与可选认证语义

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/SessionValidationWebFilter.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/SecurityConfiguration.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/security/SessionValidationWebFilterTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/security/SecurityConfigurationTest.java`

- [ ] **步骤 1：写安全状态矩阵红灯**

使用 mock `SessionVerifier`、`GatewayErrorWriter` 和 `JwtAuthenticationToken` 覆盖：

```text
PUBLIC_READ 无 Bearer             -> 不查 Redis，继续
PUBLIC_READ 合法 Bearer+ACTIVE    -> 查 Redis，继续
PUBLIC_READ 合法 Bearer+REVOKED   -> 401，不调用下游
PUBLIC_READ 非法 Bearer           -> Resource Server 401
AUTH_SENSITIVE 无 Bearer          -> 不伪造 principal；继续到限流/下游
PROTECTED 无 Bearer               -> 401
PROTECTED 合法 Bearer+ACTIVE      -> 继续
PROTECTED 合法 Bearer+MISSING 等  -> 401
任意需查会话请求+CORRUPT/ERROR    -> 503
PAYMENT_CALLBACK 无用户 Bearer    -> 不查会话；由限流失败关闭
INTERNAL                          -> 在 Security 前已 404
```

另写顺序断言：Session filter 必须在 `SecurityWebFiltersOrder.AUTHORIZATION` 之前、Bearer authentication 之后。

- [ ] **步骤 2：实现 SessionValidationWebFilter**

```java
public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    AccessDecision access = accessPolicy.classify(
            exchange.getRequest().getMethod(),
            exchange.getRequest().getPath().pathWithinApplication());
    return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(JwtAuthenticationToken.class::isInstance)
            .cast(JwtAuthenticationToken.class)
            .flatMap(authentication -> verifyAndContinue(exchange, chain, authentication))
            .switchIfEmpty(chain.filter(exchange));
}
```

`verifyAndContinue` 从已验证 Jwt 读取 `sub`、`sid`、`tokenVersion`。MISSING/REVOKED/MISMATCH 写 401；CORRUPT/DEPENDENCY_ERROR 写 503。不得把这些失败转换为 403。

- [ ] **步骤 3：实现 SecurityWebFilterChain**

```java
http
    .csrf(ServerHttpSecurity.CsrfSpec::disable)
    .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
    .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
    .logout(ServerHttpSecurity.LogoutSpec::disable)
    .authorizeExchange(exchanges -> exchanges
        .pathMatchers("/internal/v1/**").denyAll()
        .matchers(publicAndCallbackMatcher).permitAll()
        .anyExchange().authenticated())
    .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtDecoder(gatewayJwtDecoder))
        .authenticationEntryPoint(authenticationEntryPoint))
    .exceptionHandling(errors -> errors
        .authenticationEntryPoint(authenticationEntryPoint)
        .accessDeniedHandler(accessDeniedHandler))
    .addFilterBefore(sessionValidationWebFilter, SecurityWebFiltersOrder.AUTHORIZATION);
```

`publicAndCallbackMatcher` 委托同一个 `AccessPolicy`，不复制白名单字符串。Bearer converter 保持原始 Authorization header；Gateway 不创建用户身份头。

- [ ] **步骤 4：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=SessionValidationWebFilterTest,SecurityConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am test
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "feat(gateway): secure reactive request flow"
```

## 任务 8：净化身份头并解析可信客户端 IP

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/web/IpSubnet.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/web/ClientIpResolver.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/security/IdentityHeaderWebFilter.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/web/IpSubnetTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/web/ClientIpResolverTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/security/IdentityHeaderWebFilterTest.java`

- [ ] **步骤 1：写 CIDR 与代理链红灯**

覆盖 IPv4/IPv6、边界地址、非法 prefix、IPv4-mapped IPv6。客户端解析场景固定为：

```text
peer 不受信 + 任意 Forwarded/XFF -> 使用 peer
peer 受信 + Forwarded for=       -> 按右侧固定 trusted hops 取客户端
peer 受信 + XFF                  -> 同上
Forwarded 优先于 XFF
unknown、_obfuscated、空项、超长链、非法端口/括号 -> 400
链长度不足 trusted hops            -> 400
```

最多接受 16 个代理元素，每项最长 128 字符。

- [ ] **步骤 2：实现无 DNS 的 CIDR 匹配**

`IpSubnet.parse(String)` 只接受数字 IP 字面量和 prefix，不解析主机名；把网络地址和候选地址转换为等长 byte array 后逐位 mask。IPv4 与 IPv6 不交叉匹配。

`ClientIpResolver.resolve(ServerHttpRequest)` 返回规范化地址字符串，不带端口；只在 TCP peer 命中配置 CIDR 时读取 Forwarded/XFF。

- [ ] **步骤 3：实现头部净化 filter**

在 order=-260：

```text
先计算 clientIp 并写入 exchange attribute（仅限流内部使用）
再删除 Forwarded、X-Forwarded-For、X-Forwarded-Host、X-Forwarded-Proto
始终删除大小写任意组合：
X-User-Id、X-User-Type、X-Role、X-Roles、
X-Permission、X-Permissions、X-Authenticated-User、X-EduCloud-Identity-*
保留已验证流程所需 Authorization、X-Request-Id、traceparent、tracestate
```

不重新注入用户/角色/权限头，不把规范化 IP 传给下游。

- [ ] **步骤 4：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=IpSubnetTest,ClientIpResolverTest,IdentityHeaderWebFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "feat(gateway): sanitize client identity"
```

## 任务 9：实现请求体回放与 Redis 多维 Token Bucket

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/web/RequestBodyCachingWebFilter.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/ratelimit/BucketRule.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/ratelimit/RateLimitDecision.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/ratelimit/HmacKeyHasher.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/ratelimit/LoginNameExtractor.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/ratelimit/RedisTokenBucketLimiter.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/ratelimit/GatewayRateLimitWebFilter.java`
- 创建：`educloud-backend/educloud-gateway/src/main/resources/com/educloud/gateway/ratelimit/token-bucket.lua`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/web/RequestBodyCachingWebFilterTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/ratelimit/HmacKeyHasherTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/ratelimit/LoginNameExtractorTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/ratelimit/RedisTokenBucketLimiterTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/ratelimit/GatewayRateLimitWebFilterTest.java`

- [ ] **步骤 1：写请求体限制与完整回放红灯**

使用分块 `Flux<DataBuffer>` 测试，不只依赖 Content-Length：

```text
auth register/login/refresh <=16KiB 通过，>16KiB 返回 413
payment callback <=256KiB 通过，>256KiB 返回 413
其他 API <=1MiB 通过，>1MiB 返回 413
无 body 不分配 byte[]
读取后下游收到逐字节相同正文和原 Content-Type/Content-Length 语义
DataBufferLimitException 和取消订阅均释放 buffer
```

`RequestBodyCachingWebFilter` order=-270，缓存 byte[] 写入私有 exchange attribute，只允许本请求读取。

- [ ] **步骤 2：写 loginName 与 HMAC 红灯**

`LoginNameExtractor` 只接受 `application/json`，从根对象读取字符串 `loginName`，NFKC、trim、Locale.ROOT 小写后要求 1～128 字符；非法 JSON、数组、重复字段、缺失/非字符串返回 400，错误信息不含正文或 password。

`HmacKeyHasher`：

```java
String digest(String dimension, String normalizedValue);
```

使用 `HmacSHA256`，输入为 `dimension + "\n" + normalizedValue`，输出 64 位小写 hex。相同输入稳定、不同维度不同，`toString()` 不泄露 secret。

- [ ] **步骤 3：写 Lua 算法红灯**

`RedisTokenBucketLimiterTest` mock script 执行协议；资源合同检查 Lua 必须使用 Redis `TIME`，不得使用 JVM 时间、`KEYS` 或不设 TTL。多 bucket 一次脚本调用，所有 key 使用同一 hash tag：

```text
educloud:{<environment>:ratelimit}:ordinary:<hmac>
educloud:{<environment>:ratelimit}:login-ip:<hmac>
educloud:{<environment>:ratelimit}:login-account:<hmac>
educloud:{<environment>:ratelimit}:payment-callback:<hmac>
```

脚本先计算所有 bucket 的 refill/可用性；只有全部允许才统一扣减并 `PEXPIRE`。任一拒绝返回最大等待毫秒且不部分扣减。

- [ ] **步骤 4：实现限流决策**

```java
public record BucketRule(long requests, Duration period, long burst) {}
public record RateLimitDecision(boolean allowed, Duration retryAfter) {}

Mono<RateLimitDecision> acquire(List<BucketRequest> buckets);
```

普通接口：IP + routeGroup 20/s burst40；login 再叠加 10/min/IP 与 5/5min/account；payment callback 使用 60/min/IP。所有原始 IP/loginName 在生成 HMAC 后立即丢弃，不进入 Redis、日志、metrics 或 trace。

- [ ] **步骤 5：实现故障矩阵 filter**

`GatewayRateLimitWebFilter` order=-250：

```text
allowed                      -> chain
denied                       -> 429 RATE_LIMITED + Retry-After(向上取整秒)
Redis error + PUBLIC_READ 且无 Bearer -> chain + gateway.ratelimit.degraded
Redis error + 其他 kind              -> 503 DEPENDENCY_UNAVAILABLE
login 非 JSON                         -> 415
login JSON 无法安全解析               -> 400
```

公开接口携带 Bearer 不属于 fail-open；它在 Redis 限流故障时返回 503。

- [ ] **步骤 6：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=RequestBodyCachingWebFilterTest,HmacKeyHasherTest,LoginNameExtractorTest,RedisTokenBucketLimiterTest,GatewayRateLimitWebFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am test
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "feat(gateway): add distributed edge limits"
```

## 任务 10：实现 CORS、安全响应头和 Netty 请求边界

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/web/OriginPolicyWebFilter.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/web/CorsConfiguration.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/web/SecurityHeadersWebFilter.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/config/NettyRequestBoundaryConfiguration.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/web/GatewayCorsTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/web/SecurityHeadersWebFilterTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/config/NettyRequestBoundaryConfigurationTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/web/GatewayFilterOrderTest.java`

- [ ] **步骤 1：写 CORS 矩阵红灯**

本地精确 Origin：

```text
http://localhost:5173
http://localhost:5174
http://localhost:5175
http://127.0.0.1:5173
http://127.0.0.1:5174
http://127.0.0.1:5175
```

允许 methods：`GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS`；允许 request headers：`Authorization,Content-Type,X-Request-Id,Idempotency-Key,If-Match,Accept-Language`；expose：`X-Request-Id,Retry-After`；`allowCredentials=true`。

`WebTestClient` 覆盖允许 Origin、相似恶意 Origin、null、通配、错误 scheme、未允许 method/header、合法预检、无 Origin 的服务端请求。拒绝响应必须是 403 `ACCESS_DENIED` JSON，不是空正文。

- [ ] **步骤 2：实现 Origin 预检与 CorsWebFilter**

`OriginPolicyWebFilter` order=-255，在 Spring `CorsWebFilter` 前使用精确集合拒绝不合法 Origin；无 Origin 不按浏览器 CORS 拒绝。`CorsConfiguration` 创建的 `CorsWebFilter` Bean 显式标注 `@Order(GatewayFilterOrders.CORS)`，其中 `CORS=-254`；合法预检由它终止并返回配置头。不启用第二套 Spring Security CORS filter，也不使用 `allowedOriginPatterns`。

- [ ] **步骤 3：写并实现安全响应头**

所有 Gateway 自有及代理响应必须包含：

```text
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Permissions-Policy: camera=(), microphone=(), geolocation=()
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'
```

非 local 且请求 scheme=https 时增加 HSTS；local/http 不增加。`SecurityHeadersWebFilter` 使用 `response.beforeCommit(() -> applySecurityHeaders(exchange))` 删除 `Server`、`X-Powered-By` 和框架版本头，并仅在同名安全头缺失时写入基线；如果下游给出更严格同名头，不覆盖为更弱值。

- [ ] **步骤 4：配置 Netty 解码上限**

```java
factory.addServerCustomizers(server -> server.httpRequestDecoder(spec -> spec
        .maxHeaderSize(Math.toIntExact(properties.getHeaderLimit().toBytes()))
        .maxInitialLineLength(Math.toIntExact(
                properties.getInitialLineLimit().toBytes()))
        .validateHeaders(true)));
```

测试捕获 customizer 并断言 16384/8192；非法/溢出配置在启动前失败。

- [ ] **步骤 5：锁定全部过滤器顺序**

`GatewayFilterOrderTest` 断言：

```text
RequestId(-300)
SecurityHeaders(-290)
InternalPath(-280)
RequestBodyCache(-270)
IdentityHeader(-260)
OriginPolicy(-255)
CorsWebFilter(-254)
RateLimit(-250)
Spring Security(Bearer -> Session -> Authorization)
Gateway route/load-balancer
```

并用一次 WebTestClient 测试证明内部路径不触发 Redis/JWT/Nacos，非法 Origin 不触发下游，限流在 JWT 前执行但不会泄露请求体。

- [ ] **步骤 6：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=GatewayCorsTest,SecurityHeadersWebFilterTest,NettyRequestBoundaryConfigurationTest,GatewayFilterOrderTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am test
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "feat(gateway): harden reactive web edge"
```

## 任务 11：实现路由故障、超时和低基数可观测性

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/error/GatewayWebExceptionHandler.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/observability/MicrometerGatewayMetrics.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/observability/GatewayObservationWebFilter.java`
- 创建：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/observability/GatewayDependenciesHealthIndicator.java`
- 修改：`educloud-backend/educloud-gateway/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/error/GatewayWebExceptionHandlerTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/observability/GatewayMetricsTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/observability/GatewayDependenciesHealthIndicatorTest.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/observability/DownstreamPassThroughTest.java`

- [ ] **步骤 1：写异常映射与透传红灯**

`GatewayWebExceptionHandlerTest` 固定：

```text
无路由/内部保留路径          -> 404 GATEWAY_ROUTE_NOT_FOUND
ReactiveLoadBalancer 无实例  -> 503 DEPENDENCY_UNAVAILABLE
ConnectException             -> 503 DEPENDENCY_UNAVAILABLE
下游响应超时                 -> 504 GATEWAY_TIMEOUT
DataBufferLimitException     -> 413 GATEWAY_REQUEST_TOO_LARGE
非法入口解析                 -> 400 GATEWAY_BAD_REQUEST
其他未预期异常               -> 500 INTERNAL_ERROR
```

`DownstreamPassThroughTest` 使用 Reactor Netty 临时服务返回 200 JSON、400 业务 JSON、404 业务 JSON，断言 Gateway 保留 status/body/content-type，只添加 requestId 和安全响应头，不二次包装。

- [ ] **步骤 2：实现 WebExceptionHandler**

order 必须早于 Boot 默认 error handler；只在 response 未 committed 时调用 `GatewayErrorWriter`。异常到 category 的映射使用明确类型链，不把 `Throwable.getMessage()` 发送给客户端。未预期 500 只记录一次 stack trace。

`application.yml`：

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 2000
        response-timeout: 15s
      discovery:
        locator:
          enabled: false
server:
  shutdown: graceful
spring.lifecycle.timeout-per-shutdown-phase: 20s
management.tracing.sampling.probability: ${GATEWAY_TRACING_SAMPLING_PROBABILITY:1.0}
management.zipkin.tracing.endpoint: ${ZIPKIN_ENDPOINT:http://127.0.0.1:9411/api/v2/spans}
```

不配置 Retry；WebSocket route 不增加普通 `response-timeout` filter。Zipkin 上报为 best-effort，Zipkin 短暂不可用不改变 liveness/readiness 或业务响应；W3C `traceparent/tracestate` 传播通过端到端测试锁定。

- [ ] **步骤 3：写低基数 metrics 红灯**

允许 tag 值只能来自 enum/固定 routeId/method/status/error category。测试尝试传入动态 path、userId、sid、IP 时 API 不提供相应参数。至少提供：

```text
gateway.security.failures{category,routeId}
gateway.session.checks{result,routeId}
gateway.ratelimit.decisions{result,routeGroup}
gateway.ratelimit.degraded{routeGroup}
gateway.dependencies{dependency,result}
```

`GatewayObservationWebFilter` 记录 service/environment/instance/requestId/traceId/routeId/status/duration/category；不得记录 Authorization、Cookie、claims、账号、IP、正文或完整动态路径。

- [ ] **步骤 4：实现 readiness/liveness**

`GatewayDependenciesHealthIndicator` 并行检查：

```text
JwksState.loaded == true
Reactive Redis PING == PONG
Nacos NamingService server status == UP
```

Nacos 阻塞探测放到 boundedElastic 并设短超时。结果 details 只包含 `jwks=UP|DOWN`、`redis=UP|DOWN`、`nacos=UP|DOWN`，不含地址或凭据。

`application.yml` 只暴露 `health,prometheus`：

```yaml
management:
  server:
    address: ${GATEWAY_MANAGEMENT_ADDRESS:127.0.0.1}
    port: ${GATEWAY_MANAGEMENT_PORT:8081}
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,gatewayDependencies
```

Redis/Nacos 短暂故障只让 readiness DOWN，不让 liveness DOWN。
管理端点默认只监听 `127.0.0.1:8081`；生产若覆盖地址，必须使用内部管理网地址，不允许 `0.0.0.0` 或公网地址。主业务端口 8080 不暴露 Actuator 路径，测试分别探测 8080 与 8081。

- [ ] **步骤 5：运行绿灯并提交**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am -Dtest=GatewayWebExceptionHandlerTest,GatewayMetricsTest,GatewayDependenciesHealthIndicatorTest,DownstreamPassThroughTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am test
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "feat(gateway): add failure observability"
```

## 任务 12：配置专用 Nacos 客户端身份和本地秘密流程

**文件：**

- 修改：`deploy/docker-compose/.env.example`
- 修改：`deploy/scripts/generate-local-env.sh`
- 创建：`deploy/scripts/prepare-gateway-local-env.sh`
- 创建：`deploy/scripts/provision-gateway-nacos.sh`
- 创建：`deploy/scripts/generate-gateway-test-material.sh`
- 创建：`deploy/tests/prepare-gateway-local-env-tests.sh`
- 创建：`deploy/tests/provision-gateway-nacos-tests.sh`
- 创建：`deploy/tests/generate-gateway-test-material-tests.sh`
- 修改：`deploy/runbooks/rocky-linux-8.9-bootstrap.md`

- [ ] **步骤 1：先写 env 增量脚本红灯**

`prepare-gateway-local-env-tests.sh` 在临时目录复制现有 `.env`，断言：

```text
保留所有原有变量和值
只补充缺失的 NACOS_GATEWAY_NAMESPACE/CONFIG_GROUP/DISCOVERY_GROUP/USERNAME/PASSWORD
重复执行内容不变
重复 key、不可读文件或占位 ChangeMe 失败
输出不包含生成密码
最终权限为 0600
```

固定非秘密值：

```dotenv
NACOS_GATEWAY_NAMESPACE=educloud-local
NACOS_GATEWAY_CONFIG_GROUP=EDUCLOUD_GATEWAY
NACOS_GATEWAY_DISCOVERY_GROUP=EDUCLOUD_SERVICES
NACOS_GATEWAY_USERNAME=educloud_gateway
NACOS_GATEWAY_PASSWORD=LocalNacosGateway_ChangeMe_2026
```

`.env.example` 保留占位；`generate-local-env.sh` 对新文件随机化 `NACOS_GATEWAY_PASSWORD`。`prepare-gateway-local-env.sh` 对既有文件只补缺失值，绝不覆盖 MySQL/Redis/Nacos 服务端秘密。

- [ ] **步骤 2：写 Nacos provisioner 红灯**

测试使用 PATH 前置的 fake curl 记录非秘密请求形状，断言：

```text
管理员用户名来自 NACOS_ADMIN_USERNAME
管理员密码来自 NACOS_ADMIN_PASSWORD 或无回显 stdin prompt
curl 进程参数不出现密码/accessToken
脚本无 set -x，stdout 不含凭据
创建/验证 namespace educloud-local
创建/验证 user educloud_gateway 与同名 role
第二次执行不重复扩大权限
目标状态不一致时失败
```

Nacos 2.3.2 v1 API 使用 curl config/stdin 传递敏感 form/query，不把 secret 放入 argv。权限使用 action `r`/`w`，资源精确到：

```text
educloud-local:EDUCLOUD_GATEWAY:educloud-gateway.yaml      r
educloud-local:EDUCLOUD_SERVICES:educloud-gateway         r,w
educloud-local:EDUCLOUD_SERVICES:educloud-user            r
educloud-local:EDUCLOUD_SERVICES:educloud-course          r
educloud-local:EDUCLOUD_SERVICES:educloud-content         r
educloud-local:EDUCLOUD_SERVICES:educloud-order           r
educloud-local:EDUCLOUD_SERVICES:educloud-payment         r
educloud-local:EDUCLOUD_SERVICES:educloud-live            r
educloud-local:EDUCLOUD_SERVICES:educloud-file            r
educloud-local:EDUCLOUD_SERVICES:educloud-notification    r
educloud-local:EDUCLOUD_SERVICES:educloud-analytics       r
educloud-local:EDUCLOUD_SERVICES:educloud-search          r
educloud-local:EDUCLOUD_SERVICES:educloud-recommendation  r
```

不授予 `ROLE_ADMIN`、`*:*` 或配置写权限。脚本登录后读取现状、只创建缺失对象、逐项比较权限集合；多余权限或密码验证失败立即退出。

- [ ] **步骤 3：实现 Rocky 临时材料生成器**

`generate-gateway-test-material.sh --output DIR` 要求空的 0700 目录，使用 OpenSSL 生成 2048 位 RSA 私钥、公钥 JWKS、短期合法 JWT 和至少 32 字节 HMAC Secret。固定测试 claims：

```json
{
  "iss": "https://issuer.educloud.local",
  "aud": ["educloud-api"],
  "sub": "rocky-user",
  "sid": "rocky-session",
  "userType": "STUDENT",
  "tokenVersion": 1
}
```

`iat/nbf/exp` 按当前 UTC 生成，exp 不超过 15 分钟。私钥 0600、JWKS 0644（目录仍 0700）、runtime env 0600；stdout 只打印文件路径，不打印 Token/secret/key。测试验证 JWKS 没有 `d/p/q/dp/dq/qi/oth`，Token 三段且签名可由生成公钥验证，清理后无文件。

- [ ] **步骤 4：更新 Rocky runbook**

只增加 M02 专章，先运行 `prepare-gateway-local-env.sh`，再 provision Nacos，再生成临时材料。明确：

```text
不要对已有 .env 执行 generate-local-env.sh --force
不要停止共享 Redis/Nacos
管理员凭据不得粘贴到命令参数或日志
M02 不需要数据库迁移
M02 不提供真实登录
```

- [ ] **步骤 5：运行脚本绿灯并提交**

```bash
bash deploy/tests/prepare-gateway-local-env-tests.sh
bash deploy/tests/provision-gateway-nacos-tests.sh
bash deploy/tests/generate-gateway-test-material-tests.sh
bash deploy/tests/generate-local-env-tests.sh
bash deploy/tests/compose-contract-tests.sh
git diff --check
git add -- deploy/docker-compose/.env.example deploy/scripts deploy/tests deploy/runbooks/rocky-linux-8.9-bootstrap.md
git commit -m "feat(gateway): provision local security identity"
```

## 任务 13：增加真实 Redis、Nacos、Security 和路由集成测试

**文件：**

- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/RedisSessionVerifierIT.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/RedisTokenBucketLimiterIT.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/GatewaySecurityIT.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/NacosGatewayRoutingIT.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/IntegrationResourceTracker.java`
- 修改：`educloud-backend/educloud-gateway/pom.xml`

- [ ] **步骤 1：写 Redis 会话 IT 红灯**

`RedisSessionVerifierIT` 启动 `redis:7.2.5-alpine`，使用 UUID environment。真实写入：

```text
subject=<sub>
status=ACTIVE|REVOKED
tokenVersion=<version>
PEXPIRE=<positive ttl>
```

断言 ACTIVE、missing、revoked、subject/version mismatch、损坏字段、无 TTL、过期、容器停止后的 DEPENDENCY_ERROR。测试结束扫描此前缀 key 必须为 0。

- [ ] **步骤 2：写并发 Token Bucket IT 红灯**

同一 Redis 容器上并发发起大于 burst 的请求：

```java
Flux.range(0, 200)
    .flatMap(i -> limiter.acquire(buckets), 32)
    .collectList();
```

断言成功数不超过 burst、拒绝均有正 retryAfter、两个 login bucket 不发生部分扣减、TTL 有界、Redis TIME 驱动 refill、bucket 过期后 key 消失。停止 Redis 后验证 public fail-open 计数与 protected/login/callback 503。

- [ ] **步骤 3：写 Security 端到端 IT 红灯**

启动随机端口 Gateway、真实 Redis、启用鉴权的 Nacos 2.3.2、运行时双 RSA key 和 Reactor Netty 临时下游；为该测试创建 UUID namespace 与专用临时 Nacos user/role，覆盖：

```text
public 无 Token -> 可路由
public 非法/过期/未知 kid Token -> 401
public 合法但 session revoked/mismatch -> 401
protected 无 Token -> 401
protected 合法 Token+ACTIVE -> 下游看到原 Authorization、requestId、traceparent
伪造身份头和 Forwarded -> 下游看不到
Redis 中断 protected/token-bearing public -> 503
CORS、body limit、security headers、429/Retry-After
下游 2xx/4xx 正文保持原样
```

下游观察结果只在测试内存保存，不打印 Token。

- [ ] **步骤 4：写 Nacos 2.3.2 路由 IT 红灯**

`NacosGatewayRoutingIT` 同时启动 Redis 7.2.5（用于合法受保护请求的会话）并使用：

```java
GenericContainer<?> nacos = new GenericContainer<>("nacos/nacos-server:v2.3.2")
        .withEnv("MODE", "standalone")
        .withEnv("NACOS_AUTH_ENABLE", "true")
        .withEnv("NACOS_AUTH_TOKEN", generatedBase64Secret)
        .withEnv("NACOS_AUTH_IDENTITY_KEY", generatedIdentityKey)
        .withEnv("NACOS_AUTH_IDENTITY_VALUE", generatedIdentityValue)
        .withExposedPorts(8848, 9848);
```

测试创建 UUID namespace、临时 user/role、精确权限和 Reactor Netty HTTP/WS 下游实例。验证：

```text
Gateway 以临时非管理员账号注册为 educloud-gateway
可读取唯一 gateway 配置
17 条静态路由通过 Nacos 找到目标 HTTP/WS 实例
自动 locator 路径不存在
已知 route 无健康实例 -> 503
未授权配置写、无关 namespace/group/service 查询被拒绝
```

`finally` 删除实例、权限、role/user、config、namespace 和 Redis keys；任何清理请求失败都通过 `IntegrationResourceTracker` 合并为测试失败，并只输出 UUID 资源标识。

- [ ] **步骤 5：运行默认构建确认 IT 被跳过**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am clean verify
```

预期：`BUILD SUCCESS`；Surefire 执行所有 `*Test`；Failsafe 明确跳过容器 IT。

- [ ] **步骤 6：运行 integration profile**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am clean verify -Pintegration
```

预期：`RedisSessionVerifierIT`、`RedisTokenBucketLimiterIT`、`GatewaySecurityIT`、`NacosGatewayRoutingIT` 全部出现；Failures=0、Errors=0、Skipped=0；资源清理断言通过。

- [ ] **步骤 7：提交**

```bash
git diff --check
git add -- educloud-backend/educloud-gateway
git commit -m "test(gateway): verify redis nacos security flow"
```

## 任务 13A：让 Testcontainers 支持 Rocky 私有 Redis/Nacos 镜像

> **执行约束：** 用户已选择内联执行且明确禁止子智能体。本任务使用 `executing-plans` 和 `test-driven-development` 在当前对话内完成。

**目标：** 保留官方镜像作为可移植默认值，同时让 Rocky 的 Common/Gateway 集成测试通过两个非秘密环境变量使用用户现有的私有 Redis、Nacos 镜像。

**架构：** Common 与 Gateway 各自拥有只存在于 `src/test` 的小型镜像解析器，避免测试工具进入生产 JAR或建立跨模块 test-jar 依赖。解析器读取环境变量、验证显式非 `latest` 标签并返回 `DockerImageName`；所有相关 IT 只依赖解析器，不再直接解析镜像字符串。

**文件：**

- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/testcontainers/TestContainerImages.java`
- 创建：`educloud-backend/educloud-common/src/test/java/com/educloud/common/testcontainers/TestContainerImagesTest.java`
- 修改：`educloud-backend/educloud-common/src/test/java/com/educloud/common/id/RedisIdentifierConcurrencyIT.java`
- 修改：`educloud-backend/educloud-common/src/test/java/com/educloud/common/id/RedisWorkerLeaseRepositoryIT.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/TestContainerImages.java`
- 创建：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/TestContainerImagesTest.java`
- 修改：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/GatewaySecurityIT.java`
- 修改：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/NacosGatewayRoutingIT.java`
- 修改：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/RedisSessionVerifierIT.java`
- 修改：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration/RedisTokenBucketLimiterIT.java`
- 修改：`deploy/tests/common-module-contract-tests.sh`
- 修改：`deploy/tests/gateway-module-contract-tests.sh`

- [ ] **步骤 1：为 Common 镜像解析器编写失败测试**

创建 `TestContainerImagesTest`，使用 `Map<String, String>::get` 注入受控环境，不修改真实进程环境。测试必须覆盖默认值、私有镜像、空值、无标签、`latest` 和非法引用：

```java
package com.educloud.common.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TestContainerImagesTest {

    private static final String PRIVATE_REDIS =
            "swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/redis:7.2.5-alpine";

    @Test
    void usesPinnedOfficialRedisImageWhenOverrideIsAbsent() {
        assertThat(TestContainerImages.redis(Map.<String, String>of()::get).asCanonicalNameString())
                .isEqualTo("redis:7.2.5-alpine");
    }

    @Test
    void acceptsPinnedPrivateRedisImage() {
        var environment = Map.of(TestContainerImages.REDIS_IMAGE_ENV, PRIVATE_REDIS);
        assertThat(TestContainerImages.redis(environment::get).asCanonicalNameString())
                .isEqualTo(PRIVATE_REDIS);
    }

    @Test
    void rejectsBlankTaglessLatestAndInvalidOverrides() {
        for (String value : new String[] {" ", "private.example/redis", "private.example/redis:latest",
                "https://private.example/redis:7.2.5"}) {
            var environment = Map.of(TestContainerImages.REDIS_IMAGE_ENV, value);
            assertThatThrownBy(() -> TestContainerImages.redis(environment::get))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(TestContainerImages.REDIS_IMAGE_ENV);
        }
    }
}
```

- [ ] **步骤 2：为 Gateway Redis/Nacos 镜像解析器编写失败测试**

创建同包的 `TestContainerImagesTest`，分别锁定两个默认值和两个 Rocky 私有覆盖值，并对 Redis/Nacos 都验证非法覆盖失败：

```java
package com.educloud.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TestContainerImagesTest {

    @Test
    void resolvesDefaultAndPrivateImagesIndependently() {
        var environment = Map.of(
                TestContainerImages.REDIS_IMAGE_ENV,
                "swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/redis:7.2.5-alpine",
                TestContainerImages.NACOS_IMAGE_ENV,
                "swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/nacos/nacos-server:v2.3.2");

        assertThat(TestContainerImages.redis(Map.<String, String>of()::get).asCanonicalNameString())
                .isEqualTo("redis:7.2.5-alpine");
        assertThat(TestContainerImages.nacos(Map.<String, String>of()::get).asCanonicalNameString())
                .isEqualTo("nacos/nacos-server:v2.3.2");
        assertThat(TestContainerImages.redis(environment::get).asCanonicalNameString())
                .isEqualTo(environment.get(TestContainerImages.REDIS_IMAGE_ENV));
        assertThat(TestContainerImages.nacos(environment::get).asCanonicalNameString())
                .isEqualTo(environment.get(TestContainerImages.NACOS_IMAGE_ENV));
    }

    @Test
    void rejectsInvalidRedisAndNacosOverridesBeforeContainerCreation() {
        for (String variable : new String[] {
                TestContainerImages.REDIS_IMAGE_ENV, TestContainerImages.NACOS_IMAGE_ENV}) {
            for (String value : new String[] {"", "private.example/image", "private.example/image:latest",
                    "https://private.example/image:1"}) {
                var environment = Map.of(variable, value);
                assertThatThrownBy(() -> TestContainerImages.resolve(variable, "safe/image:1", environment::get))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(variable);
            }
        }
    }
}
```

- [ ] **步骤 3：运行红灯并确认缺少解析器**

```bash
mvn -f educloud-backend/pom.xml \
  -pl educloud-common,educloud-gateway \
  -am \
  -Dtest='com.educloud.common.testcontainers.TestContainerImagesTest,com.educloud.gateway.integration.TestContainerImagesTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：编译失败，明确报告 `TestContainerImages` 不存在；失败原因只能是尚未实现解析器，而不是现有测试失败。

- [ ] **步骤 4：实现两个模块内解析器的最小代码**

Common 解析器公开 `redis()` 供 `com.educloud.common.id` 使用；Gateway 解析器保持 package-private。两者的核心解析逻辑一致：

```java
package com.educloud.common.testcontainers;

import java.util.function.Function;
import org.testcontainers.utility.DockerImageName;

public final class TestContainerImages {

    public static final String REDIS_IMAGE_ENV = "EDUCLOUD_TEST_REDIS_IMAGE";
    private static final String DEFAULT_REDIS_IMAGE = "redis:7.2.5-alpine";

    private TestContainerImages() {
    }

    public static DockerImageName redis() {
        return redis(System::getenv);
    }

    static DockerImageName redis(Function<String, String> environment) {
        return resolve(REDIS_IMAGE_ENV, DEFAULT_REDIS_IMAGE, environment);
    }

    private static DockerImageName resolve(
            String variable, String defaultImage, Function<String, String> environment) {
        String override = environment.apply(variable);
        String candidate = override == null ? defaultImage : override;
        if (override != null && (override.isBlank() || !override.equals(override.trim()))) {
            throw invalid(variable, "must be a non-blank image reference without surrounding whitespace", null);
        }
        try {
            DockerImageName image = DockerImageName.parse(candidate);
            image.assertValid();
            if ("latest".equalsIgnoreCase(image.getVersionPart())) {
                throw invalid(variable, "must use an explicit non-latest tag", null);
            }
            return image;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith(variable + " ")) {
                throw exception;
            }
            throw invalid(variable, "contains an invalid Docker image reference", exception);
        }
    }

    private static IllegalArgumentException invalid(String variable, String detail, Exception cause) {
        return new IllegalArgumentException(variable + " " + detail, cause);
    }
}
```

Gateway 版本的完整实现为：

```java
package com.educloud.gateway.integration;

import java.util.function.Function;
import org.testcontainers.utility.DockerImageName;

final class TestContainerImages {

static final String REDIS_IMAGE_ENV = "EDUCLOUD_TEST_REDIS_IMAGE";
static final String NACOS_IMAGE_ENV = "EDUCLOUD_TEST_NACOS_IMAGE";
private static final String DEFAULT_REDIS_IMAGE = "redis:7.2.5-alpine";
private static final String DEFAULT_NACOS_IMAGE = "nacos/nacos-server:v2.3.2";

private TestContainerImages() {
}

static DockerImageName redis() {
    return redis(System::getenv);
}

static DockerImageName nacos() {
    return nacos(System::getenv);
}

static DockerImageName redis(Function<String, String> environment) {
    return resolve(REDIS_IMAGE_ENV, DEFAULT_REDIS_IMAGE, environment);
}

static DockerImageName nacos(Function<String, String> environment) {
    return resolve(NACOS_IMAGE_ENV, DEFAULT_NACOS_IMAGE, environment);
}

static DockerImageName resolve(
        String variable, String defaultImage, Function<String, String> environment) {
    String override = environment.apply(variable);
    String candidate = override == null ? defaultImage : override;
    if (override != null && (override.isBlank() || !override.equals(override.trim()))) {
        throw invalid(variable, "must be a non-blank image reference without surrounding whitespace", null);
    }
    try {
        DockerImageName image = DockerImageName.parse(candidate);
        image.assertValid();
        if ("latest".equalsIgnoreCase(image.getVersionPart())) {
            throw invalid(variable, "must use an explicit non-latest tag", null);
        }
        return image;
    } catch (IllegalArgumentException exception) {
        if (exception.getMessage() != null && exception.getMessage().startsWith(variable + " ")) {
            throw exception;
        }
        throw invalid(variable, "contains an invalid Docker image reference", exception);
    }
}

private static IllegalArgumentException invalid(String variable, String detail, Exception cause) {
    return new IllegalArgumentException(variable + " " + detail, cause);
}
}
```

- [ ] **步骤 5：运行解析器测试确认绿灯**

```bash
mvn -f educloud-backend/pom.xml \
  -pl educloud-common,educloud-gateway \
  -am \
  -Dtest='com.educloud.common.testcontainers.TestContainerImagesTest,com.educloud.gateway.integration.TestContainerImagesTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：两个 `TestContainerImagesTest` 全部通过，Failures=0、Errors=0。

- [ ] **步骤 6：替换全部六个 IT 中的硬编码镜像**

Common 两个 IT 导入 `com.educloud.common.testcontainers.TestContainerImages` 并使用：

```java
new GenericContainer<>(TestContainerImages.redis()).withExposedPorts(6379)
```

Gateway 四个 IT 的常量改为：

```java
private static final DockerImageName REDIS_IMAGE = TestContainerImages.redis();
```

`NacosGatewayRoutingIT` 额外改为：

```java
private static final DockerImageName NACOS_IMAGE = TestContainerImages.nacos();
```

完成后搜索所有 `*IT.java`，必须不存在 `DockerImageName.parse("redis:7.2.5-alpine")` 或 `DockerImageName.parse("nacos/nacos-server:v2.3.2")`。

- [ ] **步骤 7：强化模块契约脚本**

两个脚本分别确认测试辅助类存在、变量名和默认版本固定，并拒绝 IT 重新散落硬编码解析。Common 增加：

```bash
test_source="$module_dir/src/test/java"
image_helper="$test_source/com/educloud/common/testcontainers/TestContainerImages.java"

if [[ -f "$image_helper" ]] \
    && grep -Fq 'EDUCLOUD_TEST_REDIS_IMAGE' "$image_helper" \
    && grep -Fq 'redis:7.2.5-alpine' "$image_helper"; then
  pass 'Common Testcontainers Redis image has a pinned overridable source'
else
  fail 'Common Testcontainers Redis image override contract is missing'
fi

if grep -REn --include='*IT.java' -- 'DockerImageName.parse\("redis:7.2.5-alpine"\)' "$test_source"; then
  fail 'Common integration tests contain a scattered Redis image reference'
else
  pass 'Common integration tests use the shared test image resolver'
fi
```

Gateway 增加完整的双镜像契约：

```bash
test_source="$module_dir/src/test/java"
image_helper="$test_source/com/educloud/gateway/integration/TestContainerImages.java"

if [[ -f "$image_helper" ]] \
    && grep -Fq 'EDUCLOUD_TEST_REDIS_IMAGE' "$image_helper" \
    && grep -Fq 'EDUCLOUD_TEST_NACOS_IMAGE' "$image_helper" \
    && grep -Fq 'redis:7.2.5-alpine' "$image_helper" \
    && grep -Fq 'nacos/nacos-server:v2.3.2' "$image_helper"; then
  pass 'Gateway Testcontainers images have pinned overridable sources'
else
  fail 'Gateway Testcontainers image override contract is missing'
fi

if grep -REn --include='*IT.java' -E \
    'DockerImageName.parse\("(redis:7.2.5-alpine|nacos/nacos-server:v2.3.2)"\)' \
    "$test_source"; then
  fail 'Gateway integration tests contain scattered image references'
else
  pass 'Gateway integration tests use the shared test image resolver'
fi
```

- [ ] **步骤 8：运行本地回归并提交实现**

```bash
bash deploy/tests/common-module-contract-tests.sh
bash deploy/tests/gateway-module-contract-tests.sh
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am clean verify
git diff --check
git add -- \
  educloud-backend/educloud-common/src/test/java/com/educloud/common/testcontainers \
  educloud-backend/educloud-common/src/test/java/com/educloud/common/id/RedisIdentifierConcurrencyIT.java \
  educloud-backend/educloud-common/src/test/java/com/educloud/common/id/RedisWorkerLeaseRepositoryIT.java \
  educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/integration \
  deploy/tests/common-module-contract-tests.sh \
  deploy/tests/gateway-module-contract-tests.sh
git commit -m "test(gateway): allow private integration images"
```

预期：两个模块契约脚本返回 0；默认 `clean verify` 为 `BUILD SUCCESS` 且不启动容器。不得暂存或提交 `Untitled-1.sh` 以及任务 14 尚未收口的文件。

- [ ] **步骤 9：在 Rocky 使用用户私有镜像运行集成测试**

用户同步任务 13A 的代码后，在项目根目录执行：

```bash
export EDUCLOUD_TEST_REDIS_IMAGE='swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/redis:7.2.5-alpine'
export EDUCLOUD_TEST_NACOS_IMAGE='swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/nacos/nacos-server:v2.3.2'

mvn -f educloud-backend/pom.xml \
  -pl educloud-gateway \
  -am clean verify \
  -Pintegration \
  2>&1 | tee /tmp/m02-integration.log

M02_INTEGRATION_EXIT=${PIPESTATUS[0]}
printf 'M02_INTEGRATION_EXIT=%s\n' "$M02_INTEGRATION_EXIT"

unset EDUCLOUD_TEST_REDIS_IMAGE
unset EDUCLOUD_TEST_NACOS_IMAGE
```

预期：日志中 `Creating container for image:` 后出现指定私有 Redis 和 Nacos 引用；Common 2 个 Redis IT 与 Gateway 4 个 IT 全部执行；`M02_INTEGRATION_EXIT=0`、`BUILD SUCCESS`、Failures=0、Errors=0、Skipped=0。

## 任务 14：Rocky 启动验收、全量审查和文档收口

**文件：**

- 创建：`deploy/tests/gateway-rocky-smoke-tests.sh`
- 修改：`educloud-backend/README.md`
- 修改：`docs/superpowers/specs/2026-08-20-educloud-gateway-design.md`
- 修改：`docs/superpowers/plans/2026-08-20-educloud-gateway.md`

- [x] **步骤 1：写 Rocky smoke 脚本并先验证失败路径**（`gateway-rocky-smoke-tests.sh` 已交付；缺失 `GATEWAY_JWKS_LOCATION` 时在启动 Java 前失败，退出码 1，且 8080 无残留进程）

脚本接收已经 source 的 runtime env 和 JAR 路径，使用 `set -euo pipefail`，先验证 Redis/Nacos 可达、专用 Nacos 凭据非空、JWKS 文件可读、HMAC Secret 解码不少于 32 字节；任何缺项在启动 Java 前失败。

脚本必须：

```text
以 educloud_gateway 启动，不使用管理员账号
后台进程 PID 写入 0700 临时目录
等待业务端口 8080，以及 127.0.0.1:8081 的 liveness/readiness
查询 Nacos 确认 educloud-gateway 健康实例
断言 protected 无 Token=401
断言 /internal/v1/**=404
断言已知无实例 route=503
断言允许/拒绝 Origin、requestId、安全头
写入临时 Redis ACTIVE session 后验证合法 Token
触发限流并观察 429 与 Retry-After
停止 Gateway；不停止共享 Redis/Nacos
删除测试 session、临时 key/JWKS/HMAC/private key/PID/log
确认 8080 无残留进程
```

先用缺失 `GATEWAY_JWKS_LOCATION` 运行，预期非 0 且没有 Java 进程；再进入正式门禁。

- [x] **步骤 2：运行全部本地/CI 门禁**（9 个 `deploy/tests/*-tests.sh` 契约脚本全过；默认 `clean verify` 与 `-Pintegration` 均 `BUILD SUCCESS`；14 个集成测试零失败/错误/跳过）

```bash
bash deploy/tests/check-prerequisites-tests.sh
bash deploy/tests/generate-local-env-tests.sh
bash deploy/tests/compose-contract-tests.sh
bash deploy/tests/common-module-contract-tests.sh
bash deploy/tests/gateway-module-contract-tests.sh
bash deploy/tests/prepare-gateway-local-env-tests.sh
bash deploy/tests/provision-gateway-nacos-tests.sh
bash deploy/tests/generate-gateway-test-material-tests.sh

mvn -f educloud-backend/pom.xml clean verify
export EDUCLOUD_TEST_REDIS_IMAGE='swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/redis:7.2.5-alpine'
export EDUCLOUD_TEST_NACOS_IMAGE='swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/nacos/nacos-server:v2.3.2'
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am clean verify -Pintegration
unset EDUCLOUD_TEST_REDIS_IMAGE
unset EDUCLOUD_TEST_NACOS_IMAGE
```

预期：所有 shell 门禁返回 0；父 reactor 只有 Common 与 Gateway；默认和 integration 构建均 `BUILD SUCCESS`，Common 2 个 Redis IT 与 Gateway 4 个 IT 显式执行且零失败/错误/跳过，Testcontainers 日志显示两个指定的私有镜像。

- [x] **步骤 3：在 JDK 17 和 JDK 21 验证字节码**（JDK 17.0.20.1 与 JDK 21.0.10 双构建均 `BUILD SUCCESS`，各 167 个单元测试零失败，`javap` 均输出 major version 61）

分别设置实际 JDK 17/21 的 `JAVA_HOME` 后运行：

```bash
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am clean verify
javap -verbose educloud-backend/educloud-gateway/target/classes/com/educloud/gateway/GatewayApplication.class | grep 'major version: 61'
```

预期：两套 JDK 均 `BUILD SUCCESS`；class major version 固定 61。

- [x] **步骤 4：在 Rocky Linux 8.9 执行真实启动**（失败路径退出 1；正式门禁输出 `All Rocky Linux Gateway smoke checks passed`：liveness/readiness、Nacos 注册与注销、401/404/503、CORS、安全头、ACTIVE 会话、429/Retry-After 全过）

```bash
bash deploy/scripts/prepare-gateway-local-env.sh
bash deploy/scripts/provision-gateway-nacos.sh

gateway_runtime_dir="$(mktemp -d /tmp/educloud-gateway.XXXXXX)"
chmod 700 "$gateway_runtime_dir"
bash deploy/scripts/generate-gateway-test-material.sh --output "$gateway_runtime_dir"

set -a
. deploy/docker-compose/.env
. "$gateway_runtime_dir/runtime.env"
set +a

bash deploy/tests/gateway-rocky-smoke-tests.sh educloud-backend/educloud-gateway/target/educloud-gateway-1.0.0-SNAPSHOT.jar "$gateway_runtime_dir"
```

预期：liveness/readiness、Nacos 注册、401/404/503、CORS、安全头、合法会话和 429 全通过；共享 Redis/Nacos 仍健康；Gateway 与临时材料均清理。

- [x] **步骤 5：执行范围审查与中文代码审查**（独立中文代码审查完成：1 项必须修复、3 项建议修复、若干仅供参考，已全部处理并复验；`chinese-code-review` 工具未安装，以等价独立审查替代并记录）

先逐项对照批准规格 1～17 节，确认每个要求映射到测试或 Rocky 证据。随后在当前对话使用 `chinese-code-review`，按“必须修复/建议修改/仅供参考”检查：

```text
架构：Reactive-only、无 DB/领域越界、路由优先级
正确性：可选认证、Redis session、Token Bucket、超时/错误
安全：JWKS 私钥拒绝、secret 泄漏、identity header、CORS、代理链
性能：事件循环无阻塞、DataBuffer 释放、低基数 metrics、无正缓存
可维护性：类型/命名一致、配置元数据、测试隔离、清理可靠
规格：M02/M03 边界、11 服务、Rocky 门禁、前端未修改
```

发现任何“必须修复”项时：先新增或强化失败测试，修复最小代码，再重新运行受影响局部测试、默认 verify、integration verify、模块契约和 `git diff --check`。不得带未解决必须修复项交付。

- [x] **步骤 6：更新事实文档**（README 状态改为 `【M02 已实现并验证，等待用户验收】`；设计规格与本计划同步更新；M03 未实现、Gateway 不签发 Token、三端前端仍 Mock 等边界表述保持准确）

`README.md` 只在全部证据通过后改为 `【M02 已实现并验证，等待用户验收】`，列出准确命令和 Rocky 结果；继续明确：

```text
Gateway 不签发 Token
M03 尚未实现
真实登录/刷新/注销/业务权限联调未完成
三套前端认证仍为 Mock/localStorage
数据库迁移 N/A：Gateway 不持久化业务事实
```

设计规格状态改为 `M02 已实现并验证，等待用户验收`；本计划勾选实际完成项并记录命令、时间、测试类和退出码，不填写推测结果。

- [x] **步骤 7：最终验证与收口提交**（`git diff --check` 通过；收口提交 `docs(gateway): record M02 verification`；工作区干净）

```bash
git diff --check
git status --short
git diff --stat

bash deploy/tests/gateway-module-contract-tests.sh
mvn -f educloud-backend/pom.xml -pl educloud-gateway -am verify
```

阅读完整输出，确认退出码 0 后提交：

```bash
git add -- educloud-backend/README.md docs/superpowers/specs/2026-08-20-educloud-gateway-design.md docs/superpowers/plans/2026-08-20-educloud-gateway.md deploy/tests/gateway-rocky-smoke-tests.sh
git commit -m "docs(gateway): record M02 verification"
git status --short
```

预期：最终 `git status --short` 为空。向用户报告准确 commit、测试证据、M03 未完成边界并等待验收；未经确认不开始 M03。

## 规格覆盖矩阵

| 规格章节 | 实施任务 |
|---|---|
| 1～3 目的、边界、依赖 | 1、2、14 |
| 4 请求处理顺序 | 3、5、7～10 |
| 5 JWT/JWKS | 2、4、7、13 |
| 6 Redis 会话 | 6、7、13 |
| 7 身份头与客户端 IP | 8、13 |
| 8 路由与访问策略 | 3、7、13 |
| 9 CORS/响应头/请求边界 | 2、9、10、13 |
| 10 Redis 限流 | 2、9、13 |
| 11 Nacos 身份与配置 | 2、12、13、14 |
| 12 超时与错误 | 5、10、11、13 |
| 13 健康、指标、日志、追踪 | 5、11、13、14 |
| 14 测试策略 | 1～13 |
| 15 Rocky 验收 | 12、14 |
| 16 实施顺序与质量门禁 | 全任务 |
| 17 完成定义 | 14 |

## 完成定义

- [x] 父 POM 只在 M01 后新增 `educloud-gateway`，JDK 17/21 均构建为 Java 17 可执行 JAR。
- [x] 静态路由覆盖 11 个服务、17 条优先级路由和 WebSocket，discovery locator 关闭。
- [x] JWKS/JWT、Redis session、匿名白名单、身份头和客户端 IP 都有确定性失败测试。
- [x] Redis 多 bucket 限流、登录正文回放、CORS、响应头和大小边界有单元与端到端证据。
- [x] Gateway 自有错误统一为 Common `ApiResponse<Void>`；下游业务响应不二次包装。
- [x] Redis 7.2.5 与 Nacos 2.3.2 IT 明确执行，零失败/错误/跳过且无资源残留。
- [x] Rocky 上启动、readiness/liveness、Nacos 注册、401/404/503/429 和安全入口语义通过。
- [x] Nacos 使用专用最小权限账号；任何 Git/log/argv/metric/trace 都不含 secret 或 Token。
- [x] 数据库迁移明确 N/A；无 MVC/JDBC/MyBatis/MySQL/RabbitMQ/业务领域越界。
- [x] `chinese-code-review` 没有未解决“必须修复”项，工作区干净。
- [x] README 仍准确说明 M03 和三套前端真实认证未完成；用户确认前不进入 M03。
