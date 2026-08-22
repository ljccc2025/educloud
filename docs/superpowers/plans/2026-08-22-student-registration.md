# 学生端注册与注册限流 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 学生端登录页增加可用的注册 Tab（真实调用后端注册接口），后端为注册接口加 Redis 双层限流（IP + 设备），并修复登录页假账号预填与固定错误文案问题。

**架构：** 前端在 student-portal Login.tsx 内做「登录|注册」双 Tab；后端新增 OncePerRequestFilter（RegistrationRateLimitFilter），用 StringRedisTemplate INCR+EXPIRE 计数，超限写 429（复用 CommonErrorCode.RATE_LIMITED），Redis 异常写 503（DEPENDENCY_UNAVAILABLE）。前端 http.ts 生成匿名 deviceId 并附加 X-Device-Id 头，apiErrorText 增加 429/503 中文映射。

**技术栈：** React 18 + Vite 5 + zustand + axios（前端）；Spring Boot 3.2.5 + Spring Data Redis + Testcontainers（后端）；Redis 7.2.5。

---

## 文件结构

**后端（educloud-backend/educloud-user/）：**
- 创建 `src/main/java/com/educloud/user/config/RegistrationRateLimitProperties.java` — 限流配置（@ConfigurationProperties）
- 创建 `src/main/java/com/educloud/user/security/RegistrationRateLimitFilter.java` — 注册限流过滤器（@Component @Order，OncePerRequestFilter）
- 修改 `src/main/resources/application.yml` — 增加限流配置默认值
- 创建 `src/test/java/com/educloud/user/security/RegistrationRateLimitFilterTest.java` — 单元测试（mock StringRedisTemplate）
- 创建 `src/test/java/com/educloud/user/security/RegistrationRateLimitIT.java` — 集成测试（真实 Redis GenericContainer）
- 修改 `src/test/java/com/educloud/user/testcontainers/TestContainerImages.java` — 增加 redis() 镜像解析

**前端（educloud-frontend/student-portal/）：**
- 修改 `src/services/http.ts` — deviceId 生成与 X-Device-Id 头、apiErrorText 增加 RATE_LIMITED/DEPENDENCY_UNAVAILABLE 映射
- 修改 `src/pages/Login.tsx` — 双 Tab 登录/注册、注册表单与校验、成功回登录填用户名、移除假账号预填、真实错误提示

**文档：**
- 修改 `docs/superpowers/specs/2026-08-22-student-registration-design.md`（错误码复用 common——已改）

---

### 任务 1：后端限流（配置 + 过滤器 + 单元测试）

**文件：**
- 创建 `educloud-backend/educloud-user/src/main/java/com/educloud/user/config/RegistrationRateLimitProperties.java`
- 创建 `educloud-backend/educloud-user/src/main/java/com/educloud/user/security/RegistrationRateLimitFilter.java`
- 修改 `educloud-backend/educloud-user/src/main/resources/application.yml`
- 创建 `educloud-backend/educloud-user/src/test/java/com/educloud/user/security/RegistrationRateLimitFilterTest.java`

- [ ] **步骤 1：编写失败的单元测试**

创建 `RegistrationRateLimitFilterTest.java`：

```java
package com.educloud.user.security;

import com.educloud.common.error.CommonErrorCode;
import com.educloud.user.config.RegistrationRateLimitProperties;
import com.educloud.user.config.SessionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RegistrationRateLimitFilterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RegistrationRateLimitProperties properties;
    private RegistrationRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        properties = new RegistrationRateLimitProperties();
        properties.setEnabled(true);
        properties.setIpMaxAttempts(5);
        properties.setDeviceMaxAttempts(3);
        properties.setWindow(Duration.ofMinutes(5));
        properties.setRedisKeyPrefix("educloud:{env}:ratelimit");
        SessionProperties session = mock(SessionProperties.class);
        when(session.environment()).thenReturn("test");
        filter = new RegistrationRateLimitFilter(redis, properties, session, new ObjectMapper());
    }

    private MockHttpServletRequest registerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    void allowsRequestWithinIpLimit() throws Exception {
        when(values.increment(anyString())).thenReturn(1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(registerRequest(), response, (req, res) -> {
            res.setStatus(200);
        });
        assertThat(response.getStatus()).isEqualTo(200);
        verify(values).increment(argThat(key -> key.startsWith("educloud:test:ratelimit:register-ip:")));
    }

    @Test
    void rejectsWhenIpLimitExceeded() throws Exception {
        when(values.increment(anyString())).thenReturn(6L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(registerRequest(), response, (req, res) -> res.setStatus(200));
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void rejectsWhenDeviceLimitExceeded() throws Exception {
        when(values.increment(anyString())).thenReturn(4L);
        MockHttpServletRequest request = registerRequest();
        request.addHeader("X-Device-Id", "device-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> res.setStatus(200));
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void fallsBackToIpOnlyWhenDeviceHeaderMissing() throws Exception {
        when(values.increment(anyString())).thenReturn(1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(registerRequest(), response, (req, res) -> res.setStatus(200));
        verify(values, times(1)).increment(anyString());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void returns503WhenRedisFails() throws Exception {
        when(values.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(registerRequest(), response, (req, res) -> res.setStatus(200));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void ignoresNonRegisterPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> res.setStatus(200));
        verify(values, never()).increment(anyString());
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
```

