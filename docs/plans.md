# Android 앱 개발 계획 (Rivals Skill Simulator)

> **문서 용도**
> 이 문서는 두 가지 목적을 가진다.
> 1. **개발 계획 및 진행 상태 기록** — 무엇을 만들지, 지금 어디까지 왔는지 추적한다.
> 2. **검증 기준(Reference)** — 이후 구현이 계획과 일치하는지, 웹 프론트엔드와 동작이 동일한지 검증할 때 기준으로 사용한다.
>
> 진행에 따라 아래 **진행 상태 체크리스트**와 **진행 로그**를 계속 갱신할 것.
> 상태 표기: `⬜ 미착수` · `🟨 진행중` · `✅ 완료` · `⛔ 보류/차단`

---

## 1. 목표

기존 웹 프론트엔드(`skill-sim-project/frontend`, Next.js)와 **거의 동일한 역할**을 수행하는 네이티브 안드로이드 앱을 `android/` 디렉토리에 구축한다.

웹 프론트엔드는 다음 두 기능을 제공한다. 안드로이드 앱도 동일하게 제공한다.

- **시뮬레이터 (Simulator)** — 스킬 체인지 롤 시뮬레이션 (`/api/skills/*`)
- **점수 계산기 (Calculator)** — 스킬 조합 점수 계산 (`/api/score*`)

## 2. 확정된 설계 결정

| 항목 | 결정 | 비고 |
|------|------|------|
| 백엔드 연동 | **기존 REST 백엔드 재사용** | 롤/점수 계산 로직은 서버에 유지. 앱은 API 클라이언트 역할. |
| 기능 범위 | **두 탭 모두 (풀 패리티)** | 시뮬레이터 + 점수 계산기 + 5개국어 i18n |
| UI 아키텍처 | **Jetpack Compose + MVVM** | 프론트의 hook 기반 상태관리를 ViewModel + StateFlow로 이식 |
| 언어 | **Kotlin** | |
| 오프라인 캐싱 | **사용자 입력 스탯 중심 + 언어 설정** | Jetpack DataStore. 웹 대비 신규 기능(웹은 언어만 localStorage 저장). |

## 3. 아키텍처

프론트엔드의 계층 구조를 안드로이드 표준 패턴으로 매핑한다.

```
┌─────────────────────────────────────────────┐
│ ui (Compose)                                 │
│  SimulatorScreen / CalculatorScreen          │
│  + 공용 컴포저블 (SkillCard 등) + 탭/언어선택 │
└───────────────┬─────────────────────────────┘
                │ observes StateFlow
┌───────────────┴─────────────────────────────┐
│ ViewModel (MVVM)                             │
│  SimulatorViewModel  ← useSkillSimulator      │
│  CalculatorViewModel ← useScoreCalculator     │
└───────────────┬─────────────────────────────┘
                │ suspend calls
┌───────────────┴─────────────────────────────┐
│ data                                         │
│  Repository → Retrofit API → 백엔드          │
│  DataStore (오프라인 캐싱)                    │
└─────────────────────────────────────────────┘
```

### 프론트 → 안드로이드 대응표

| 프론트엔드 (React/Next.js) | 안드로이드 (Kotlin/Compose) |
|---|---|
| `lib/api.ts` (axios) | `data/api/SkillApi.kt` (Retrofit) + `SkillRepository` |
| `types/index.ts` (enum/type) | `data/model/*.kt` (data class / enum class) |
| `lib/useSkillSimulator.ts` | `ui/simulator/SimulatorViewModel.kt` |
| `lib/useScoreCalculator.ts` | `ui/calculator/CalculatorViewModel.kt` |
| `views/SimulatorView.tsx` | `ui/simulator/SimulatorScreen.kt` |
| `views/CalculatorView.tsx` | `ui/calculator/CalculatorScreen.kt` |
| `components/TabNavigation.tsx` | `ui/common/AppTabs.kt` |
| `components/SkillCard.tsx` | `ui/common/SkillCard.kt` |
| `components/SkillSelectionModal.tsx` | `ui/common/SkillSelectionDialog.kt` |
| `components/ScoreSkillPicker.tsx` | `ui/calculator/ScoreSkillPicker.kt` |
| `components/SkillSetEffect.tsx` | `ui/common/SkillSetEffect.kt` |
| `components/LanguageSelector.tsx` | `ui/common/LanguageSelector.kt` |
| `lib/i18n.tsx` + `locales/translations.ts` | `i18n/` (번역 맵) + DataStore 언어 저장 |

