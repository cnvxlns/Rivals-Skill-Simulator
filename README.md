# Rivals-Skill-Simulator

MLB 라이벌(MLB Rivals) 모바일 게임의 스킬 변경 시스템을 웹에서 실험할 수 있는 토이 프로젝트입니다. 카드 타입(Prime/Moment/Signature), 변경권 종류(일반/고급/최고급), 포지션 필터를 조합해 실제 게임 규칙에 가까운 확률 롤을 돌려 볼 수 있고, 스킬 조합의 점수도 계산할 수 있습니다.

## 핵심 기능
- 스킬 변경 시뮬레이션: 변경권별 확률 테이블(Weighted Random) 적용, 최고급 변경권 사용 시 1번 슬롯 골드 티어 보장, 슬롯 간 스킬 중복 방지.
- 카드 타입별 잠금 규칙: Prime은 1번 슬롯 잠금 가능, Moment는 1번 슬롯이 Moment 티어일 때만 잠금 가능, Signature는 잠금 불가. 백엔드에서 검증하고 프론트에서도 제어합니다.
- 스킬 레벨 보호: 슬롯별 `useLevelProtectionSlots` 플래그로 등급 하락을 방지하며, 기존 등급보다 낮아지지 않도록 처리합니다.
- 포지션 필터: Pitcher/Batter 전용 스킬 풀을 분리하며, 요청에 포지션 누락 시 400 오류를 반환합니다.
- 스킬 점수 계산기: 카드 타입과 포지션을 기준으로 스킬 3개(시그니처 블랙은 4개)와 레벨을 선택하면 총점, 스킬별 기여도, 스탯별 내역을 계산합니다.
- 정적 데이터 시드: `score_skills.csv`, `score_effects.csv`, `stat_weights.csv`를 애플리케이션 시작 시 읽어 메모리에 적재합니다.

