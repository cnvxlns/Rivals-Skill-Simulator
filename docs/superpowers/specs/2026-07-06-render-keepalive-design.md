# Render 백엔드 Keep-Alive 설계

날짜: 2026-07-06

## 배경

백엔드(`https://rivals-skill-random-generator-api.onrender.com`)는 Render 무료 플랜에 배포되어 있다. 무료 플랜은 15분간 인바운드 요청이 없으면 인스턴스를 잠재우고, 다음 요청 시 콜드스타트로 수십 초가 걸린다. 외부에서 15분 이내 간격으로 주기적 요청을 보내 항상 깨어 있게 한다.

## 목표

- 백엔드가 Render 유휴 타임아웃(15분)으로 잠들지 않게 한다.
- 저장소를 장기간 방치해도(60일 무커밋) keep-alive가 멈추지 않게 한다.
- 백엔드가 다운되면 GitHub 워크플로 실패 알림(이메일)으로 알 수 있게 한다.

## 비목표

- 정식 업타임 모니터링/알림 시스템 구축 (워크플로 실패 알림으로 갈음)
- 백엔드 코드 변경 (기존 `GET /api/health` 엔드포인트를 그대로 사용)

## 설계

새 파일 1개: `.github/workflows/keep-alive.yml`

### 트리거

- `schedule`: cron `*/10 * * * *` (10분마다). GitHub cron은 몇 분 지연될 수 있으나 Render의 15분 한도 대비 여유가 있다.
- `workflow_dispatch`: 수동 실행으로 즉시 테스트 가능.

### 잡 구성 (단일 잡, 스텝 2개)

1. **Ping**: `curl`로 `https://rivals-skill-random-generator-api.onrender.com/api/health` 호출.
   - `--fail`로 HTTP 오류 시 스텝 실패 → 워크플로 실패 → GitHub 이메일 알림.
   - 콜드스타트 대비 `--retry 3 --retry-delay 20 --max-time 60` (잠들어 있던 경우 이 요청이 깨우는 역할을 하며, 첫 응답까지 수십 초 걸릴 수 있음).
2. **워크플로 자동 재활성화**: public 저장소는 60일간 저장소 활동이 없으면 scheduled workflow가 자동 비활성화되며, 워크플로 실행 자체는 활동으로 집계되지 않는다. 이를 막기 위해 매 실행마다 GitHub API `PUT /repos/{owner}/{repo}/actions/workflows/keep-alive.yml/enable`을 자기 자신에게 호출해 60일 타이머를 리셋한다.
   - `gh api` 사용, 잡 권한에 `actions: write` 필요 (`GITHUB_TOKEN`).
   - 이미 활성화된 워크플로에 호출해도 무해하며, 호출 빈도(시간당 6회)는 API 한도에 전혀 문제없다.

### 오류 처리

- ping 실패(재시도 소진) 시 워크플로가 실패로 표시되고 GitHub이 저장소 소유자에게 알림을 보낸다.
- 재활성화 API 호출 실패는 ping 성공과 무관하게 스텝 실패로 드러난다.

## 제약/전제

- Render 무료 플랜 월 750 인스턴스 시간: 서비스 1개를 24시간 유지하면 약 730시간으로 한도 내.
- 이 저장소는 public이어야 60일 규칙이 적용되며, 재활성화 스텝은 public/private 모두에서 무해하다.

## 검증

- `workflow_dispatch`로 수동 실행하여 두 스텝 모두 성공하는지 확인.
- Actions 탭에서 schedule 실행이 주기적으로 도는지 확인.
