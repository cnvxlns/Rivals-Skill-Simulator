# 1. Build Stage
FROM node:20-alpine AS build
WORKDIR /app

# 의존성 매니페스트를 먼저 복사해 캐시 레이어를 만든다.
# src가 바뀌어도 이 레이어는 재사용되므로 매번 npm ci를 다시 돌리지 않는다.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .

# EXPO_PUBLIC_API_URL을 넘기지 않는다. 값이 없으면 src/lib/api.ts가 상대경로(/api)로
# 떨어지고, nginx가 같은 오리진에서 /api를 백엔드로 프록시한다.
# 덕분에 CORS 설정도, 빌드 시점에 백엔드 주소를 박아 넣는 일도 필요 없다.
# app/.env는 .dockerignore로 제외한다. 안 그러면 거기 적힌 주소가 번들에 구워진다.
RUN npx expo export -p web

# 2. Serve Stage
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# nginx 이미지는 이미 비루트로 워커를 돌린다. 헬스체크는 compose에서 정의한다.
EXPOSE 80
