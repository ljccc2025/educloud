# EduCloud Backend

当前状态：`【M01 计划已批准，实施中】`。

父 Maven 工程用于锁定 Java 17 字节码目标、依赖版本和统一构建插件。构建 JDK 只接受 17 或 21；使用 JDK 21 构建时仍通过 `--release 17` 生成 Java 17 字节码。当前只声明 `educloud-common`：它是不可独立运行的公共 JAR，不是后端服务，不提供端口、Controller、领域模型或数据库。M02～M13 仍未实现。

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

当前构建只验证父工程和已加入的 Common 库，不代表任何后端服务已经实现或可以启动。