注意：ApiResponseFactory 需要非 null 的 RequestContextAccessor/Clock——测试中传 null 会导致 NPE。实现时若 ApiResponseFactory 构造器对 null 有 Objects.requireNonNull，则 Filter 的错误写出不依赖它，改为直接用 ObjectMapper 序列化标准错误体（见步骤 3 实现）。

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-user -am test -Dtest=RegistrationRateLimitFilterTest`
预期：编译失败，`RegistrationRateLimitFilter` 不存在。

- [ ] **步骤 3：实现配置类与过滤器**

创建 `RegistrationRateLimitProperties.java`：

```java
package com.educloud.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 注册限流配置（educloud.user.registration.rate-limit）。依据：M03 注册限流设计 4.3 节。 */
@ConfigurationProperties("educloud.user.registration.rate-limit")
public final class RegistrationRateLimitProperties {

    private boolean enabled = true;
    private int ipMaxAttempts = 5;
    private int deviceMaxAttempts = 3;
    private Duration window = Duration.ofMinutes(5);
    private String redisKeyPrefix = "educloud:{env}:ratelimit";

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int ipMaxAttempts() { return ipMaxAttempts; }
    public void setIpMaxAttempts(int ipMaxAttempts) { this.ipMaxAttempts = ipMaxAttempts; }
    public int deviceMaxAttempts() { return deviceMaxAttempts; }
    public void setDeviceMaxAttempts(int deviceMaxAttempts) { this.deviceMaxAttempts = deviceMaxAttempts; }
    public Duration window() { return window; }
    public void setWindow(Duration window) { this.window = window; }
    public String redisKeyPrefix() { return redisKeyPrefix; }
    public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }
}
```

创建 `RegistrationRateLimitFilter.java`：

```java
package com.educloud.user.security;

