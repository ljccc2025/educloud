import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)

cmds = [
    # 各服务进程启动时间
    "ps -eo pid,lstart,args | grep 'educloud-.*1.0.0' | grep -v grep",
    # start-dev 运行日志
    "tail -40 /tmp/educloud-live/start-dev-m08.log",
    # payment 日志（可能仍在启动）
    "tail -20 /tmp/educloud-live/payment.log",
    # 直接探测 4 个未就绪端口
    "for p in 8083 8081 8090 8094; do printf \"port $p: \"; curl -s -o /dev/null -w '%{http_code}' -m 4 http://127.0.0.1:$p/actuator/health; echo; done",
]
for c in cmds:
    print('===', c[:90])
    _, o, e = ssh.exec_command(c, timeout=60)
    print(o.read().decode('utf-8', errors='replace')[-3500:])
    err = e.read().decode('utf-8', errors='replace').strip()
    if err:
        print('[err]', err[:300])
ssh.close()
