"""修复 content→course 服务令牌链路：
1. user 库：educloud-content 客户端 audiences/scopes 扩展
2. 上传新 start-dev.sh（content 启动段自动注入 COURSE_CLIENT_SECRET）
3. 重启 content 并验证 readiness + secret 注入
"""
import os
import posixpath
import re
import time
import paramiko

PW = "39c3df909277146fa5a381c6cb98752c5570a23724ec14a8"
REMOTE_ROOT = "/root/educloud/.worktrees/educloud-backend-foundation"
LOCAL_SH = r"D:\microservice\deploy\scripts\start-dev.sh"


def run(ssh, cmd, timeout=300):
    print(f"\n[EXEC] {cmd[:220]}")
    _, o, e = ssh.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", errors="replace")
    err = e.read().decode("utf-8", errors="replace")
    st = o.channel.recv_exit_status()
    if out.strip():
        print(out.strip()[-2500:])
    if err.strip() and st != 0:
        print("[STDERR]", err.strip()[-1000:])
    print(f"[exit={st}]")
    return st, out


def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect("192.168.100.136", 22, "root", "1", timeout=15)

    # 1. 扩展 audiences/scopes（幂等 UPDATE）
    run(ssh, "mysql -uroot -p%s -h127.0.0.1 educloud_user -e \""
             "UPDATE service_client SET allowed_audiences_json='[\\\"educloud-file\\\",\\\"educloud-course\\\"]', "
             "allowed_scopes_json='[\\\"file:internal\\\",\\\"course:internal\\\"]' "
             "WHERE client_id='educloud-content'; "
             "SELECT client_id, allowed_audiences_json, allowed_scopes_json FROM service_client WHERE client_id='educloud-content';\"" % PW)

    # 2. 上传新 start-dev.sh（LF 行尾）：content 启动段自动注入
    #    EDUCLOUD_CONTENT_COURSE_CLIENT_SECRET（回退 content 的 file secret）
    sftp = ssh.open_sftp()
    with open(LOCAL_SH, "rb") as f:
        data = f.read().replace(b"\r\n", b"\n")
    tmp = LOCAL_SH + ".lf"
    with open(tmp, "wb") as f:
        f.write(data)
    sftp.put(tmp, f"{REMOTE_ROOT}/deploy/scripts/start-dev.sh")
    os.remove(tmp)
    sftp.close()
    print("start-dev.sh uploaded.")

    # 3. 重启 content（start-dev.sh 对已运行服务幂等跳过）
    run(ssh, "pkill -f 'educloud-content-1.0.0-SNAPSHOT.jar' || true")
    time.sleep(3)
    run(ssh, f"cd {REMOTE_ROOT} && nohup bash deploy/scripts/start-dev.sh "
             f"> /tmp/educloud-live/start-dev-run3.log 2>&1 & echo STARTED")

    for _ in range(18):
        time.sleep(10)
        st, out = run(ssh, "curl -s -m 3 http://127.0.0.1:8086/actuator/health/readiness", timeout=30)
        if '"status":"UP"' in out:
            print("\nCONTENT UP.")
            break
    else:
        run(ssh, "tail -40 /tmp/educloud-live/content.log")
        ssh.close()
        return

    # 4. 验证 content 进程确实拿到了 secret
    run(ssh, "cat /proc/$(pgrep -f educloud-content-1.0.0 | head -1)/environ | tr '\\0' '\\n' | grep COURSE_CLIENT_SECRET | sed 's/=.*/=<set>/'")
    ssh.close()


if __name__ == "__main__":
    main()
