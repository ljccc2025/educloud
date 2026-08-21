# EduCloud Backend

当前状态：`【M02 计划已批准，实施中】`。

父 Maven 工程用于锁定 Java 17 字节码目标、依赖版本和统一构建插件。构建 JDK 只接受 17 或 21；使用 JDK 21 构建时仍通过 `--release 17` 生成 Java 17 字节码。M01 `educloud-common` 已完成；M02 `educloud-gateway` 正在实施。M03～M13 尚未实现。

Gateway 是不拥有数据库的 Reactive 边缘服务，只消费并验证身份凭证，不提供登录、刷新或 Token 签发。真实登录和业务权限能力属于 M03。当前三套前端仍使用 Mock/localStorage，尚未完成真实认证联调；M02 不修改任何前端代码。

## 增量模块顺序

后端模块按以下顺序逐个加入父 POM；前一个模块完成测试、运行验证和审查并获得用户确认后，才开始下一个模块：

```text
M01 educloud-common
M02 educloud-gateway
M03 educloud-user
M04 educloud-file
M05 educloud-course
M06 educloud-content
M07 educloud-order
M08 educloud-payment
M09 educloud-notification
M10 educloud-live
M11 educloud-search
M12 educloud-analytics
M13 educloud-recommendation
```

执行顺序、条件门禁和旧路线图的覆盖关系见[后端模块执行顺序与准备门禁](../docs/superpowers/specs/2026-08-20-educloud-backend-module-execution.md)。

## 当前阶段验证

从仓库根目录运行：

```bash
mvn -f educloud-backend/pom.xml help:effective-pom
mvn -f educloud-backend/pom.xml verify
```

当前构建验证父工程、Common 库和正在实施的 Gateway。只有 M02 的全部门禁完成并经用户验收后，才能把 Gateway 描述为已完成；这也不代表 M03 登录或 Token 签发已经实现。

M01 已在 Rocky Linux 8.9、JDK 21 和 Docker 环境通过真实 Redis Testcontainers 门禁：两个集成测试类共执行 5 个测试，失败、错误、跳过均为 0。测试使用独立容器和随机环境命名空间，不连接当前共享 Redis。需要复验时运行：

```bash
bash deploy/tests/common-module-contract-tests.sh
mvn -f educloud-backend/pom.xml -pl educloud-common -am verify
mvn -f educloud-backend/pom.xml -pl educloud-common -am verify -Pintegration
```
