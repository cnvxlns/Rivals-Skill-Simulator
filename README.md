# Rivals-Skill-Simulator

MLB 라이벌(MLB Rivals) 모바일 게임의 스킬 변경 시스템을 웹에서 실험할 수 있는 토이 프로젝트입니다. 카드 타입(Prime/Moment/Signature), 변경권 종류(일반/고급/최고급), 포지션 필터를 조합해 실제 게임 규칙에 가까운 확률 롤을 돌려 볼 수 있고, 스킬 조합의 점수도 계산할 수 있습니다.

## 핵심 기능
- 스킬 변경 시뮬레이션: 변경권별 확률 테이블(Weighted Random) 적용, 최고급 변경권 사용 시 1번 슬롯 골드 티어 보장, 슬롯 간 스킬 중복 방지.
- 카드 타입별 잠금 규칙: Prime은 1번 슬롯 잠금 가능, Moment는 1번 슬롯이 Moment 티어일 때만 잠금 가능, Signature는 잠금 불가. 백엔드에서 검증하고 프론트에서도 제어합니다.
- 스킬 레벨 보호: 슬롯별 `useLevelProtectionSlots` 플래그로 등급 하락을 방지하며, 기존 등급보다 낮아지지 않도록 처리합니다.
- 포지션 필터: Pitcher/Batter 전용 스킬 풀을 분리하며, 요청에 포지션 누락 시 400 오류를 반환합니다.
- 스킬 점수 계산기: 카드 타입과 포지션을 기준으로 스킬 3개(시그니처 블랙은 4개)와 레벨을 선택하면 총점, 스킬별 기여도, 스탯별 내역을 계산합니다.
- 정적 데이터 시드: `score_skills.csv`, `score_effects.csv`, `stat_weights.csv`를 애플리케이션 시작 시 읽어 SQLite DB에 적재하며, 스킬 변경 롤과 점수 계산이 동일한 스킬 데이터를 공유합니다.

## 기술 스택
**App (Web / Android)**  
![Expo](https://img.shields.io/badge/Expo-000020?style=flat&logo=expo&logoColor=white)
![React Native](https://img.shields.io/badge/React_Native-61DAFB?style=flat&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat&logo=typescript&logoColor=white)

**Backend**  
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat&logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-007396?style=flat&logo=java&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat&logo=gradle&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-003B57?style=flat&logo=sqlite&logoColor=white)

- App: Expo SDK 57, React Native, expo-router, TypeScript, axios. 웹과 안드로이드를 한 코드베이스로 빌드합니다.
- Backend: Spring Boot 3.2, Java 17, Gradle(Wrapper), Spring Data JPA, SQLite, OpenCSV.
- DB: SQLite(`simulator.db`) 사용, `schema.sql`로 필요한 테이블을 생성합니다.

## 폴더 구조
```
Rivals-Skill-Simulator/
├── backend    # Spring Boot API 서버 (포트 8080, SQLite + CSV 시드)
├── app        # Expo(React Native) 앱 — 웹/안드로이드 공용 UI, axios로 /api/skills/roll 및 /api/score 호출
├── docs       # 데이터 원천(rivals_skills.xlsx)과 변환기(convert_xlsx.py), 기획 노트
├── .github    # EAS 빌드/OTA 배포, Render keep-alive 워크플로
└── README.md  # 본 문서
```

## 브랜치 전략

| 브랜치 | 역할 |
|---|---|
| `deploy` | 배포 브랜치. Vercel(웹)과 Render(백엔드)가 이 브랜치를 바라보며, push 시 EAS OTA 업데이트도 나갑니다. |
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
3. 기본 포트는 `http://localhost:8080`입니다. `simulator.db`는 루트에 생성되며, 부팅 시 `score_skills.csv`, `score_effects.csv`, `stat_weights.csv`를 읽어 데이터를 적재합니다.

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

## API 개요
### 스킬 변경
- 엔드포인트: `POST /api/skills/roll`
- 요청 예시:
```json
{
  "cardType": "PRIME",
  "ticketType": "SUPREME_SKILL_CHANGE",
  "useLevelProtectionSlots": [true, false, false],
  "lockedSlots": [0],
  "currentSkillIds": [101, null, null],
  "currentLevels": ["A", "B", "D"],
  "position": "PITCHER"
}
```
  - `lockedSlots`는 0부터 시작하는 인덱스.
  - `currentLevels`는 JSON alias로 `currentGrades`도 허용됩니다.
  - `position`은 필수이며 누락 시 400 반환.
- 응답 예시:
```json
{
  "slots": [
    {
      "skill": {
        "id": 1,
        "name": "Skill name",
        "tier": "GOLD",
        "position": "PITCHER",
        "description": "Skill description",
        "weight": 1
      },
      "grade": "A"
    }
  ]
}
```

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
