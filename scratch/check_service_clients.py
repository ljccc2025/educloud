import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=15)
cmds = [
    # user 库中已注册的服务客户端及其 audiences
    "mysql -uroot -p39c3df909277146fa5a381c6cb98752c5570a23724ec14a8 -h127.0.0.1 -t -e \"SELECT client_id, status, allowed_audiences_json, allowed_scopes_json FROM educloud_user.service_client;\"",
    # content 服务的 course 配置（启动环境变量）
    "ps -ef | grep educloud-content | grep -v grep | head -1 | tr ' ' '\\n' | grep -i course || true",
    "cat /proc/$(pgrep -f educloud-content-1.0.0 | head -1)/environ | tr '\\0' '\\n' | grep -i COURSE || true",
]
for c in cmds:
    print('===', c[:80])
    _, o, e = ssh.exec_command(c)
    print(o.read().decode('utf-8', errors='replace'))
    err = e.read().decode('utf-8', errors='replace')
    if err.strip():
        print('[err]', err[:300])
ssh.close()
