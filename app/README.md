# app

Rivals-Skill-Simulator의 Expo(React Native) 앱입니다. 웹과 안드로이드를 한 코드베이스로 빌드합니다.
프로젝트 소개, 전체 스택 실행, API 명세는 [루트 README](../README.md)에 있습니다.

## 로컬 실행

Node.js 20+와 npm이 필요하고, 백엔드가 `http://localhost:8080`에 떠 있어야 합니다.

```bash
echo 'EXPO_PUBLIC_API_URL=http://localhost:8080' > .env.local
npm install
npm run web        # http://localhost:8081, 안드로이드는 npm run android
```

**`EXPO_PUBLIC_API_URL`을 빼면 API 호출이 실패합니다.** 값이 없으면 `src/lib/api.ts`가 상대경로 `/api`로
요청하는데, 개발 서버에는 그 경로를 받아 줄 프록시가 없습니다. 상대경로는 nginx가 `/api`를 백엔드로
넘겨 주는 Docker 스택(`make up`)을 위한 동작입니다. 루트에서 `make app`으로 띄우면 이 값을 대신 넘겨 줍니다.

안드로이드 에뮬레이터에서는 `localhost`가 에뮬레이터 자신을 가리키므로 `http://10.0.2.2:8080`을 씁니다.

## 환경변수

`EXPO_PUBLIC_` 접두사가 붙은 값은 빌드 시점에 클라이언트 번들에 들어갑니다. 비밀값을 넣지 마세요.

| 변수 | 쓰는 곳 | 비워 두면 |
|---|---|---|
| `EXPO_PUBLIC_API_URL` | `src/lib/api.ts` — 백엔드 주소 | 상대경로 `/api`. 네이티브 빌드에서는 반드시 절대 URL이 필요합니다 |
| `EXPO_PUBLIC_APK_URL` | `src/components/ApkInstallButton.tsx` — 웹의 APK 설치 버튼 | 버튼을 숨깁니다 |

`.env`와 `.env.*`는 git과 Docker 빌드 컨텍스트에서 모두 제외됩니다.

## 폴더 구조

```
app/
├── src
│   ├── app          # expo-router 라우트. 화면은 index.tsx 하나이고 탭은 그 안에서 바뀝니다
│   ├── views        # 탭 화면: 점수표 · 계산기 · 덱 · 산정 방식
│   ├── components   # 공용 UI, 야구장 그림, 수식(KaTeX) 렌더러
│   ├── lib          # API 클라이언트, 인증, 카드 규칙, 화면별 훅
│   ├── locales      # 문자열
│   ├── theme        # 색·간격 토큰
│   └── types        # 백엔드 요청·응답 타입
├── Dockerfile       # 웹 정적 빌드 → nginx
├── nginx.conf       # 정적 파일 서빙 + /api 프록시
├── vercel.json      # Vercel 웹 배포 설정
└── eas.json         # EAS 안드로이드 빌드 프로필
```

## 배포

| 대상 | 방식 | 언제 |
|---|---|---|
| 웹 | Vercel이 `expo export -p web` 결과물(`dist/`)을 배포 | `deploy` 브랜치에 push |
| 안드로이드 JS 변경 | EAS Update(OTA). 앱을 재시작하면 받습니다 | `deploy`에 push되면서 `src/`, `assets/`, `app.json`이 바뀌었을 때 |
| 안드로이드 네이티브 변경 | EAS Build로 APK를 새로 빌드 | Actions에서 `EAS Build (APK)`를 수동 실행 |

새 Expo 모듈, 권한 추가, SDK 업그레이드는 OTA로 전달되지 않으므로 APK를 다시 빌드해야 합니다.
APK 설치 URL은 빌드마다 바뀌므로 Vercel의 `EXPO_PUBLIC_APK_URL`도 새 주소로 바꾸고 재배포합니다.

## 개발 참고

- Expo SDK 57을 씁니다. 코드를 쓰기 전에 [버전별 문서](https://docs.expo.dev/versions/v57.0.0/)를 확인하세요.
- 경로 별칭 `@/`는 `src/`를, `@/assets/`는 `assets/`를 가리킵니다.
