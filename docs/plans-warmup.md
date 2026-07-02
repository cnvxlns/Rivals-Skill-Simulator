# 실행 계획: 백엔드 콜드스타트 워업 UX (Backend Cold-Start Warm-up)

> **문서 용도**: 이 파일은 `plans.md`(methodology 등)와 **의도적으로 분리**된 독립 실행 계획이다. 담당(agy 서브에이전트)은 이 문서만으로 콜드 스타트하여 구현할 수 있어야 한다.
>
> **⚠️ 착수 시점 / 충돌 방지**: 이 작업은 아래 파일들에서 **methodology 공개 작업과 파일이 겹친다**(`frontend/src/app/page.tsx`, `frontend/src/lib/api.ts`, `frontend/src/locales/translations.ts`, `android .../MainActivity.kt`, `android .../i18n/Strings.kt`, `android .../data/api/SkillApi.kt`, `backend .../controller`). **methodology 작업(코드+빌드 검증)이 완전히 끝난 뒤에 착수**한다. 그 전에는 시작하지 말 것.
>
> 상태 표기: `⬜ 미착수` · `🟨 진행중` · `✅ 완료` · `⛔ 보류/차단`

## A. 목표

Render 무료 티어 백엔드가 비활성 시 잠들어 첫 요청이 ~30–45초 걸리는 문제를, **선제적 워업 + 지연 후 전체화면 블로킹** 로딩 UX로 개선한다. 목적: "고장이 아니라 서버를 깨우는 중"임을 사용자에게 명확히 알리고, 준비되면 자동 진입하게 하여 신뢰 있는 대기 경험을 제공.

## B. 확정된 설계 결정 (브레인스토밍 결과)

| 항목 | 결정 |
|------|------|
| 감지 방식 | **선제적 워업** — 앱/페이지 진입 즉시 경량 health 엔드포인트 폴링으로 백엔드를 깨움 |
| 화면 동작 | **지연 후 전체화면 블로킹** — 웜스타트(빠른 응답)면 화면 안 뜸(깜박임 방지), 콜드면 전체화면 오버레이로 상호작용 차단 + 안심 메시지·경과 표시, 준비되면 자동 해제 |
| 구현 접근 | **A안**: 전용 `GET /api/health` 엔드포인트 + 클라이언트 폴링 게이트 |
| 근거 텍스트 i18n | 워업 문구는 **5개국어** (`translations.ts` / `Strings.kt`) |

## C. 권장 파라미터 (구현 기준값)

- 오버레이 노출 지연 임계: **~1200ms** (이 시간 내 준비되면 오버레이 미표시 → 웜스타트 깜박임 방지)
- 폴링 간격: **2초**
- 시도당 타임아웃: **8초** (콜드 시 단일 요청이 매달리지 않게 짧게, 실패하면 재폴링)
- 소프트 캡: **~90초** 경과 후 자동 폴링 중단 → **수동 재시도 버튼** + "최대 1분 정도 걸릴 수 있어요" 안내
- 상태 구분: `checking`(임계 전) · `waking`(오버레이 표시, 폴링 중) · `ready` · `error`(오프라인/서버오류 → 재시도)

## D. 아키텍처 / 데이터 흐름

```
앱/페이지 진입 → 워업 게이트: GET /api/health 폴링
  ├─ ~1.2초 내 200      → 오버레이 없이 바로 앱 진입
  └─ 임계값 초과(콜드)  → 전체화면 워업 오버레이(안심문구 + 경과)
                          폴링 계속 → 200 → 오버레이 해제 → 앱
  └─ 소프트 캡 초과/오류 → 재시도 버튼 + 안내 문구 (무한 스피너 금지)
```

**컴포넌트 경계**: 백엔드 health는 읽기 전용·무의존성. 클라이언트 게이트는 준비 상태만 관리(비즈니스 로직 없음). 준비 완료 후 기존 화면을 그대로 렌더.

## E. 작업 항목

### E-1. 백엔드 (Spring, `skill-sim-project/backend`)
1. `controller/HealthController.java` 신설: `GET /api/health` → `{"status":"ok"}` 200 즉응. 데이터/DB 조회 없음. 기존 컨트롤러들과 동일한 `@CrossOrigin` 오리진 설정 적용.
2. (선택) 테스트: 200 및 바디 검증하는 슬라이스/단위 테스트.
   > **주의**: `ScoreController` 등 methodology 작업이 만진 파일과 충돌하지 않도록 **새 파일로만** 추가한다. 기존 컨트롤러를 수정하지 말 것.

