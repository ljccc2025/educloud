"""
部署阶段 2：上传修复的测试文件 → V005 迁移 → VM 编译 → 重启服务 → 健康检查
"""
import os
import posixpath
import re
import time
import paramiko

VM_HOST = "192.168.100.136"
VM_USER = "root"
VM_PASS = "1"
LOCAL_ROOT = "D:\\microservice"
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"

TEST_FILES = [
    "educloud-backend/educloud-order/src/test/java/com/educloud/order/controller/OrderStudentControllerTest.java",
    "educloud-backend/educloud-order/src/test/java/com/educloud/order/service/CartServiceTest.java",
    "educloud-backend/educloud-order/src/test/java/com/educloud/order/service/OrderMockPayTest.java",
    "educloud-backend/educloud-order/src/test/java/com/educloud/order/service/OrderServiceTest.java",
    "educloud-backend/educloud-course/src/test/java/com/educloud/course/controller/InternalCourseControllerTest.java",
    "educloud-backend/educloud-course/src/test/java/com/educloud/course/service/CourseAuditServiceTest.java",
    "educloud-backend/educloud-content/src/test/java/com/educloud/content/service/CoursewareAccessServiceTest.java",
    "educloud-backend/educloud-content/src/test/java/com/educloud/content/service/ContentAuditServiceTest.java",
    "educloud-backend/educloud-content/src/test/java/com/educloud/content/controller/ContentControllerTest.java",
]


def to_lf(local_path):
    with open(local_path, "rb") as f:
        data = f.read()
    tmp = local_path + ".lf"
    with open(tmp, "wb") as f:
        f.write(data.replace(b"\r\n", b"\n"))
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
    print(f"\n[EXEC] {cmd[:300]}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    status = stdout.channel.recv_exit_status()
    if out.strip():
        print(out.strip()[-4000:])
    if err.strip() and status != 0:
        print(f"[STDERR] {err.strip()[-2000:]}")
    print(f"[exit={status}]")
    return status, out, err


def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(VM_HOST, port=22, username=VM_USER, password=VM_PASS, timeout=15)
    sftp = ssh.open_sftp()

    # ---- 1. 上传测试文件 ----
    print("=== 1/5 Upload test files ===")
    for rel in TEST_FILES:
        local = os.path.join(LOCAL_ROOT, rel.replace("/", os.sep))
        lf = to_lf(local)
        remote = f"{REMOTE_ROOT}/{rel}"
        remote_makedirs(sftp, posixpath.dirname(remote))
        sftp.put(lf, remote)
        os.remove(lf)
        print(f"  uploaded {rel.split('/')[-1]}")

    # ---- 2. V005 迁移（root 密码取自 .env） ----
    print("\n=== 2/5 V005 migration ===")
    st, env_out, _ = run(ssh, f"cat {REMOTE_ROOT}/deploy/docker-compose/.env")
    m = re.search(r"^MYSQL_ROOT_PASSWORD=(\S+)", env_out, re.M)
    root_pw = m.group(1) if m else "1"
    print(f"MySQL root password resolved from .env: {'yes' if m else 'fallback'}")
    check = (
        f"mysql -uroot -p'{root_pw}' -h127.0.0.1 educloud_course -N -e "
        "\"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='educloud_course' "
        "AND table_name='course' AND column_name='pre_submit_lifecycle_status';\""
    )
    st, out, _ = run(ssh, check)
    if st == 0 and out.strip().startswith("0"):
        run(ssh, f"mysql -uroot -p'{root_pw}' -h127.0.0.1 educloud_course < {REMOTE_ROOT}/deploy/sql/course/V005__course_pre_submit_lifecycle.sql")
        print("V005 applied.")
    else:
        print(f"column exists or check failed (out={out.strip()}), skip.")

    # ---- 3. 编译（含测试编译，跳过测试执行） ----
    print("\n=== 3/5 Maven package ===")
    st, out, err = run(
        ssh,
        f"cd {REMOTE_ROOT}/educloud-backend && mvn clean package -DskipTests=true 2>&1 | tail -30; "
        "echo MVN_EXIT=${PIPESTATUS[0]}",
        timeout=1800,
    )
    if "MVN_EXIT=0" not in out:
        print("!!! BUILD FAILED, aborting !!!")
        sftp.close(); ssh.close()
        return
    print("BUILD OK.")

    # ---- 4. 重启受影响服务 ----
    print("\n=== 4/5 Restart services ===")
    for jar in ["educloud-user", "educloud-course", "educloud-content", "educloud-order"]:
        run(ssh, f"pkill -f '{jar}-1.0.0-SNAPSHOT.jar' || true")
    time.sleep(6)
    run(ssh, f"cd {REMOTE_ROOT} && bash deploy/scripts/start-dev.sh", timeout=900)

    # ---- 5. 健康检查 ----
    print("\n=== 5/5 Health polling ===")
    checks = [
        ("user", "http://127.0.0.1:8083/actuator/health/readiness"),
        ("gateway", "http://127.0.0.1:8081/actuator/health/readiness"),
        ("course", "http://127.0.0.1:8090/actuator/health/readiness"),
        ("file", "http://127.0.0.1:8088/actuator/health/readiness"),
        ("content", "http://127.0.0.1:8086/actuator/health/readiness"),
        ("order", "http://127.0.0.1:8092/actuator/health/readiness"),
    ]
    pending = dict(checks)
    for _ in range(24):
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
        for name in pending:
            run(ssh, f"tail -40 /tmp/educloud-live/{name}.log")
    else:
        print("ALL SERVICES UP.")

    sftp.close()
    ssh.close()


if __name__ == "__main__":
    main()
