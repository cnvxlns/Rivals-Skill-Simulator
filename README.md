# Rivals-Skill-Simulator

MLB 라이벌(MLB Rivals) 모바일 게임의 스킬 변경 시스템을 웹에서 실험할 수 있는 토이 프로젝트입니다. 카드 타입(Prime/Moment/Signature), 변경권 종류(일반/고급/최고급), 포지션 필터를 조합해 실제 게임 규칙에 가까운 확률 롤을 돌려 볼 수 있습니다.

## 핵심 기능
- 스킬 변경 시뮬레이션: 변경권별 확률 테이블(Weighted Random) 적용, 최고급 변경권 사용 시 1번 슬롯 골드 티어 보장, 슬롯 간 스킬 중복 방지.
- 카드 타입별 잠금 규칙: Prime은 1번 슬롯 잠금 가능, Moment는 1번 슬롯이 Moment 티어일 때만 잠금 가능, Signature는 잠금 불가. 백엔드에서 검증하고 프론트에서도 제어합니다.
- 스킬 레벨 보호: 슬롯별 `useLevelProtectionSlots` 플래그로 등급 하락을 방지하며, 기존 등급보다 낮아지지 않도록 처리합니다.
- 포지션 필터: Pitcher/Batter 전용 스킬 풀을 분리하며, 요청에 포지션 누락 시 400 오류를 반환합니다.
- 정적 데이터 시드: `src/main/resources/skills.csv`를 애플리케이션 시작 시 읽어 SQLite DB에 적재합니다.

## 기술 스택
- Frontend: Next.js 14(App Router), TypeScript, Tailwind CSS, axios, lucide-react 아이콘.
- Backend: Spring Boot 3.2, Java 17, Maven, Spring Data JPA, SQLite, OpenCSV.
- DB: SQLite(`simulator.db`) 사용, `hibernate.ddl-auto=create`로 부팅 시 테이블을 다시 생성합니다.

## 폴더 구조
```
skill-sim-project/
├── backend   # Spring Boot API 서버 (포트 8080, SQLite + CSV 시드)
├── frontend  # Next.js 14 UI (포트 3000, axios로 /api/skills/roll 호출)
└── README.md # 본 문서
```

## 실행 방법
### 1) Backend (Spring Boot)
1. 필수: JDK 17, Maven 3.9+
2. 실행:
   ```bash
   cd skill-sim-project/backend
   mvn spring-boot:run
   ```
3. 기본 포트는 `http://localhost:8080`입니다. `simulator.db`는 루트에 생성되며, 부팅 시 `skills.csv`를 읽어 테이블을 초기화합니다(DDL create라 커스텀 데이터는 재시작 시 삭제될 수 있음).

### 2) Frontend (Next.js)
1. 필수: Node.js 18.17+ (Next.js 14 요구), npm
2. 실행:
   ```bash
   cd skill-sim-project/frontend
   npm install
   npm run dev
   ```
3. 기본 포트는 `http://localhost:3000`이며, 백엔드가 8080에서 떠 있어야 API 요청이 성공합니다.
4. 프로덕션 빌드:
   ```bash
   npm run build
   npm start
   ```

## API 개요
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
## TODO
- 포지션별 등장 스킬 세부 구현(ex. 더블스토퍼는 중계, 마무리 투수에게만 등장, 포수리드는 포수에가만 등장)
- 모먼트, HOF스킬 추가
- 스킬 설명 추가
- 스킬 점수 계산


## 참고
- 프론트엔드 UI/사용법 상세: `skill-sim-project/frontend/README.md`
- CORS는 기본으로 `http://localhost:3000`에서 허용되도록 설정되어 있습니다.

## 면책 조항 (Disclaimer)
This involves an unofficial fan-made project. 본 프로젝트는 팬심으로 제작된 비공식 시뮬레이터이며, 게임 개발사(Com2uS) 및 MLB와 어떠한 공식적인 관계도 없습니다.

저작권 준수: 본 서비스는 게임 내 이미지(에셋), 로고, 상표를 무단으로 사용하지 않았으며, 모든 UI는 CSS와 무료 오픈소스 아이콘으로 직접 구현되었습니다.

비영리 목적: 이 프로젝트는 학습 및 포트폴리오 목적으로 제작되었으며, 게임 데이터에 대한 권리는 원저작권자에게 있습니다.

Unofficial Fan App: Not affiliated with, endorsed, sponsored, or specifically approved by Com2uS or MLB. All trademarks and game concepts belong to their respective owners.
