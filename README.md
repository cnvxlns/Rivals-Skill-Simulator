# Rivals-Skill-Simulator

MLB 라이벌(MLB Rivals) 모바일 게임의 스킬 조합 점수를 웹에서 계산해 보는 토이 프로젝트입니다. 카드 타입과 포지션을 고르고 스킬과 레벨을 선택하면 총점과 함께 스킬별·스탯별 기여도를 보여 줍니다.

## 핵심 기능
- **스킬 점수 계산기**: 카드 타입과 포지션에 맞는 스킬 3개(블랙 계열은 4개)와 레벨을 선택하면 총점, 스킬별 기여도, 스탯별 내역을 계산합니다. 타순·투수 슬롯·투타 방향·보유 스탯을 함께 넘기면 조건부 효과까지 반영합니다.
- **티어별 점수표**: 전체 스킬을 S레벨 기준으로 채점해 티어별 상위 N개를 내림차순으로 보여 줍니다.
- **산정 방식 공개**: 점수를 어떤 근거로 계산했는지 스킬·효과·스탯 가중치를 앱에서 그대로 확인할 수 있습니다.
- **카드 타입별 규칙**: 타입마다 슬롯 수와 레벨 사다리(등급 라벨)가 다릅니다. 백엔드가 이를 단일 기준으로 관리하고 앱은 그 결과를 받아 씁니다.
- **포지션 필터**: 투수/타자 전용 스킬 풀을 분리하며, 세부 포지션(SP·RP·CP·IF·OF 등)도 해석합니다. 카드 타입이나 포지션이 없으면 400을 반환합니다.
- **덱 관리(로그인 필요)**: 26명(타자 14 + 투수 12)짜리 덱을 구성해 저장하고 종합 점수를 받습니다. 주전 9명은 야구장 그림 위 실제 수비 위치에 배치되고, 선수 이름을 적을 수 있습니다. 계정별로 격리됩니다.
- **컬렉션 버프**: 같은 계열 카드를 모은 장수에 따라 덱 전체 능력치가 오릅니다. 채점에 반영되며 계열별 내역도 함께 돌려줍니다.
- **정적 데이터 시드**: `score_skills.csv`, `score_effects.csv`, `stat_weights.csv`를 애플리케이션 시작 시 읽어 메모리에 적재합니다. 스킬은 DB가 아니라 CSV가 원천이며, DB에는 계정과 덱만 들어갑니다.

### 카드 등급과 변형

카드는 **등급**과 **변형** 두 축을 가집니다. 스킬이 속한 **티어**는 또 다른 축이며, 셋은 서로 다릅니다.

**등급**(`cardGrade`) — 낮은 것부터. 이 서열이 `상대등급우세` 조건의 발동 확률을 정합니다.

| 등급 | 슬롯 | 고를 수 있는 스킬 |
|---|---|---|
| `SEASON` · `LIVE` | 3 | 아이언·브론즈·실버·골드 |
| `IMPACT` | 3 | 〃 |
| `PRIME` | 3 | 〃 |
| `MOMENT` | 3 | 〃 + 모먼트 |
| `SUPREME_MOMENT` | 3 | 〃 + 모먼트 |
| `SIGNATURE` | 3 | 아이언·브론즈·실버·골드 |
| `SIGNATURE_BLACK` | **4** | 〃 + 블랙 |
| `HOF` | 3 | 〃 + HOF |

**변형**(`cardVariant`) — `NONE`(기본) · `FA` · `WBC`. **서열을 바꾸지 않습니다.** `PRIME`·`SIGNATURE`·`SIGNATURE_BLACK`에만 붙으며, `WBC`만 WBC 전용 스킬 풀을 더합니다.

> `SUPREME_MOMENT`의 실제 서열 위치는 확인되지 않아 모먼트와 시그니처 사이에 두었습니다.
> 예전 단일 `cardType`(`NORMAL`·`WBC`·`WBC_BLACK` 등)으로 보내도 등급과 변형으로 자동 변환됩니다.

