import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
cmds = [
    # course 日志里异常的头部（异常类名+消息）
    "grep -nE 'Exception|ERROR|Access|Denied|401|403' /tmp/educloud-live/course.log | tail -20",
    # 最近一次完整异常（取 ERROR 块）
    "grep -B3 -A8 -E '(Exception|ERROR)' /tmp/educloud-live/course.log | tail -60",
]
for c in cmds:
    print('===', c[:70])
    _, o, _ = ssh.exec_command(c, timeout=60)
    print(o.read().decode('utf-8', errors='replace')[-4000:])
ssh.close()