import com.educloud.common.error.CommonErrorCode;
import com.educloud.user.config.RegistrationRateLimitProperties;
import com.educloud.user.config.SessionProperties;
import com.educloud.user.session.SessionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册限流过滤器：POST /api/v1/auth/register 按 IP + 设备双层 Redis 计数，超限 429；
 * Redis 不可用失败关闭 503。依据：M03 注册限流设计 4 节。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class RegistrationRateLimitFilter extends OncePerRequestFilter {

    private static final String REGISTER_PATH = "/api/v1/auth/register";

    private final StringRedisTemplate redis;
    private final RegistrationRateLimitProperties properties;
    private final SessionProperties sessionProperties;
    private final ObjectMapper objectMapper;

    public RegistrationRateLimitFilter(
            StringRedisTemplate redis,
            RegistrationRateLimitProperties properties,
            SessionProperties sessionProperties,
            ObjectMapper objectMapper) {
        this.redis = redis;
        this.properties = properties;
        this.sessionProperties = sessionProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !REGISTER_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String deviceHeader = request.getHeader("X-Device-Id");
            if (deviceHeader != null && !deviceHeader.isBlank()) {
                if (exceeded("register-device", SessionFactory.sha256Hex(deviceHeader.trim()),
                        properties.deviceMaxAttempts(), response)) {
                    return;
                }
            }
            if (exceeded("register-ip", SessionFactory.sha256Hex(request.getRemoteAddr()),
                    properties.ipMaxAttempts(), response)) {
                return;
            }
            chain.doFilter(request, response);
        } catch (Exception failure) {
            writeError(response, CommonErrorCode.DEPENDENCY_UNAVAILABLE, 503, null);
        }
    }

    private boolean exceeded(String subKey, String hashedValue, int maxAttempts, HttpServletResponse response)
            throws IOException {
        String key = properties.redisKeyPrefix().replace("{env}", sessionProperties.environment())
                + ":" + subKey + ":" + hashedValue;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, properties.window());
        }
        if (count != null && count > maxAttempts) {
            writeError(response, CommonErrorCode.RATE_LIMITED, 429, retryAfter());
            return true;
        }
        return false;
    }

    private long retryAfter() {
        return Math.max(1L, properties.window().toSeconds());
    }

    private void writeError(HttpServletResponse response, CommonErrorCode code, int status, Long retryAfter)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code.code());
        body.put("message", code.defaultMessage());
        body.put("data", null);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (retryAfter != null) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
        }
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
```

注意：Redis key 的 `{env}` 由 SessionProperties.environment() 替换（与 Gateway 会话 key 同源），构造器为 4 参（redis, properties, sessionProperties, objectMapper）。

在 `application.yml` 的 `educloud.user` 节增加：

```yaml
    registration:
      rate-limit:
        enabled: ${EDUCLOUD_USER_REGISTRATION_RATE_LIMIT_ENABLED:true}
        ip-max-attempts: ${EDUCLOUD_USER_REGISTRATION_IP_MAX_ATTEMPTS:5}
        device-max-attempts: ${EDUCLOUD_USER_REGISTRATION_DEVICE_MAX_ATTEMPTS:3}
        window: ${EDUCLOUD_USER_REGISTRATION_RATE_LIMIT_WINDOW:5m}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-user -am test -Dtest=RegistrationRateLimitFilterTest`
预期：PASS（6 个用例全绿）。若 ApiResponseFactory null 参数报错，按步骤 1 注记改为 Filter 直接用 ObjectMapper 写错误体（即步骤 3 的实现已如此）。

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-user/src/main/java/com/educloud/user/config/RegistrationRateLimitProperties.java educloud-backend/educloud-user/src/main/java/com/educloud/user/security/RegistrationRateLimitFilter.java educloud-backend/educloud-user/src/main/resources/application.yml educloud-backend/educloud-user/src/test/java/com/educloud/user/security/RegistrationRateLimitFilterTest.java
git commit -m "feat(user): registration rate limit filter (ip + device)"
```

---

### 任务 2：Filter 集成与上下文适配

**文件：**
- 修改 `educloud-backend/educloud-user/src/test/java/com/educloud/user/UserApplicationContextTest.java`（如上下文加载失败）
- 修改 `educloud-backend/educloud-user/src/test/java/com/educloud/user/MethodSecurityAndAdminEndpointsTest.java`（如上下文加载失败）

- [ ] **步骤 1：跑全量单测确认上下文**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-user -am test`
预期：UserApplicationContextTest / MethodSecurityAndAdminEndpointsTest 若因新 Filter 依赖（StringRedisTemplate mock 已有、ObjectMapper 已有、SessionProperties 已有）失败，则补对应 mock/bean；否则全绿。

- [ ] **步骤 2：修复上下文（如失败）**

若 `UserApplicationContextTest` 报缺少 bean：在其 @TestConfiguration 中补充 `RegistrationRateLimitProperties` 与 `RegistrationRateLimitFilter` 相关 bean（或确认组件扫描已覆盖且依赖可用）。

- [ ] **步骤 3：全量单测**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-user -am test`
预期：BUILD SUCCESS（含新增 Filter 测试）。

- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-user/src/test
git commit -m "test(user): adapt contexts for registration rate limit filter"
```

---

### 任务 3：注册限流集成测试（真实 Redis）

**文件：**
- 修改 `educloud-backend/educloud-user/src/test/java/com/educloud/user/testcontainers/TestContainerImages.java`（增加 redis()）
- 创建 `educloud-backend/educloud-user/src/test/java/com/educloud/user/security/RegistrationRateLimitIT.java`

- [ ] **步骤 1：TestContainerImages 增加 redis()**

在 `TestContainerImages.java` 增加：

```java
static final String REDIS_IMAGE_ENV = "EDUCLOUD_TEST_REDIS_IMAGE";
private static final String DEFAULT_REDIS_IMAGE = "redis:7.2.5-alpine";