## 4. 모듈 구조 (예정)

```
android/
  settings.gradle.kts
  build.gradle.kts
  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/rivals/skillsim/
        MainActivity.kt
        data/
          api/        SkillApi.kt, RetrofitProvider.kt
          model/      Enums.kt, Skill.kt, RollRequest/Response.kt, ScoreRequest/Response.kt
          repository/ SkillRepository.kt
          local/      UserPrefsDataStore.kt   ← 오프라인 캐싱
        ui/
          simulator/  SimulatorScreen.kt, SimulatorViewModel.kt
          calculator/ CalculatorScreen.kt, CalculatorViewModel.kt, ScoreSkillPicker.kt
          common/     AppTabs.kt, SkillCard.kt, SkillSelectionDialog.kt,
                      SkillSetEffect.kt, LanguageSelector.kt
          theme/      Color.kt, Theme.kt, Type.kt
        i18n/         Strings.kt, LanguageCode.kt
```

## 5. API 계약 매핑 (검증 기준)

앱의 API 모델은 아래 계약을 그대로 따른다. Kotlin 모델은 `types/index.ts`와 필드명·타입이 1:1로 일치해야 한다.

| 엔드포인트 | 메서드 | 요청 | 응답 | 사용처 |
|---|---|---|---|---|
| `/api/skills/roll` | POST | `RollRequest` | `RollResponse` | 시뮬레이터 롤 |
| `/api/skills/initial` | GET | `cardType, position, subPosition?` | `RollResponse` | 시뮬레이터 초기 스킬 |
| `/api/score/skills` | GET | `cardType, position` | `ScoreSkillOption[]` | 계산기 스킬 목록 |
| `/api/score` | POST | `ScoreRequest` | `ScoreResponse` | 계산기 점수 계산 |

**핵심 enum (반드시 서버 값과 문자열 일치):**
`Tier`(IRON~WBC), `Grade`(D~S4), `TicketType`(3종), `CardType`(6종), `Position`(PITCHER/BATTER), `SubPosition`(ALL/SP/RP/CP/C/1B.../DH).

## 6. 오프라인 캐싱

- **저장 대상 (사용자 입력 중심):**
  - 계산기 사용자 스탯 (`userStats`: 구속/변화/... 등 스탯별 수치)
  - 계산기 설정값 (cardType, position, subPosition, battingOrder)
  - 언어 선택
  - (선택) 시뮬레이터 티켓 사용 횟수(`ticketUsageCounts`), 보호 사용 횟수
- **저장 수단:** Jetpack DataStore (Preferences)
- **동작:** 앱 재실행 시 마지막 입력 스탯/설정을 복원한다. 스킬 목록·롤 결과 등 서버 계산 데이터는 캐싱 대상이 아니다(온라인 시 재요청).

## 7. i18n

- 지원 언어: 웹과 동일하게 **5개국어** (`translations.ts` 기준).
- 웹의 번역 문자열을 그대로 이식하여 키/값 누락이 없어야 한다.
- 앱 내 언어 전환 시 즉시 반영되며 선택은 DataStore에 저장된다.

---

## 8. 마일스톤 & Definition of Done

| # | 마일스톤 | Definition of Done |
|---|---------|---------------------|
| M1 | 프로젝트 스캐폴딩 | `android/`에 Gradle 프로젝트 빌드 성공, 빈 화면 실행됨 |
| M2 | data 레이어 | 4개 엔드포인트 Retrofit 연결, 모델이 API 계약(5장)과 일치, 응답 파싱 성공 |
| M3 | 시뮬레이터 탭 | 카드/티켓/포지션 설정 → 롤 → 슬롯 결과·총점 표시가 웹과 동일하게 동작 |
| M4 | 점수 계산기 탭 | 스킬 선택/레벨 → 계산 → 총점·스킬별·스탯별 분해가 웹과 동일하게 동작 |
| M5 | i18n | 5개국어 전환 동작, 문자열 누락 없음 |
| M6 | 오프라인 캐싱 | 사용자 입력 스탯/설정/언어가 재실행 후 복원됨 |
| M7 | 마감 검증 | 9장 검증 기준 전부 통과 |

## 9. 검증 방법 (이후 검증 시 이 절 사용)