`position`은 `PITCHER` / `BATTER` 외에 `SP`, `RP`, `CP`, `C`, `1B`~`SS`, `IF`, `LF`/`CF`/`RF`, `OF`, `DH`를 받습니다. 값은 대소문자를 가리지 않습니다.

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
├── tools      # 스킬 데이터 도구 — CSV 검증기, 설명문 파서 (파이썬, 의존성 없음)
├── docs       # 참고 자료 (비스탯효과 목록, 채점 로직 메모)
├── .github    # EAS APK 빌드 / OTA 업데이트 / 데이터 검증 워크플로
├── docker-compose.yml         # db + backend + app 실행 스택 (+ .override / .tunnel)
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

`make up`은 스택을 올리기 전에 `.env`의 `APP_JWT_SECRET`이 비어 있으면 한 번 채웁니다. 이 값이 없으면
백엔드가 부팅마다 임의 서명 키를 만들어 **컨테이너를 다시 띄울 때마다 로그인이 풀립니다.**
`docker compose`를 직접 쓴다면 `.env`에 직접 넣어 주세요(`openssl rand -base64 48`).

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

점수 관련 엔드포인트는 인증 없이 열려 있습니다. 덱 관련은 로그인이 필요하며 덱은 소유자에게만 보입니다.

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| `GET` | `/api/health` | 헬스 체크. `{"status":"ok"}` |
| `GET` | `/api/score/skills` | 카드 타입·포지션에 맞는 선택 가능 스킬 목록 |
| `POST` | `/api/score` | 선택한 스킬 조합의 점수 계산 |
| `POST` | `/api/score/table?topN=10` | 티어별 스킬 점수표 (S레벨 기준, `topN` 0 이하면 전부) |
| `GET` | `/api/score/methodology` | 점수 산정 근거(효과·스탯 가중치) |
| `POST` | `/api/auth/signup` · `/api/auth/login` | 가입 · 로그인 → `{ token }` |
| `GET` | `/api/auth/me` | 내 계정 🔒 |
| `POST` | `/api/decks/score` | 저장 없이 덱 채점(편집 중 미리보기) |
| `GET` `POST` | `/api/decks` | 내 덱 목록 · 생성 🔒 |
| `GET` `PUT` `DELETE` | `/api/decks/{id}` | 상세 · 수정 · 삭제 🔒 |

🔒 표시는 `Authorization: Bearer <token>` 헤더가 필요합니다.

### 스킬 목록 조회
```
GET /api/score/skills?cardGrade=SIGNATURE&cardVariant=WBC&position=BATTER
```
`cardGrade`와 `position`은 필수이고 `cardVariant`는 생략하면 기본형입니다. 지원하지 않는 값이거나
등급에 없는 변형(예: `HOF` + `WBC`)이면 400을 반환합니다.

### 스킬 점수 계산
- 점수 계산: `POST /api/score`
- 요청 예시:
```json
{
  "cardGrade": "SIGNATURE",
  "cardVariant": "NONE",
  "position": "BATTER",
  "battingOrder": 3,
  "selections": [
    { "skillId": "S_001", "level": 2 },
    { "skillId": "S_002", "level": 2 },
    { "skillId": "S_003", "level": 2 }
  ]
}
```

**포지션에 따라 추가로 필수인 필드가 있습니다.** 빠지면 400을 반환합니다.

| 포지션 | 추가 필수 필드 | 허용 범위 |
|---|---|---|
| 타자 (`BATTER`, `C`, `1B`~`SS`, `LF`~`RF`, `IF`, `OF`, `DH`) | `battingOrder` | 1 ~ 9 |
| 선발 (`SP`, `PITCHER`) | `pitcherSlot` | 1 ~ 5 |
| 불펜 (`RP`) | `pitcherSlot` | 1 ~ 6 |
| 마무리 (`CP`) | 없음 | — |

나머지 필드는 선택입니다.

| 필드 | 설명 |
|---|---|
| `throwHand` / `batHand` | 선수 본인의 투구·타격 방향(`LEFT`/`RIGHT`). 미지정 시 우완·우타로 간주합니다 |
| `userStats` | 보유 스탯(`{"파워": 100.0}` 형태). 스탯 조건이 붙은 효과 계산에 씁니다 |

