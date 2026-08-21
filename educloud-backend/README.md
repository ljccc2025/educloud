# EduCloud Backend

当前状态：`【M03 已实现并验证，等待用户验收】`。

父 Maven 工程用于锁定 Java 17 字节码目标、依赖版本和统一构建插件。构建 JDK 只接受 17 或 21；使用 JDK 21 构建时仍通过 `--release 17` 生成 Java 17 字节码。M01 `educloud-common` 已完成；M02 `educloud-gateway` 已实现并完成全部门禁，等待用户验收。M03 `educloud-user` 已实现并完成本地全部门禁（单元 64/64、契约脚本全绿、IT 编译通过），正在 Rocky 8.9 执行最终门禁，等待用户验收。M04～M13 尚未实现。

Gateway 是不拥有数据库的 Reactive 边缘服务，只消费并验证身份凭证，不提供登录、刷新或 Token 签发。真实登录和业务权限能力由 M03 `educloud-user` 提供（学生注册、登录、Refresh 轮换、注销、改密、禁用/恢复、RBAC、Outbox、服务令牌与内部快照）。当前三套前端仍使用 Mock/localStorage，尚未完成真实认证联调；M02/M03 不修改任何前端代码。

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

M03 已在本地完成全部门禁：13 个 deploy 契约脚本全过（含新增 `provision-user-nacos-tests.sh` 与更新后的 compose/模块契约）；User 单元测试 64/64 零失败；`-Pintegration` 的 5 个集成测试类（2 个 Schema、Refresh 并发轮换、迁移校验、Outbox 发布链路）编译通过，待 Rocky 8.9 容器环境执行；`user-gateway-e2e-tests.sh` 覆盖注册/登录//me/并发刷新/宽限外重用撤销/注销/改密/禁用/Redis 失败关闭，独立 `m03-e2e-*` 前缀并自动清理。

M02 已在 Rocky Linux 8.9 完成全部门禁：9 个 deploy 契约脚本全过；`-Pintegration` 下 14 个 Gateway/Common 集成测试零失败（含 Testcontainers 私有镜像覆盖）；JDK 17 与 JDK 21 双构建均 `BUILD SUCCESS`，`javap` 确认 class major version 61；`gateway-rocky-smoke-tests.sh` 真实启动门禁全过。独立中文代码审查无未解决必须修复项。

M02/M03 仍然不代表前端真实认证联调已经完成；Gateway 不签发 Token，三套前端仍为 Mock/localStorage。

M01 已在 Rocky Linux 8.9、JDK 21 和 Docker 环境通过真实 Redis Testcontainers 门禁：两个集成测试类共执行 5 个测试，失败、错误、跳过均为 0。测试使用独立容器和随机环境命名空间，不连接当前共享 Redis。需要复验时运行：

```bash
bash deploy/tests/common-module-contract-tests.sh
mvn -f educloud-backend/pom.xml -pl educloud-common -am verify
mvn -f educloud-backend/pom.xml -pl educloud-common -am verify -Pintegration
```