public static DockerImageName redis() {
    return redis(System::getenv);
}

static DockerImageName redis(Function<String, String> environment) {
    return resolve(REDIS_IMAGE_ENV, DEFAULT_REDIS_IMAGE, environment);
}
```

- [ ] **步骤 2：编写集成测试**

创建 `RegistrationRateLimitIT.java`：

```java
package com.educloud.user.security;

import com.educloud.user.config.RegistrationRateLimitProperties;
import com.educloud.user.testcontainers.TestContainerImages;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RegistrationRateLimitIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(TestContainerImages.redis())
            .withExposedPorts(6379);

    private static RegistrationRateLimitFilter filter;

    @BeforeAll
    static void connect() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        RegistrationRateLimitProperties properties = new RegistrationRateLimitProperties();
        properties.setEnabled(true);
        properties.setIpMaxAttempts(5);
        properties.setDeviceMaxAttempts(3);
        properties.setWindow(Duration.ofMinutes(5));
        properties.setRedisKeyPrefix("it:" + java.util.UUID.randomUUID().toString().substring(0, 8) + ":{env}:ratelimit");
        // SessionProperties 是 record：environment 用构造器传入（与单元测试的 mock 不同）。
        SessionProperties session = new SessionProperties(
                "test", Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofSeconds(5), false);
        filter = new RegistrationRateLimitFilter(redis, properties, session, new ObjectMapper());
    }

    private MockHttpServletResponse hit(String deviceId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setRemoteAddr("10.1.1.1");
        if (deviceId != null) {
            request.addHeader("X-Device-Id", deviceId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> res.setStatus(200));
        return response;
    }

    @Test
    void sixthIpRequestWithinWindowIs429() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(hit(null).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse sixth = hit(null);
        assertThat(sixth.getStatus()).isEqualTo(429);
        assertThat(sixth.getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void deviceLimitKicksInEarlier() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(hit("device-x").getStatus()).isEqualTo(200);
        }
        assertThat(hit("device-x").getStatus()).isEqualTo(429);
    }
}
```

- [ ] **步骤 3：本地编译集成测试**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-user -am test-compile -DskipTests`
预期：编译通过（IT 默认被 failsafe 跳过，本地无 Docker 不执行）。

- [ ] **步骤 4：VM 执行集成测试**

在 VM 同步代码后运行：`mvn -f educloud-backend/pom.xml -pl educloud-user -am verify -Pintegration -Dit.test=RegistrationRateLimitIT -Dfailsafe.failIfNoSpecifiedTests=false`
预期：2 个用例 PASS。

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-user/src/test/java/com/educloud/user/testcontainers/TestContainerImages.java educloud-backend/educloud-user/src/test/java/com/educloud/user/security/RegistrationRateLimitIT.java
git commit -m "test(user): registration rate limit integration test"
```

---

### 任务 4：前端 http.ts（deviceId + 错误映射）

**文件：**
- 修改 `educloud-frontend/student-portal/src/services/http.ts`

- [ ] **步骤 1：修改 http.ts**

在 `http.ts` 增加 deviceId 管理与 X-Device-Id 头，并扩展 apiErrorText：

```ts
const DEVICE_KEY = 'educloud_device_id';

function deviceId(): string {
  let id = localStorage.getItem(DEVICE_KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(DEVICE_KEY, id);
  }
  return id;
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (config.url?.startsWith('/auth/register')) {
    config.headers = config.headers ?? {};
    config.headers['X-Device-Id'] = deviceId();
  }
  return config;
});
```

apiErrorText switch 增加：

```ts
case 'RATE_LIMITED':
  return '操作太频繁，请稍后再试';
case 'DEPENDENCY_UNAVAILABLE':
  return '服务暂不可用，请稍后重试';