- `selections` 개수는 카드 타입의 슬롯 수를 넘을 수 없습니다(위 표 참고). 같은 스킬을 중복 선택할 수 없습니다.
- `level`은 1부터 시작하며, 해당 스킬의 최대 레벨(`maxLevel`)을 넘으면 400을 반환합니다.
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
  위 예시는 주요 필드만 추린 것입니다. 실제 응답에는 최상위 `warnings`와, `perSkill` 항목마다 `resolvedDescription`(수치가 채워진 스킬 설명)·`breakdown`(계산 근거)·`warnings`가 함께 들어갑니다.

### 컬렉션 버프

덱 채점 응답에는 `collectionBuff`가 함께 들어갑니다. 같은 계열 카드를 모은 장수가 임계값을
넘을 때마다 덱 전체 선수의 능력치가 1씩 오르고, 계열끼리도 누적됩니다.

| 계열 | 포함 등급 | 임계값 | 대상 |
|---|---|---|---|
| HOF | `HOF` | 2 · 5 · 8 | 전원 |
| 시그니처 | `SIGNATURE` `SIGNATURE_BLACK` | 2 · 5 · 8 · 11 · 14 · 17 | 전원 |
| 모먼트 | `MOMENT` `SUPREME_MOMENT` | 2 · 5 · 8 · 11 · 14 | 전원 |
| 라이브 | `LIVE` | 2 · 4 · 8 · 11 | 전원 |
| 시즌 | `SEASON` | 6 · 16 (타자) / 10 · 20 (투수) | 역할별 |

- **슈프림 모먼트 한 장은 모먼트 두 장으로 셉니다.** 그래서 집계는 장수가 아니라 가중치 합입니다.
- 변형(FA·WBC)은 등급을 바꾸지 않으므로 같은 계열로 셉니다.
- **임팩트와 프라임은 어느 계열에도 넣지 않았습니다.** 두 등급에 걸리는 컬렉션 버프가 확인되지 않았습니다.
- 장수는 덱 전체로 세고, 역할은 버프를 받을 대상만 가릅니다. 타자만 시즌 카드를 14장 들고 있어도
  컬렉션은 14장이라 투수 임계값 10도 함께 넘어갑니다.

> **총점이 잘 움직이지 않습니다.** 능력치는 `floor(기준스탯 x 계수)` 형태의 비례 효과에서만
> 쓰이는데 그런 스킬이 239종 중 16종뿐이고, 계수가 0.01~0.06이라 정수 경계를 넘겨야 값이 오릅니다.
> 합산형 기준 스탯(`주루+수비`)을 쓰는 스킬은 보정이 두 배로 들어가 비교적 잘 반응합니다.

### 티어별 점수표
```
POST /api/score/table?topN=10
{ "position": "BATTER", "battingOrder": 3 }
```
전체 스킬을 S레벨 기준으로 채점해 티어별 상위 `topN`개를 내림차순으로 돌려줍니다(`{"tiers": [...]}`). `topN`은 기본 10이고 0 이하면 전부 반환합니다.

응답이 이미 티어별로 나뉘므로 **카드 등급은 받지 않습니다.**


## 면책 조항 (Disclaimer)
This involves an unofficial fan-made project. 본 프로젝트는 팬심으로 제작된 비공식 시뮬레이터이며, 게임 개발사(Com2uS) 및 MLB와 어떠한 공식적인 관계도 없습니다.

저작권 준수: 본 서비스는 게임 내 이미지(에셋), 로고, 상표를 무단으로 사용하지 않았으며, 모든 UI는 CSS와 무료 오픈소스 아이콘으로 직접 구현되었습니다.

비영리 목적: 이 프로젝트는 학습 및 포트폴리오 목적으로 제작되었으며, 게임 데이터에 대한 권리는 원저작권자에게 있습니다.

Unofficial Fan App: Not affiliated with, endorsed, sponsored, or specifically approved by Com2uS or MLB. All trademarks and game concepts belong to their respective owners.
