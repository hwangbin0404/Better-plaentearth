# Better PlanetEarth

플레닛어스(PlanetEarth) 서버 플레이를 편하게 해주는 Fabric 클라이언트 모드입니다. Lunar Client처럼 화면에 드래그로 배치하는 HUD와, 서버 플레이에 필요한 여러 자동화/알림 기능을 모아뒀습니다.

- **버전**: Minecraft 1.20.1 (Fabric)
- **라이선스**: CC0-1.0

## 기능

- **드래그형 HUD 편집** — `Right Shift`로 편집 화면을 열어 모든 HUD 요소를 마우스로 옮기고 크기 조절
- **전쟁 알리미** — 전쟁 선포/시작을 감지해 시작까지 남은 시간과 깃발 점령 현황을 보스바로 표시
- **국가/마을 태그** — 조준한(또는 주변) 플레이어 머리 위에 소속 국가/마을 표시
- **위치 표시(HUD)** — 야생에서 근처 마을까지 거리 표시
- **특산품 추천** — `/goods` 결과를 파싱해 다음 정기선 시간 안에 얻을 수 있는 특산품 추천, 획득/리스폰 알림
- **특산품 방향 비콘** — `/특품선택`으로 추적 중인 특산품 쪽으로 화면에 방향/거리 표시(예: `↗ 목재(바이칼호) 342m`), 10블록 이내로 도착하면 자동으로 사라짐
- **아군 채팅 강조** — 국가원/동맹 채팅을 색으로 구분
- **인원파악** — `Page Down`을 누르고 있으면 주변 플레이어를 국가별로 집계해서 채팅에 보고
- **양조 타이머** — `/양조선택`으로 레시피를 고른 뒤 가마솥에 시계를 우클릭하면 발효 완료까지 카운트다운
- **보트 자동이동** — 보트에 타고 있는 동안 전진(W)키를 자동으로 눌러줌. 서버 규정(매크로 금지, 2조 2항)이 명시적으로 허용하는 "보트에서 W키를 누른 상태로 이동"의 범위 안에서만 동작하며, 방향 조작이나 경로탐색은 하지 않습니다.
- **보스바 스택** — 위 기능들의 보스바가 동시에 여러 개 필요하면(전쟁+양조+특산품+보트 등) 겹치지 않고 한 줄씩 쌓여서 표시
- **자동 업데이트 확인** — 게임 접속 시 GitHub Releases의 최신 버전을 확인하고, 새 버전이 있으면 채팅 알림의 버튼을 눌러 자동으로 받아둘 수 있음 (적용은 다음 재시작부터)
- **원격 상태 페이지** — `http://better-planetearth.ggm.kr/<닉네임>`에서 오프라인/대기열(순번 실시간 표시)/온라인 상태를 확인하고 원격으로 접속을 종료할 수 있음. 비밀번호로 잠겨 있으며, Mod Menu에서 켜고 비밀번호를 정하면 그 값이 그대로 등록됨 (자세한 동작은 `server/README.md` 참고)

## 설치

1. [Fabric Loader](https://fabricmc.net/use/)와 아래 의존 모드를 설치
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Cloth Config](https://modrinth.com/mod/cloth-config) (설정 화면용)
   - [Mod Menu](https://modrinth.com/mod/modmenu) (설정 화면 진입점, 선택)
2. [Releases](https://github.com/hwangbin0404/Better-plaentearth/releases/latest)에서 최신 jar 다운로드
3. `.minecraft/mods` 폴더에 넣고 실행

## 키 바인드

기본값이며 조작 설정(Controls)의 "Better PlanetEarth" 항목에서 변경 가능합니다.

| 동작 | 기본 키 |
| --- | --- |
| HUD 편집 화면 열기 | `Right Shift` |
| 인원파악 (누르고 있기) | `Page Down` |
| 보트 자동이동 토글 | `B` |

## 명령어

| 명령어 | 설명 |
| --- | --- |
| `/양조선택 <이름>` | 양조 레시피를 선택 (다음 가마솥+시계 우클릭에 타이머 시작) |
| `/양조선택취소` | 양조 타이머 해제 |
| `/특품추천` | 다음 정기선 시간 안에 얻을 수 있는 특산품 추천 |
| `/특품선택 <이름>` | 추천 목록의 특산품을 보스바로 추적 |
| `/특품선택취소` | 특산품 추적 해제 |
| `/업데이트` | 대기 중인 새 버전을 지금 받기 (채팅 알림의 버튼과 동일) |

세부 설정(반경, 색상, 전쟁 시간 등)은 Mod Menu → Better PlanetEarth 또는 HUD 편집 화면의 "⚙ 설정" 버튼에서 조절합니다.

## 빌드

```bash
./gradlew build
```

`build/libs/betterplanetearth-<version>.jar`가 생성됩니다.

## 새 버전 배포하기

1. `gradle.properties`의 `mod_version` 갱신
2. `./gradlew build`
3. `gh release create v<version> build/libs/betterplanetearth-<version>.jar --title "v<version>" --notes "..."`

접속 중인 클라이언트가 새 릴리즈를 감지해 채팅으로 알려주고, 버튼을 누르면 자동으로 받아둡니다.
