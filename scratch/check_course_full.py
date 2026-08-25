import paramiko, json
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.100.136', 22, 'root', '1', timeout=30, banner_timeout=30)
_, o, _ = ssh.exec_command("curl -s -m 8 http://127.0.0.1:8089/api/v1/courses/9000000000000000115", timeout=30)
raw = o.read().decode('utf-8', errors='replace')
print("=== RAW (first 2000) ===")
print(raw[:2000])
try:
    data = json.loads(raw)
    d = data.get('data') or {}
    print("=== KEY FIELDS ===")
    for k in ['status', 'lifecycleStatus', 'onSale', 'isOnSale', 'price', 'originalPrice', 'publishStatus']:
        if k in d:
            print(f"{k} = {d[k]!r}")
    print("=== ALL KEYS ===")
    print(list(d.keys()))
except Exception as e:
    print("parse error:", e)
ssh.close()
