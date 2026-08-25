"""
部署代码审查修复（16 项 BUG）到 VM：
1. 上传 32 个修复文件（LF 行尾）到 /root/educloud/.worktrees/educloud-backend-foundation
2. 执行 V005 迁移（course 库 pre_submit_lifecycle_status 列，幂等）
3. 编译 4 个受影响模块（user/course/content/order）
4. 重启受影响服务并轮询健康检查
"""
import os
import posixpath
import time
import paramiko

VM_HOST = "192.168.100.136"
VM_PORT = 22
VM_USER = "root"
VM_PASS = "1"
LOCAL_ROOT = "D:\\microservice"
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"

# 本次修复涉及的全部文件（相对 LOCAL_ROOT）
FILES = [
    # content（BUG-001~006）
    "educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ContentAdminController.java",
    "educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ContentTeacherController.java",
    "educloud-backend/educloud-content/src/main/java/com/educloud/content/security/ContentJwtValidator.java",
    "educloud-backend/educloud-content/src/main/java/com/educloud/content/security/TeacherAccessGuard.java",
    "educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ContentAuditService.java",
    "educloud-backend/educloud-content/src/main/java/com/educloud/content/service/CoursewareAccessService.java",
    "educloud-backend/educloud-content/src/main/java/com/educloud/content/service/CourseClient.java",
    "educloud-backend/educloud-content/src/main/java/com/educloud/content/config/ContentCourseProperties.java",
    "educloud-backend/educloud-content/src/main/resources/application.yml",
    # course（BUG-051~053 + 报名查询端点）
    "educloud-backend/educloud-course/src/main/java/com/educloud/course/controller/InternalCourseController.java",
    "educloud-backend/educloud-course/src/main/java/com/educloud/course/entity/CourseEntity.java",
    "educloud-backend/educloud-course/src/main/java/com/educloud/course/messaging/OrderPaidListener.java",
    "educloud-backend/educloud-course/src/main/java/com/educloud/course/messaging/RabbitConfiguration.java",
    "educloud-backend/educloud-course/src/main/java/com/educloud/course/service/CourseAuditService.java",
    "educloud-backend/educloud-course/src/main/java/com/educloud/course/dto/response/InternalEnrollmentStatusResponse.java",
    # order（BUG-016~022）
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/OrderApplication.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/controller/OrderStudentController.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper/OutboxEventMapper.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/mapper/TradeOrderMapper.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/OrderEventPublisher.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/OutboxEventWriter.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/OutboxRelay.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/messaging/ExpiredOrderSweeper.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/service/impl/CartServiceImpl.java",
    "educloud-backend/educloud-order/src/main/java/com/educloud/order/service/impl/OrderServiceImpl.java",
    "educloud-backend/educloud-order/src/main/resources/application.yml",
    # user（BUG-034~037）
    "educloud-backend/educloud-user/src/main/java/com/educloud/user/command/ServiceClientCredentialCommand.java",
    "educloud-backend/educloud-user/src/main/java/com/educloud/user/controller/InternalServiceClientBootstrapController.java",
    "educloud-backend/educloud-user/src/main/java/com/educloud/user/service/AuthenticationService.java",
    "educloud-backend/educloud-user/src/main/java/com/educloud/user/service/ServiceTokenService.java",
    "educloud-backend/educloud-user/src/main/java/com/educloud/user/service/SessionRevocationService.java",
    "educloud-backend/educloud-user/src/main/java/com/educloud/user/service/UserStatusService.java",
    # SQL 迁移
    "deploy/sql/course/V005__course_pre_submit_lifecycle.sql",
]


def to_lf(local_path):
    """CRLF -> LF，避免 Windows 行尾影响 VM 上的 Maven 编译。"""
    with open(local_path, "rb") as f:
        data = f.read()
    data = data.replace(b"\r\n", b"\n")
    tmp = local_path + ".lf"
    with open(tmp, "wb") as f:
        f.write(data)
    return tmp


def remote_makedirs(sftp, remote_path):
    dirs = []
    head = remote_path
    while head not in ("", "/", "."):
        dirs.append(head)
        head = posixpath.dirname(head)
    for d in reversed(dirs):
        try:
            sftp.mkdir(d)
        except Exception:
            pass


