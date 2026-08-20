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

## 2. 安装基础工具和 Java 17

```bash
sudo dnf -y install \
  ca-certificates \
  curl \
  dnf-plugins-core \
  git \
  gzip \
  java-17-openjdk-devel \
  tar

java -version
javac -version
git --version
```

期望 `java -version` 和 `javac -version` 的主版本均为 17。

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

期望 Maven 为 3.9.16，并且 Maven 输出中的 Java 主版本为 17。

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
- Java 主版本严格为 17；
- Maven 版本不低于 3.9；
- Git、Docker CLI 可执行；
- `docker compose` 插件主版本不低于 2；
- 当前用户能够连接 Docker daemon。

## 7. 进入共享依赖验证

前置检查通过后，再执行准备阶段的 Compose 验证：

```bash
cp deploy/docker-compose/.env.example deploy/docker-compose/.env

docker compose \
  --env-file deploy/docker-compose/.env \
  -f deploy/docker-compose/compose.yml \
  config
```

复制后必须先修改 `.env` 中的本地密码。`.env` 已被 Git 忽略，不得提交。只有 Compose 文件在后续准备任务中创建完成后，本节命令才可执行。