동일 입력에 대해 **웹 프론트와 안드로이드 앱의 결과가 일치**하는지를 기준으로 검증한다.

1. **API 계약 검증** — 앱 모델 필드/타입이 `types/index.ts`와 일치. 4개 엔드포인트 응답 파싱 무오류.
2. **시뮬레이터 동등성** — 동일 설정에서 롤 결과 슬롯 구조·총점 계산 방식이 웹과 일치(난수 결과 자체가 아니라 처리 로직·표시 일치).
3. **계산기 동등성** — 동일 스킬/레벨/스탯 입력 → `/api/score` 응답을 웹과 앱이 동일하게 렌더링(총점, perSkill, perStat, 내 스탯/상대 스탯 부호 처리 포함).
4. **i18n 검증** — 각 언어에서 화면 문자열 누락/하드코딩 없음.
5. **오프라인 캐싱 검증** — 스탯 입력 후 앱 종료·재실행 시 값 복원.

---

## 10. 진행 상태 체크리스트

- ✅ M1 프로젝트 스캐폴딩
- ✅ M2 data 레이어 (Retrofit + 모델)
- ✅ M2 오프라인 캐싱 DataStore 기반 구성
- ✅ M3 시뮬레이터 탭
- ✅ M4 점수 계산기 탭
- ✅ M5 i18n (5개국어)
- ✅ M6 오프라인 캐싱 (사용자 스탯/설정 복원)
- ✅ M7 마감 검증

## 11. 진행 로그

| 날짜 | 내용 |
|------|------|
| 2026-07-01 | 계획 문서 작성. 설계 결정 확정(REST 재사용 · 풀 패리티 · Compose/MVVM · 사용자 스탯 오프라인 캐싱). |
| 2026-07-01 | M1 완료. `skill-sim-project/android/`에 Android Gradle 프로젝트를 구성하고 Compose 기반 빈 화면을 추가했다. 검증: `:app:testDebugUnitTest`, `:app:assembleDebug` 성공. |
| 2026-07-01 | M2 data 레이어 완료. `types/index.ts` 계약에 맞춘 Kotlin 모델, 4개 Retrofit 엔드포인트, `SkillRepository`를 추가하고 응답 JSON 파싱 테스트를 통과했다. |
| 2026-07-01 | M2 DataStore 기반 구성 완료. 언어, 계산기 설정, 사용자 스탯을 Preferences DataStore로 저장/복원하는 래퍼와 round-trip 테스트를 추가했다. |
| 2026-07-01 | M3~M7 최종 완료. 모먼트 테마 연동 및 1번 슬롯 잠금 규칙이 적용된 시뮬레이터, 타순 입력 및 내/상대 스탯 분해 렌더링이 반영된 계산기를 구축했다. 웹 translations.ts의 5개국어 문자열을 완벽하게 이식하고 DataStore 저장/복원 검증 및 Gradle 단위 테스트를 통과시켰다. |

---
---

# 신규 계획: 점수 산정 방식 공개 (Methodology Disclosure)

> **문서 용도**: 아래는 위 Android 계획과 독립된 신규 실행 계획이다. 담당(agy 서브에이전트)은 이 섹션만으로 콜드 스타트하여 구현할 수 있어야 한다. 진행에 따라 체크리스트/로그를 갱신할 것. 상태 표기: `⬜ 미착수` · `🟨 진행중` · `✅ 완료` · `⛔ 보류/차단`

## A. 목표

점수 계산기의 **산정방식(공식 + 조건확률 + 스탯 가중치)을 프론트와 앱 사용자에게 전면 공개**한다. 목적은 **신뢰·투명성 확보** — 사용자가 "이 점수가 임의값이 아니라 정의된 공식·가정에서 나온다"를 납득하게 한다.

## B. 확정된 설계 결정 (브레인스토밍 결과)

| 항목 | 결정 |
|------|------|
| 전달 방식 | **B안: 전용 "산정 방식" 화면 + methodology API** (프론트/앱 모두 별도 탭) |
| 공개 깊이 | **상수값까지 전부 공개** (조건확률·가중치 실제 숫자 노출) |
| 범위 | **백엔드 API + 프론트 + 앱 한 번에** |
| 핵심 원칙 | **단일 진실 소스(Single Source of Truth)** — 상수를 문서/UI에 복사하지 않는다. 백엔드가 **계산에 실제로 쓰는 상수 그대로**를 API로 노출하고, 프론트·앱은 렌더링만 한다. 상수가 바뀌면 설명도 자동으로 따라간다. |
| 근거 텍스트 i18n | 백엔드는 **값 + 안정적 `descriptionKey`** 만 반환. 각 클라이언트는 `descriptionKey`를 자신의 i18n(`translations.ts` / `Strings.kt`)에서 지역화. (값은 단일 소스, 문구는 클라이언트별 5개국어) |