```

- [ ] **步骤 2：typecheck**

运行：`cd educloud-frontend/student-portal && npx tsc --noEmit`
预期：无错误。

- [ ] **步骤 3：Commit**

```bash
git add educloud-frontend/student-portal/src/services/http.ts
git commit -m "feat(frontend): device id header and rate limit error mapping"
```

---

### 任务 5：前端 Login.tsx 双 Tab 注册表单

**文件：**
- 修改 `educloud-frontend/student-portal/src/pages/Login.tsx`

- [ ] **步骤 1：改造 Login.tsx**

保留现有品牌区 JSX 与登录表单样式类；新增：

```tsx
// 新增状态
const [mode, setMode] = useState<'login' | 'register'>('login');
const [username, setUsername] = useState('');
const [phone, setPhone] = useState('');
const [displayName, setDisplayName] = useState('');
const [confirmPassword, setConfirmPassword] = useState('');
const [notice, setNotice] = useState('');
// email/password 改为空串初始值（移除假账号预填）

const handleSubmit = async (e: React.FormEvent) => {
  e.preventDefault();
  setError('');
  setNotice('');
  setLoading(true);
  try {
    if (mode === 'register') {
      if (password !== confirmPassword) {
        setError('两次输入的密码不一致');
        return;
      }
      await authApi.register({
        username,
        password,
        email,
        phone,
        displayName: displayName || username,
      });
      setMode('login');
      setEmail(username);
      setPassword('');
      setNotice('注册成功，请使用新账号登录');
      return;
    }
    const success = await login(email, password);
    if (success) {
      navigate(redirectTo, { replace: true });
    } else {
      setError(useAuthStore.getState().error ?? '登录失败，请重试');
    }
  } catch (err) {
    setError(err instanceof Error ? err.message : '操作失败，请重试');
  } finally {
    setLoading(false);
  }
};
```

表单 JSX：登录/注册字段按 mode 条件渲染（注册额外显示用户名/手机号/昵称/确认密码），顶部加 Tab 切换（复用现有 section-label 样式），提交按钮文案切换（登录/注册），错误条与绿色 notice 条复用现有样式类。

- [ ] **步骤 2：typecheck + build**

运行：`cd educloud-frontend/student-portal && npx tsc --noEmit && npx vite build`
预期：均通过。

- [ ] **步骤 3：Commit**

```bash
git add educloud-frontend/student-portal/src/pages/Login.tsx
git commit -m "feat(frontend): student registration tab with real backend"
```

---

### 任务 6：VM 部署与联调验收

**文件：**
- VM：重建 educloud-user jar、同步前端、重启服务

- [ ] **步骤 1：VM 重建后端 jar 并重启**

同步代码到 VM 后：

```bash
cd /root/educloud/.worktrees/educloud-backend-foundation/educloud-backend
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-17.0.20.1.1-1.1.el8_10.x86_64
export PATH=$JAVA_HOME/bin:$PATH
/opt/maven/bin/mvn -q -pl educloud-user -am package -DskipTests
bash /root/educloud/.worktrees/educloud-backend-foundation/deploy/scripts/start-dev.sh
```

- [ ] **步骤 2：curl 验收注册限流**

```bash
# 同一 IP 连发 6 次注册，前 5 次 201/409，第 6 次应 429 且带 Retry-After
for i in 1 2 3 4 5 6; do
  curl -s -o /tmp/r$i.json -w "attempt$i=%{http_code}\n" -X POST http://127.0.0.1:5173/api/v1/auth/register \
    -H "Content-Type: application/json" -H "X-Device-Id: verify-device" \
    -d "{\"username\":\"rl_$i\",\"password\":\"Rate@2026test\",\"email\":\"rl_$i@educloud.cn\",\"phone\":\"1370000000$i\"}";
done
# 预期第 6 次 429 + Retry-After 头
```

- [ ] **步骤 3：浏览器验收清单**

- 打开 http://192.168.100.136:5173，登录页无假账号预填
- 「注册」Tab：填表 → 注册成功 → 自动回登录 Tab + 绿色提示 + 用户名已填入 → 输密码登录成功
- 连点注册 6 次 → 出现「操作太频繁，请稍后再试」
- 教师端/管理端不受影响（demo_teacher / demo_admin 正常登录）

---

### 任务 7：门禁回归与收尾

**文件：**
- 文档与全量验证

- [ ] **步骤 1：回归验证**

```bash
mvn -f educloud-backend/pom.xml -pl educloud-user -am clean verify
bash deploy/tests/user-module-contract-tests.sh
git diff --check
```

预期：全部通过。

- [ ] **步骤 2：Commit + 汇报**

```bash
git add -A
git commit -m "docs(user): record registration feature verification"
```

向用户汇报验收地址与账号，等待验收。