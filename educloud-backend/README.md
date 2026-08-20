# EduCloud Backend

当前状态：`【准备阶段】`。

此目录目前只有父 Maven 工程，用于锁定 Java 17 字节码目标、依赖版本和统一构建插件。准备阶段不创建服务启动类、Controller、Entity、Mapper、占位接口或业务代码，也不声明尚未实现的 Maven 子模块。

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

## 准备阶段验证

从仓库根目录运行：

```bash
mvn -f educloud-backend/pom.xml help:effective-pom
mvn -f educloud-backend/pom.xml verify
```

当前父工程的 `verify` 只证明 Maven 配置可以解析并满足工具链约束，不代表任何后端服务已经实现或可以启动。