## C. 산정방식의 실제 구조 (구현 참고 — 출처: `service/ScoreCalculator.java`)

- **스킬별 점수**: `skillScore = Σ_effects ( weight × value × conditionProbability )`
- **총점**: `total = Σ skillScore`
- **weight**: 스탯별 가중치. 출처 `config/ScoreDataLoader.getStatWeights()` (데이터 파일 로드, `Map<String,Double>`).
- **value**: 스킬 레벨별 증가치. `%`형 효과(= `baseStat` 존재)는 `value = floor(baseValue × rawValue)`. baseValue 기본값: 일반 스탯 `120`, 이름에 "덱" 포함 스탯 `500`. 합산형 기준스탯("변화+제구")은 구성 스탯 합.
- **conditionProbability**: 조건 발생 확률. 다중 조건은 `+`로 분할 후 결합: `모드_*` 는 max, `포지션_*`/`선발*`/`중계*` 는 max, 나머지는 곱. `ALWAYS`/빈값 = 1.0.
- **반올림**: `round(x) = Math.round(x*100)/100` (소수 2자리).
- **조건확률 상수 그룹** (전부 공개 대상):
  - 정적: `STATIC_CONDITION_PROBABILITIES`, `PLATE_SITUATION_PROBABILITIES`, `GAME_STATE_PROBABILITIES`, `MODE_PROBABILITIES`, `LAUNCH_ANGLE_PROBABILITIES`
  - 역할별 테이블: `INNING_WEIGHTS_BY_ROLE`, `GUTS_PROBABILITIES_BY_ROLE`, `NINE_BATTER_DURATION_PROBABILITIES_BY_ROLE`, `MAESTRO_CUMULATIVE_PROBABILITIES_BY_ROLE`
  - 타순별 테이블: `BATTING_ORDER_DEFAULT_PROBABILITIES`, 타석 도달 확률 3종(`TOP/MIDDLE/LOWER_ORDER_PLATE_APPEARANCE_REACH`)
  - 게이트: 포지션 게이트/슬롯 게이트(1.0 또는 0.0로 동작 — 토큰 목록과 의미만 설명)

## D. 아키텍처 / 데이터 흐름

```
ScoreCalculator 상수 (public static 접근자)  ─┐
ScoreDataLoader.getStatWeights()             ─┼─→ MethodologyService ─→ MethodologyResponse (DTO)
                                              ┘        │  GET /api/score/methodology (JSON)
                                                       ├─→ 프론트 methodology 탭 (MethodologyView)
                                                       └─→ 앱 MethodologyScreen
클라이언트 i18n(translations.ts / Strings.kt) ─→ descriptionKey → 지역화 문구
```

**컴포넌트 경계**: `MethodologyService`는 읽기 전용(계산 로직과 분리). 클라이언트 화면은 순수 렌더러(계산 로직 없음).

## E. 작업 항목

### E-1. 백엔드 (Spring, `skill-sim-project/backend`)
1. **상수 노출 리팩터**: `service/ScoreCalculator.java`의 위 상수 그룹들을 **public static 읽기전용 접근자**로 노출한다(불변 복사본 반환). 계산 코드는 계속 동일 상수를 사용 → 단일 소스 보장. (대안: `ScoreConstants` 클래스로 추출 후 양쪽에서 참조 — 더 깔끔하나 변경폭 큼. 접근자 방식 권장.)
2. **DTO** `dto/MethodologyResponse.java` 신설:
   - `formula`: `perSkillFormula`, `totalFormula`, `percentEffectRule`(기본값 120/500 포함), `roundingRule`, `conditionCombinationRule` — 각 항목에 표시용 문자열 + `descriptionKey`.
   - `statWeights`: `Map<String,Double>` (ScoreDataLoader에서).
   - `conditionProbabilities`: 그룹별(static / byRole / byBattingOrder / gates) 토큰→값 + 각 토큰 `descriptionKey`.
