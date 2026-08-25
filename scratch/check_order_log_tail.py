import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
cmds = [
    "wc -l /tmp/educloud-live/order.log",
    "tail -30 /tmp/educloud-live/order.log",
    # 网关侧看 /api/v1/orders 请求被路由到哪个服务
    "grep -E 'orders' /tmp/educloud-live/gateway.log | tail -8",
]
for c in cmds:
    print('===', c[:70])
    _, o, _ = ssh.exec_command(c, timeout=60)
    print(o.read().decode('utf-8', errors='replace')[-2500:])
ssh.close()
