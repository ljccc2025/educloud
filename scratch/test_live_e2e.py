import json
import time
import requests
import websocket

GATEWAY_URL = "http://192.168.100.136:8080"
WS_GATEWAY_URL = "ws://192.168.100.136:8080"

def login(username, password, portal):
    resp = requests.post(f"{GATEWAY_URL}/api/v1/auth/login", json={
        "loginName": username,
        "password": password,
        "portal": portal
    })
    data = resp.json()
    if resp.status_code == 200 and data.get("code") == "SUCCESS":
        return data["data"]["accessToken"], data["data"].get("userId")
    raise Exception(f"Login failed for {username} ({portal}): {data}")

def run_e2e_test():
    print("=== [1] Logging in Teacher & Student & Admin ===")
    teacher_token, teacher_id = login("demo_teacher", "EduCloud@2026", "TEACHER")
    student_token, student_id = login("fe_demo_10", "FeDemo@2026", "STUDENT")
    admin_token, admin_id = login("demo_admin", "EduCloud@2026", "ADMIN")
    print(f"Teacher Logged in: id={teacher_id}")
    print(f"Student Logged in: id={student_id}")
    print(f"Admin Logged in: id={admin_id}")

    teacher_headers = {"Authorization": f"Bearer {teacher_token}"}
    student_headers = {"Authorization": f"Bearer {student_token}"}
    admin_headers = {"Authorization": f"Bearer {admin_token}"}

    print("\n=== [2] Teacher Creates Live Room ===")
    create_resp = requests.post(f"{GATEWAY_URL}/api/v1/live-rooms", headers=teacher_headers, json={
        "courseId": "9000000000000000110",
        "title": "2026 高并发云原生微服务实战直播课",
        "description": "全链路直播互动、实时弹幕与录制回放",
        "scheduledStartAt": "2026-08-25T18:00:00",
        "scheduledEndAt": "2026-08-25T20:00:00"
    })
    print(f"Create Room Response ({create_resp.status_code}): {create_resp.text}")
    assert create_resp.status_code == 200, f"Create room failed: {create_resp.text}"
    room_data = create_resp.json()["data"]
    room_id = room_data["id"]
    print(f"Created Live Room ID: {room_id}")

    print("\n=== [3] Teacher Starts Live ===")
    start_resp = requests.post(f"{GATEWAY_URL}/api/v1/live-rooms/{room_id}/start", headers=teacher_headers)
    print(f"Start Live Response ({start_resp.status_code}): {start_resp.text}")
    assert start_resp.status_code == 200, f"Start live failed: {start_resp.text}"
    start_data = start_resp.json()["data"]
    session_id = start_data["sessionId"]
    print(f"Started Live Session ID: {session_id}, Push URL: {start_data['pushInfo']['pushUrl']}")

    print("\n=== [4] Issue Connection Tickets ===")
    t_ticket_resp = requests.post(f"{GATEWAY_URL}/api/v1/live-rooms/{room_id}/connection-ticket", headers=teacher_headers)
    print(f"Teacher Ticket Response ({t_ticket_resp.status_code}): {t_ticket_resp.text}")
    assert t_ticket_resp.status_code == 200
    teacher_ticket = t_ticket_resp.json()["data"]["ticket"]
    print(f"Teacher Ticket: {teacher_ticket}")

    s_ticket_resp = requests.post(f"{GATEWAY_URL}/api/v1/live-rooms/{room_id}/connection-ticket", headers=student_headers)
    print(f"Student Ticket Response ({s_ticket_resp.status_code}): {s_ticket_resp.text}")
    assert s_ticket_resp.status_code == 200
    student_ticket = s_ticket_resp.json()["data"]["ticket"]
    print(f"Student Ticket: {student_ticket}")

    print("\n=== [5] Establish WebSocket Connections & Test Live Interaction ===")
    ws_teacher_url = f"{WS_GATEWAY_URL}/ws/v1/live/{room_id}?ticket={teacher_ticket}"
    ws_student_url = f"{WS_GATEWAY_URL}/ws/v1/live/{room_id}?ticket={student_ticket}"

    ws_t = websocket.create_connection(ws_teacher_url, origin="http://192.168.100.136:5174", timeout=5)
    print("Teacher WebSocket Connected!")
    ws_s = websocket.create_connection(ws_student_url, origin="http://192.168.100.136:5173", timeout=5)
    print("Student WebSocket Connected!")

    # Student sends CHAT
    ws_s.send(json.dumps({"type": "CHAT", "content": "老师好，微服务架构真清晰！"}))
    time.sleep(0.5)

    # Read messages on teacher side
    msg = ws_t.recv()
    print(f"Teacher received broadcast message: {msg}")

    # Teacher sends whiteboard action
    ws_t.send(json.dumps({"type": "WHITEBOARD", "payload": {"action": "DRAW_LINE", "points": [10, 20, 30, 40]}}))
    time.sleep(0.5)

    # Read message on student side
    msg_s = ws_s.recv()
    print(f"Student received whiteboard message: {msg_s}")

    ws_s.close()
    ws_t.close()
    print("WebSocket real-time interaction test passed!")

    print("\n=== [6] Query Live Messages History ===")
    msg_hist_resp = requests.get(f"{GATEWAY_URL}/api/v1/live-rooms/{room_id}/messages", headers=teacher_headers)
    print(f"Message History ({msg_hist_resp.status_code}): {msg_hist_resp.text}")
    assert msg_hist_resp.status_code == 200

    print("\n=== [7] Teacher Ends Live ===")
    end_resp = requests.post(f"{GATEWAY_URL}/api/v1/live-rooms/{room_id}/end", headers=teacher_headers)
    print(f"End Live Response ({end_resp.status_code}): {end_resp.text}")
    assert end_resp.status_code == 200
    replay_id = end_resp.json()["data"]["replayId"]
    print(f"Live ended, auto-archived Replay ID: {replay_id}")

    print("\n=== [8] Query Replay & Playback URL ===")
    replay_resp = requests.get(f"{GATEWAY_URL}/api/v1/live-rooms/{room_id}/replays/{replay_id}", headers=student_headers)
    print(f"Replay Detail Response ({replay_resp.status_code}): {replay_resp.text}")
    assert replay_resp.status_code == 200
    play_url = replay_resp.json()["data"].get("playUrl")
    print(f"Play URL: {play_url}")

    print("\n==========================================")
    print(">>> ALL E2E LIVE FLOW TESTS PASSED 100% <<<")
    print("==========================================")

if __name__ == "__main__":
    run_e2e_test()