3. **서비스** `service/MethodologyService.java`: 위 접근자 + `getStatWeights()`를 조합해 DTO 생성.
4. **엔드포인트**: `controller/ScoreController.java`에 `@GetMapping("/methodology")` 추가 → `GET /api/score/methodology`. (기존 CORS 설정 상속)
5. **테스트**: `MethodologyServiceTest` — 응답에 알려진 값 포함 검증(예: static `홈`=0.3, `원정`=0.7), statWeights 비어있지 않음, 각 값이 ScoreCalculator 상수와 일치(단일 소스 회귀 가드).

### E-2. 프론트엔드 (Next.js, `skill-sim-project/frontend`)
1. `types/index.ts`: `Methodology*` 응답 타입 추가.
2. `lib/api.ts`: `getMethodology()` (`GET /api/score/methodology`).
3. `lib/useMethodology.ts`: 로드/에러 상태 훅.
4. `views/MethodologyView.tsx`: 섹션 구성 — ① 공식 설명, ② 스탯 가중치 표, ③ 조건확률 표(그룹별, 값 + 지역화 근거). 기존 계산기 톤/스타일 유지.
5. `components/TabNavigation.tsx` + `app` 상위: `TabKey`에 `'methodology'` 추가, 탭 렌더 연결.
6. `CalculatorView.tsx`: 결과 수식 접기 영역(`score_formula`)에 "이 숫자는 어떻게 나오나요? → 산정 방식" 링크(해당 탭으로 전환).
7. `locales/translations.ts`: 섹션 라벨 + 모든 `descriptionKey` 근거 문구를 **5개국어** 추가(누락 금지).

### E-3. 앱 (Android/Compose, `skill-sim-project/android`)
1. `data/model/MethodologyModels.kt`: 백엔드 DTO와 1:1 일치.
2. `data/api/SkillApi.kt`: `@GET("/api/score/methodology")`.
3. `data/repository/SkillRepository.kt`: methodology 조회 메서드.
4. `ui/methodology/MethodologyViewModel.kt` + `ui/methodology/MethodologyScreen.kt`: 로드/렌더.
5. `ui/common` 탭(AppTabs 상당) + `MainActivity`: 세 번째 탭 추가.
6. `i18n/Strings.kt`: 라벨 + 모든 `descriptionKey` 근거 문구 **5개국어** 추가.

## F. Definition of Done / 검증

1. `GET /api/score/methodology` 200, 공식·statWeights·조건확률(값 포함) 전부 반환. 단일 소스 회귀 테스트 통과.
2. 웹: 세 번째 탭에서 공식·가중치·조건확률 값과 근거가 렌더링됨.
3. 앱: 세 번째 탭 화면에서 동일 내용 렌더링(웹과 값 일치).
4. **일관성**: 계산기 결과 수식에 나오는 조건확률/가중치가 산정 방식 화면의 값과 동일(같은 백엔드 상수).
5. i18n: 신규 문자열 5개국어 누락/하드코딩 없음(웹·앱 both).
6. 백엔드 `:test`, 프론트 빌드, 안드로이드 `:app:testDebugUnitTest`·`:app:assembleDebug` 통과.

## G. 진행 상태 체크리스트

- ✅ E-1 백엔드 methodology API (상수 노출 + DTO + 서비스 + 엔드포인트 + 테스트)
- ✅ E-2 프론트 methodology 탭/뷰 + i18n
- ✅ E-3 앱 methodology 탭/화면 + i18n
- ✅ F 검증 (일관성 + 빌드/테스트)

## H. 진행 로그

| 날짜 | 내용 |
|------|------|
| 2026-07-02 | 계획 수립. 브레인스토밍으로 B안(전용 탭+API)·상수값 전면 공개·일괄 범위·단일 진실 소스 원칙 확정. 구현은 agy 서브에이전트에 위임. |
| 2026-07-02 | E-1~E-3 구현 완료. 통합 검증 중 발견한 갭 마감: (1) 안드로이드 테스트 페이크 2종에 `fetchMethodology()` 오버라이드 누락 → 컴파일 오류 수정, (2) `Strings.kt` JP(일본어) 맵에 methodology i18n 키 ~150개 누락으로 `StringsTest.everyLanguageHasSameKeys` 실패 → agy가 일본어 번역 보강. 검증 통과: 백엔드 `:test`, 프론트 `build`, 안드로이드 `:app:testDebugUnitTest`. F 검증 완료. |