## 기술 스택
**App (Web / Android)**  
![Expo](https://img.shields.io/badge/Expo-000020?style=flat&logo=expo&logoColor=white)
![React Native](https://img.shields.io/badge/React_Native-61DAFB?style=flat&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat&logo=typescript&logoColor=white)

**Backend**  
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat&logo=springboot&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat&logo=gradle&logoColor=white)

- App: Expo SDK 57, React Native, expo-router, TypeScript, axios. 웹과 안드로이드를 한 코드베이스로 빌드합니다.
- Backend: Spring Boot 3.2.4, Kotlin 1.9.22(JVM 17), Gradle(Wrapper), OpenCSV, JDBC + Flyway, JJWT.
- 데이터: 스킬은 클래스패스 CSV를 부팅 시 읽어 메모리에 적재합니다(`InMemoryScoreSkillRepository`). 계정과 덱만 PostgreSQL에 저장합니다.
- 인증: 이메일 + 비밀번호(BCrypt), JWT 베어러 토큰. `spring-boot-starter-security` 없이 `HandlerInterceptor`로 처리합니다.

## 폴더 구조
```
Rivals-Skill-Simulator/
├── backend    # Spring Boot API 서버 (포트 8080, CSV 시드)
├── app        # Expo(React Native) 앱 — 웹/안드로이드 공용 UI, axios로 /api/score 호출
├── docs       # 데이터 원천(rivals_skills.xlsx)과 변환기(convert_xlsx.py)
├── .github    # EAS 빌드/OTA 배포 워크플로
├── docker-compose.yml         # 백엔드 실행 스택 (+ .dev / .tunnel 오버라이드)
├── Makefile   # docker compose / gradlew / npm 을 감싼 단축 명령 (make up)
└── README.md  # 본 문서
```

## 브랜치 전략

| 브랜치 | 역할 |
|---|---|
| `deploy` | 배포 브랜치. Vercel(웹)이 이 브랜치를 바라보며, push 시 EAS OTA 업데이트도 나갑니다. 백엔드는 개인 서버에서 compose로 직접 띄웁니다. |
| `develop` | 기본 브랜치. 일상 개발은 여기서 분기하고 여기로 머지합니다. |
| `feat/*`, `fix/*`, `chore/*` | `develop`에서 분기해 작업 후 `develop`으로 PR. |

릴리즈는 `develop` → `deploy` PR로 올립니다. `deploy`에 직접 커밋하지 않습니다.

## 실행 방법
### 1) Backend (Spring Boot)
1. 필수: JDK 17 (Gradle Wrapper 포함)
2. 실행:
   ```bash
   cd backend
   ./gradlew bootRun
   ```
   - Windows PowerShell/명령프롬프트에서는 `gradlew.bat bootRun`
3. 기본 포트는 `http://localhost:8080`입니다. 부팅 시 `score_skills.csv`, `score_effects.csv`, `stat_weights.csv`를 읽어 메모리에 적재합니다.

### 2) App (Expo)
1. 필수: Node.js 20+, npm
2. 실행:
   ```bash
   cd app
   npm install
   npm run web      # 안드로이드는 npm run android
   ```
3. 기본 포트는 `http://localhost:8081`이며, 백엔드가 8080에서 떠 있어야 API 요청이 성공합니다. 백엔드 주소는 `EXPO_PUBLIC_API_URL`로 지정합니다.
4. 웹 프로덕션 빌드:
   ```bash
   npx expo export -p web   # 결과물은 dist/
   ```

### 3) Docker로 전체 스택 한 번에 (권장)

Node도 JDK도 설치할 필요 없이 DB·백엔드·프론트가 같이 뜹니다.

```bash
make up                  # 또는 docker compose up --build
```

`http://localhost:8081` 하나로 끝입니다. `docker-compose.override.yml`이 자동으로 얹혀 포트를 열어 줍니다.

구조는 nginx가 앱의 정적 빌드를 서빙하면서 `/api`만 백엔드로 프록시하는 형태입니다. 같은 오리진이라 CORS 설정이 필요 없고, 앱 번들에 백엔드 주소를 박아 넣지도 않습니다(`app/src/lib/api.ts`가 환경변수가 없으면 상대경로 `/api`로 떨어집니다).

```
브라우저 ──> localhost:8081  app (nginx)
                              ├── /        정적 파일 (expo export -p web 산출물)
                              └── /api/*   proxy_pass ──> backend:8080 (Spring)
                                                             └── db:5432 (Postgres)
```

**첫 빌드는 오래 걸립니다.** 컨테이너 안에서 Gradle 의존성과 npm 패키지를 처음부터 받고, 백엔드는 이미지 빌드 중에 전체 테스트까지 돌립니다. 두 Dockerfile 모두 BuildKit 캐시 마운트를 쓰므로 두 번째부터는 훨씬 빠릅니다.

개인 서버에 Cloudflare 터널로 노출할 때는 override를 빼고 터널 오버레이를 겹칩니다. 이때는 호스트에 포트를 열지 않습니다.

```bash
cp .env.example .env        # TUNNEL_TOKEN 채우기
make tunnel-up              # 또는 docker compose -f docker-compose.yml -f docker-compose.tunnel.yml up -d --build
```

| 파일 | 역할 |
|---|---|
| `docker-compose.yml` | db + backend + app 기본 스택. 포트를 호스트에 열지 않습니다 |
| `docker-compose.override.yml` | 로컬 개발용. compose가 자동으로 얹어 8081(앱)·8080(API)을 엽니다 |
| `docker-compose.tunnel.yml` | 개인 서버용 cloudflared. app을 터널로 노출합니다 |

스킬 데이터는 클래스패스 CSV가 원천이지만, **계정과 덱은 `pgdata` 볼륨에 남습니다.** 이 프로젝트에서 잃으면 복구할 수 없는 유일한 데이터입니다.

> ⚠️ `make clean`은 볼륨을 건드리지 않습니다. 볼륨까지 지우려면 `make nuke`(확인 절차 있음)를 쓰고, 백업은 `make db-dump`으로 받습니다.

### 4) Makefile 단축 명령

위 명령들을 짧게 부르는 래퍼입니다. 새로운 실행 경로는 아니고, `docker compose`·`gradlew`·`npm`을 그대로 감싸기만 합니다. 인자 없이 `make`만 치면 목록이 나옵니다.

| 명령 | 하는 일 |
|---|---|
| `make up` | 전체 스택 기동, 포그라운드. `http://localhost:8081` |
| `make up-d` | 같은 스택을 백그라운드로 |
| `make down` | 컨테이너 정지 및 제거 |
| `make restart` | `down` 후 `up-d` |
| `make logs` | 로그 따라가기 |
| `make ps` | 컨테이너 상태 |
| `make build` / `make rebuild` | 이미지 빌드 / 캐시 없이 빌드 |
| `make clean` | `down` + 로컬 이미지 제거. **DB 볼륨은 유지** |
| `make nuke` | `clean` + DB 볼륨 삭제. 계정과 덱이 사라집니다(확인 입력 필요) |
| `make db-dump` | `pg_dump`으로 `backups/`에 DB 덤프 |
| `make tunnel-up` / `make tunnel-down` | 터널 오버레이 기동 / 정지 |
| `make backend` | 도커 없이 Spring Boot 실행 (JDK 17 필요) |
| `make app` | 도커 없이 Expo 웹 실행 (Node 20+ 필요) |

윈도우에서는 `make`를 따로 설치해야 합니다(`winget install ezwinports.make`). Makefile이 셸을 `sh`로 고정하므로 PowerShell에서 실행해도 Git Bash에서 실행해도 동작이 같습니다. Git과 함께 설치되는 `sh.exe`가 PATH에 있어야 합니다.

## API 개요
### 스킬 점수 계산
- 목록 조회: `GET /api/score/skills?cardType=NORMAL&position=BATTER`
- 점수 계산: `POST /api/score`
- 요청 예시:
```json
{
  "cardType": "NORMAL",
  "position": "BATTER",
  "selections": [
    { "skillId": "S_001", "level": 2 },
    { "skillId": "S_002", "level": 2 },
    { "skillId": "S_003", "level": 2 }
  ]
}
```
- 응답 예시:
```json
{
  "total": 6.2,
  "perSkill": [
    {
      "skillId": "S_001",
      "name": "좌투선호",
      "score": 1.2,
      "perStat": [
        { "stat": "파워", "value": 0.66 },
        { "stat": "정확", "value": 0.54 }
      ]
    }
  ],
  "perStat": [
    { "stat": "파워", "value": 3.41 },
    { "stat": "정확", "value": 2.79 }
  ]
}
```

## TODO
- 스킬 설명 추가

## 면책 조항 (Disclaimer)
This involves an unofficial fan-made project. 본 프로젝트는 팬심으로 제작된 비공식 시뮬레이터이며, 게임 개발사(Com2uS) 및 MLB와 어떠한 공식적인 관계도 없습니다.

저작권 준수: 본 서비스는 게임 내 이미지(에셋), 로고, 상표를 무단으로 사용하지 않았으며, 모든 UI는 CSS와 무료 오픈소스 아이콘으로 직접 구현되었습니다.

비영리 목적: 이 프로젝트는 학습 및 포트폴리오 목적으로 제작되었으며, 게임 데이터에 대한 권리는 원저작권자에게 있습니다.

Unofficial Fan App: Not affiliated with, endorsed, sponsored, or specifically approved by Com2uS or MLB. All trademarks and game concepts belong to their respective owners.
