# Better PlanetEarth 상태 서버

`http://better-planetearth.ggm.kr/<닉네임>` 페이지를 서빙하고, 모드 클라이언트와 웹소켓으로 연결해
실시간 온라인/대기열 상태를 받고 원격 "접속 종료" 명령을 내려보내는 백엔드입니다.

## 동작 방식

- 비밀번호는 별도 가입 절차 없이 **처음 인증에 성공한 값이 그대로 등록**됩니다
  (모드에서 비밀번호를 설정하고 처음 접속하면 그 값이 그 닉네임의 비밀번호로 저장됨).
- 상태는 전부 메모리에 있고 재시작하면 초기화됩니다(모드가 재연결하면서 바로 채워짐).
- 웹 페이지는 SSE(`/:nickname/events`)로 실시간 갱신되고, 로그인은 닉네임 경로에 스코프된
  쿠키(`bpe_sess_<nickname>`)로 유지됩니다.

## 프로토콜 (모드 <-> 서버, `wss://.../ws`)

모드 -> 서버:
```json
{"type":"auth","nickname":"Steve","password":"..."}
{"type":"status","state":"OFFLINE|CONNECTING|QUEUE|ONLINE","pos":5,"total":14}
{"type":"chat","text":"..."}
```

서버 -> 모드:
```json
{"type":"auth_ok"}
{"type":"auth_fail"}
{"type":"disconnect_request"}
{"type":"run_command","command":"..."}
```

웹 페이지의 명령어 입력창에 친 내용은 그대로 게임 채팅으로 전송됩니다(`/`로 시작하면
명령어, 아니면 일반 채팅) — 즉 그 계정으로 뭐든 칠 수 있다는 뜻이니 비밀번호 유출에 주의.

## 로컬 실행

```bash
cd server
npm install
PORT=8787 npm start
```

## 배포 (systemd + Caddy)

`/etc/systemd/system/better-pe-status.service`:
```ini
[Unit]
Description=Better PlanetEarth status server
After=network.target

[Service]
WorkingDirectory=/opt/better-pe-status
ExecStart=/usr/bin/node src/index.js
Environment=PORT=8787
Restart=always
User=ubuntu

[Install]
WantedBy=multi-user.target
```

Caddyfile에 추가:
```
better-planetearth.ggm.kr {
	reverse_proxy 127.0.0.1:8787
}
```

`sudo systemctl daemon-reload && sudo systemctl enable --now better-pe-status`
`sudo systemctl reload caddy`