def run(ssh, cmd, timeout=600):
    print(f"\n[EXEC] {cmd}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    status = stdout.channel.recv_exit_status()
    if out.strip():
        print(out.strip()[-4000:])
    if err.strip() and status != 0:
        print(f"[STDERR] {err.strip()[-3000:]}")
    print(f"[exit={status}]")
    return status, out, err


def main():
    print(f"Connecting {VM_HOST}...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(VM_HOST, port=VM_PORT, username=VM_USER, password=VM_PASS, timeout=15)
    sftp = ssh.open_sftp()

    # ---- 1. 上传文件 ----
    print("\n=== 1/4 Uploading files ===")
    for rel in FILES:
        local = os.path.join(LOCAL_ROOT, rel.replace("/", os.sep))
        if not os.path.exists(local):
            print(f"MISSING LOCAL: {rel}")
            continue
        lf = to_lf(local)
        remote = f"{REMOTE_ROOT}/{rel}"
        remote_makedirs(sftp, posixpath.dirname(remote))
        sftp.put(lf, remote)
        os.remove(lf)
        print(f"  uploaded {rel}")
    print(f"Uploaded {len(FILES)} files.")

    # ---- 2. V005 迁移（幂等） ----
    print("\n=== 2/4 V005 migration (idempotent) ===")
    check = (
        "mysql -uroot -p1 -h127.0.0.1 educloud_course -N -e "
        "\"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='educloud_course' "
        "AND table_name='course' AND column_name='pre_submit_lifecycle_status';\""
    )
    st, out, _ = run(ssh, check)
    if st == 0 and out.strip().startswith("0"):
        run(ssh, f"mysql -uroot -p1 -h127.0.0.1 educloud_course < {REMOTE_ROOT}/deploy/sql/course/V005__course_pre_submit_lifecycle.sql")
        print("V005 applied.")
    else:
        print(f"pre_submit_lifecycle_status already exists (count={out.strip()}), skip migration.")

    # ---- 3. 编译 ----
    print("\n=== 3/4 Maven build (user, course, content, order) ===")
    st, out, err = run(
        ssh,
        f"cd {REMOTE_ROOT}/educloud-backend && "
        "mvn -pl educloud-user,educloud-course,educloud-content,educloud-order -am "
        "clean package -DskipTests=true -q 2>&1 | tail -50; echo MVN_EXIT=${PIPESTATUS[0]}",
        timeout=1500,
    )
    if "MVN_EXIT=0" not in out:
        print("!!! BUILD FAILED, aborting restart !!!")
        sftp.close()
        ssh.close()
        return

    # ---- 4. 重启受影响服务 ----
    print("\n=== 4/4 Restart services ===")
    run(ssh, "pkill -f 'educloud-user-1.0.0-SNAPSHOT.jar' || true")
    run(ssh, "pkill -f 'educloud-course-1.0.0-SNAPSHOT.jar' || true")
    run(ssh, "pkill -f 'educloud-content-1.0.0-SNAPSHOT.jar' || true")
    run(ssh, "pkill -f 'educloud-order-1.0.0-SNAPSHOT.jar' || true")
    time.sleep(5)
    run(ssh, f"cd {REMOTE_ROOT} && bash deploy/scripts/start-dev.sh", timeout=900)

    # ---- 5. 健康检查轮询 ----
    print("\n=== Health polling (max 180s) ===")
    checks = [
        ("user", "http://127.0.0.1:8083/actuator/health/readiness"),
        ("gateway", "http://127.0.0.1:8081/actuator/health/readiness"),
        ("course", "http://127.0.0.1:8090/actuator/health/readiness"),
        ("file", "http://127.0.0.1:8088/actuator/health/readiness"),
        ("content", "http://127.0.0.1:8086/actuator/health/readiness"),
        ("order", "http://127.0.0.1:8092/actuator/health/readiness"),
    ]
    pending = dict(checks)
    for _ in range(18):
        time.sleep(10)
        for name in list(pending):
            st, out, _ = run(ssh, f"curl -s -m 3 {pending[name]}", timeout=30)
            if '"status":"UP"' in out:
                print(f"  [UP] {name}")
                del pending[name]
        if not pending:
            break
    if pending:
        print(f"!!! NOT READY: {list(pending)}")
        for name, url in pending.items():
            run(ssh, f"tail -30 /tmp/educloud-live/{name}.log")
    else:
        print("ALL SERVICES UP.")

    sftp.close()
    ssh.close()


if __name__ == "__main__":
    main()
