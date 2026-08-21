# Rocky Linux 8.9 后端测试环境准备

本文用于准备 EduCloud 后端的指定测试环境。所有命令均在 Rocky Linux 的 Bash 中执行。

## 支持状态警告

Rocky Linux 官方当前只支持每个主版本的最新次版本。Rocky Linux 8.9 已被 8.10 取代，因此 8.9 不再接收 Rocky 项目的更新和维护；它只能作为与现有测试环境一致的临时基线，不应作为新的生产环境。官方状态见 [Rocky Linux Releases](https://docs.rockylinux.org/latest/releases/)。

本项目当前按用户明确指定的 8.9 进行严格检查，不会静默升级到 8.10。若允许调整环境，应先另行确认把基线升级到 Rocky Linux 8.10，并同步更新前置检查与验证证据。

## 1. 确认系统版本

```bash
set -euo pipefail

source /etc/os-release
printf 'ID=%s VERSION_ID=%s\n' "$ID" "$VERSION_ID"
test "$ID" = "rocky"
test "$VERSION_ID" = "8.9"
```

不要在需要保持 8.9 的机器上直接运行无版本约束的 `dnf upgrade`；该命令可能把系统推进到 8.10。由于 8.9 已不受支持，这台机器必须限制在测试用途并记录该风险。

## 2. 安装基础工具和 Java 21（也接受 Java 17）

```bash
sudo dnf -y install \
  ca-certificates \
  curl \
  dnf-plugins-core \
  git \
  gzip \
  java-21-openjdk-devel \
  openssl \
  python3 \
  tar

java -version
javac -version
git --version
```

期望 `java -version` 和 `javac -version` 的主版本均为 21。前置检查也接受 Java 17；如需使用 Java 17，把安装包名改为 `java-17-openjdk-devel`。两种构建 JDK 都生成 Java 17 目标字节码。Spring Boot 3.2.5 官方支持 Java 17 至 Java 22，见 [Spring Boot 3.2.5 System Requirements](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.system-requirements)。

## 3. 安装并校验 Maven 3.9.16

Apache Maven 官方当前推荐 3.9.16。安装流程和校验文件来自 [Apache Maven 下载页](https://maven.apache.org/download.cgi) 与 [安装说明](https://maven.apache.org/install.html)。

```bash
MAVEN_VERSION=3.9.16
MAVEN_ARCHIVE="apache-maven-${MAVEN_VERSION}-bin.tar.gz"
MAVEN_BASE_URL="https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries"
MAVEN_TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$MAVEN_TMP_DIR"' EXIT

cd "$MAVEN_TMP_DIR"
curl --fail --location --remote-name "${MAVEN_BASE_URL}/${MAVEN_ARCHIVE}"
curl --fail --location --remote-name "${MAVEN_BASE_URL}/${MAVEN_ARCHIVE}.sha512"

MAVEN_SHA512="$(tr -d '\r\n' <"${MAVEN_ARCHIVE}.sha512")"
printf '%s  %s\n' "$MAVEN_SHA512" "$MAVEN_ARCHIVE" | sha512sum --check -

sudo tar -xzf "$MAVEN_ARCHIVE" -C /opt
sudo ln -sfn "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven

JAVA_HOME_PATH="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
sudo tee /etc/profile.d/educloud-java-maven.sh >/dev/null <<EOF
export JAVA_HOME=${JAVA_HOME_PATH}
export MAVEN_HOME=/opt/maven
export PATH=\$JAVA_HOME/bin:\$MAVEN_HOME/bin:\$PATH
EOF

source /etc/profile.d/educloud-java-maven.sh
mvn --version
```

期望 Maven 为 3.9.16，并且 Maven 输出中的 Java 主版本为 17 或 21。

## 4. 安装 Docker Engine 和 Compose 插件

Rocky Linux 官方 Docker 指南使用 Docker 的 RHEL 仓库，见 [Docker - Install Engine](https://docs.rockylinux.org/gemstones/containers/docker/)。Docker Compose 必须以 `docker compose` CLI 插件形式安装，不使用旧的 `docker-compose` 独立命令；插件说明见 [Docker Compose on Linux](https://docs.docker.com/compose/install/linux/)。

```bash
sudo dnf config-manager --add-repo \
  https://download.docker.com/linux/rhel/docker-ce.repo

sudo dnf -y install \
  containerd.io \
  docker-buildx-plugin \
  docker-ce \
  docker-ce-cli \
  docker-compose-plugin

sudo systemctl enable --now docker
sudo systemctl --no-pager --full status docker

sudo usermod -aG docker "$USER"
```

退出当前登录会话并重新登录，使 `docker` 用户组生效。然后运行：

```bash
docker --version
docker compose version
docker info
docker run --rm hello-world
```

前置检查接受 `docker compose` 插件主版本 2 或更高版本。不要安装或依赖旧的 Python `docker-compose` v1。

## 5. 检查本地端口占用

准备阶段的共享依赖默认使用以下端口。端口已经被其他进程占用时，应修改未提交的本地 `.env` 映射，不要直接停止未知进程。

```bash
for port in \
  3000 3306 5672 6379 8848 9000 9001 9090 9200 9411 9848 9849 15672
do
  if ss -lnt "sport = :$port" | grep -q LISTEN; then
    printf 'IN USE: %s\n' "$port"
  else
    printf 'FREE:   %s\n' "$port"
  fi
done
```

这里只检查占用情况，不自动修改 `firewalld`。默认测试环境不应把数据库、中间件管理端口直接暴露到公网。

## 6. 运行项目前置检查

在仓库根目录执行：

```bash
bash deploy/scripts/check-prerequisites.sh
```

成功输出的最后一行必须是：

```text
Prerequisite check passed
```

脚本会检查：

- 操作系统严格为 Rocky Linux 8.9；
- Java 主版本为 17 或 21；
- Maven 版本不低于 3.9；
- Git、Docker CLI 可执行；
- `docker compose` 插件主版本不低于 2；
- 当前用户能够连接 Docker daemon。

## 7. 进入共享依赖验证

前置检查通过后，再执行准备阶段的 Compose 验证：

```bash
bash deploy/scripts/generate-local-env.sh

docker compose \
  --env-file deploy/docker-compose/.env \
  -f deploy/docker-compose/compose.yml \
  config
```

生成器使用 `/dev/urandom` 为每项凭据生成独立随机值，把文件权限限制为 `600`，且不会在终端打印密码。`.env` 已被 Git 忽略，不得提交。已有 `.env` 时生成器默认拒绝覆盖；确认其中只有示例占位值后，可显式执行 `bash deploy/scripts/generate-local-env.sh --force`。

## 8. 准备 M02 Gateway 本地身份与测试材料

M02 复用已经运行的 Redis 和 Nacos，不需要数据库迁移，也不提供真实登录接口。Gateway 只校验由测试材料签发的 JWT，并校验 Redis 中的会话状态。

已有 `deploy/docker-compose/.env` 时，不要再执行 `generate-local-env.sh --force`。增量脚本只补充缺失的 Gateway 专用 Nacos 变量，不覆盖 MySQL、Redis 或 Nacos 服务端秘密：

```bash
bash deploy/scripts/prepare-gateway-local-env.sh \
  --env-file deploy/docker-compose/.env

grep -E '^NACOS_GATEWAY_(NAMESPACE|CONFIG_GROUP|DISCOVERY_GROUP|USERNAME)=' \
  deploy/docker-compose/.env
```

使用 Nacos 管理员身份创建并核对 Gateway 的 namespace、同名 user/role 和精确最小权限。管理员密码不要粘贴到命令参数、shell history 或日志；脚本会从无回显的标准输入读取：

```bash
export NACOS_ADMIN_USERNAME='nacos'
bash deploy/scripts/provision-gateway-nacos.sh \
  --env-file deploy/docker-compose/.env
unset NACOS_ADMIN_USERNAME
```

如果自动化环境必须通过环境变量提供管理员密码，只在受控进程环境内短暂设置 `NACOS_ADMIN_PASSWORD`，运行后立即 `unset`。不要使用 `set -x`。

创建一次性的 0700 目录并生成短期 RSA/JWKS/JWT 与 HMAC 测试材料：

```bash
GATEWAY_TEST_MATERIAL_DIR="$(mktemp -d /tmp/educloud-gateway.XXXXXX)"
chmod 700 "$GATEWAY_TEST_MATERIAL_DIR"

bash deploy/scripts/generate-gateway-test-material.sh \
  --output "$GATEWAY_TEST_MATERIAL_DIR"

test "$(stat -c '%a' "$GATEWAY_TEST_MATERIAL_DIR/private.pem")" = '600'
test "$(stat -c '%a' "$GATEWAY_TEST_MATERIAL_DIR/jwks.json")" = '644'
test "$(stat -c '%a' "$GATEWAY_TEST_MATERIAL_DIR/runtime.env")" = '600'
```

测试结束后删除整个临时材料目录：

```bash
rm -rf -- "$GATEWAY_TEST_MATERIAL_DIR"
unset GATEWAY_TEST_MATERIAL_DIR
```

不要停止共享 Redis/Nacos，也不要把 `.env`、私钥、JWT、HMAC Secret 或 Nacos 管理员凭据提交到 Git。

## 9. 执行 M02 Gateway Rocky 启动门禁

前置要求：smoke 脚本依赖 `redis-cli`（缺失时执行 `dnf install -y redis`）；`.env` 必须能被 bash source（值含空格的键需加引号，仓库模板已修正，历史生成的 `.env` 需手动修正 `ELASTICSEARCH_JAVA_OPTS="-Xms1g -Xmx1g"` 后再继续）。

先完成默认构建和容器集成测试；`-Pintegration` 会创建独立的 Redis/Nacos Testcontainers，不会连接或清理共享实例：

```bash
mvn -f educloud-backend/pom.xml \
  -pl educloud-gateway -am clean verify

mvn -f educloud-backend/pom.xml \
  -pl educloud-gateway -am clean verify -Pintegration
```

集成测试结束后再生成一套全新的短期材料，避免 15 分钟 JWT 在构建期间过期。不要复用第 8 节的旧 Token；如果第 8 节的演示目录仍存在，先按该节的清理命令删除，再执行：

```bash
GATEWAY_TEST_MATERIAL_DIR="$(mktemp -d /tmp/educloud-gateway.XXXXXX)"
chmod 700 "$GATEWAY_TEST_MATERIAL_DIR"

bash deploy/scripts/generate-gateway-test-material.sh \
  --output "$GATEWAY_TEST_MATERIAL_DIR"

set -a
. deploy/docker-compose/.env
. "$GATEWAY_TEST_MATERIAL_DIR/runtime.env"
set +a

gateway_jar='educloud-backend/educloud-gateway/target/educloud-gateway-1.0.0-SNAPSHOT.jar'
```

先验证缺少 JWKS 时脚本会在启动 Java 前失败；该失败路径不会删除测试材料：

```bash
if env -u GATEWAY_JWKS_LOCATION \
  bash deploy/tests/gateway-rocky-smoke-tests.sh \
  "$gateway_jar" "$GATEWAY_TEST_MATERIAL_DIR"; then
  echo 'FAIL: 缺少 JWKS 时 smoke 脚本错误地返回了成功' >&2
  exit 1
fi

if curl --silent --max-time 1 http://127.0.0.1:8080/ >/dev/null 2>&1; then
  echo 'FAIL: 失败路径启动了 Gateway 进程' >&2
  exit 1
fi
```

再执行正式启动门禁：

```bash
bash deploy/tests/gateway-rocky-smoke-tests.sh \
  "$gateway_jar" "$GATEWAY_TEST_MATERIAL_DIR"
```

脚本只启动并停止 Gateway，不停止共享 Redis/Nacos；它会为 Redis session/限流 key 生成独立的 `m02-smoke-*` 环境前缀，验证 Nacos 注册、liveness/readiness、401/404/503、CORS、安全响应头、ACTIVE 会话和 429/`Retry-After`，并只删除该前缀下的测试 key、PID、日志和整个临时材料目录。脚本结束后还要清除父 shell 中已经 source 的敏感变量：

```bash
unset GATEWAY_TEST_JWT \
  GATEWAY_RATE_LIMIT_HMAC_SECRET \
  GATEWAY_JWKS_LOCATION \
  GATEWAY_JWT_ISSUER \
  GATEWAY_JWT_AUDIENCE \
  GATEWAY_TEST_MATERIAL_DIR \
  gateway_jar
```
