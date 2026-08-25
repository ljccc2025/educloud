import requests

GATEWAY_URL = "http://192.168.100.136:8080"

resp = requests.post(f"{GATEWAY_URL}/api/v1/auth/login", json={
    "loginName": "fe_demo_10",
    "password": "FeDemo@2026",
    "portal": "STUDENT"
})
token = resp.json()["data"]["accessToken"]

r = requests.get(f"{GATEWAY_URL}/api/v1/me/enrollments", headers={"Authorization": f"Bearer {token}"})
print("Enrollments:", r.json())