### E-2. 프론트엔드 (Next.js, `skill-sim-project/frontend`)
1. `lib/api.ts`: `checkHealth()` 추가(`GET /api/health`, 짧은 타임아웃). — methodology가 이 파일을 수정했으므로 **함수만 추가**하고 기존 코드는 보존.
2. `lib/useBackendWarmup.ts` (신규): 진입 시 폴링, C장 파라미터대로 상태(`checking/waking/ready/error`)와 경과·재시도 노출.
3. `components/WakeUpOverlay.tsx` (신규): 전체화면 블로킹 오버레이. 안심 메시지·스피너/경과·(에러 시)재시도 버튼. 기존 다크 그라디언트 톤 유지.
4. `app/page.tsx`: 최상위에서 `useBackendWarmup` 게이트. `ready` 아니고 임계 초과면 `WakeUpOverlay` 렌더, 그 외 기존 탭 UI. — methodology가 이 파일에 methodology 탭을 추가했으므로 **그 구조를 보존한 채 게이트만 감싼다.**
5. `locales/translations.ts`: 워업 관련 문자열 **5개국어** 추가(누락 금지). 기존 methodology 키 보존.

### E-3. 앱 (Android/Compose, `skill-sim-project/android`)
1. `data/api/SkillApi.kt`: `@GET("/api/health")` 추가(함수만 추가, 기존 보존).
2. `data/model/HealthModels.kt` (신규): health 응답 모델.
3. `data/repository/SkillRepository.kt`: health 체크 메서드 추가.
4. `ui/warmup/WarmupViewModel.kt` + `ui/warmup/WakeUpScreen.kt` (신규): 진입 폴링 게이트 + 전체화면 워업 컴포저블.
5. `MainActivity.kt`: 준비 전 게이트 — `ready` 아니고 임계 초과면 `WakeUpScreen`, 그 외 기존 탭 UI. — methodology 탭 배선을 보존한 채 감쌀 것.
6. `i18n/Strings.kt`: 워업 문자열 **5개국어** 추가(기존 methodology 키 보존).

## F. Definition of Done / 검증

1. `GET /api/health` 200 즉응(웜 상태에서 빠름).
2. 웜스타트(백엔드 이미 기동): 오버레이가 뜨지 않고 곧바로 앱 진입(깜박임 없음).
3. 콜드스타트: 임계값 후 전체화면 워업 화면 표시 → 준비되면 자동 해제 → 정상 진입.
4. 오류/장기 지연: 무한 스피너 없이 재시도 경로 제공.
5. i18n: 워업 문자열 5개국어 누락/하드코딩 없음(웹·앱 both).
6. 빌드/테스트: 백엔드 `:test`, 프론트 빌드, 안드로이드 `:app:testDebugUnitTest`·`:app:assembleDebug` 통과. (**agy는 빌드 금지 — Claude가 통합·빌드·검증**)

## G. 진행 상태 체크리스트

- ✅ E-1 백엔드 `/api/health` 엔드포인트
- ✅ E-2 프론트 워업 게이트/오버레이 + i18n
- ✅ E-3 앱 워업 게이트/화면 + i18n
- ✅ F 검증 (빌드/테스트 통과 — 웜/콜드/오류 UX 시나리오는 실기기/런타임 수동 확인 권장)

## H. 진행 로그

| 날짜 | 내용 |
|------|------|
| 2026-07-02 | 계획 수립. 브레인스토밍으로 선제적 워업·지연 후 전체화면 블로킹·A안(health+폴링) 확정. methodology 작업과 파일 충돌 방지를 위해 별도 파일로 분리하고, methodology 완료 후 착수하기로 결정. 구현은 agy 서브에이전트 위임 예정. |
| 2026-07-02 | methodology 완료·빌드 검증 후 착수. agy가 E-1~E-3 구현: 백엔드 `GET /api/health`(+슬라이스 테스트), 프론트 `checkHealth()`/`useBackendWarmup`/`WakeUpOverlay`/page.tsx 게이트, 앱 `HealthModels`/`WarmupViewModel`/`WakeUpScreen`/RivalsSkillSimApp 게이트, 워업 문자열 5개국어(웹·앱). §C 파라미터(임계 1.2s·폴링 2s·타임아웃 8s·소프트캡 90s) 반영. 검증 통과: 백엔드 `:test`, 프론트 `build`, 안드로이드 `:app:testDebugUnitTest`·`:app:assembleDebug`. |
