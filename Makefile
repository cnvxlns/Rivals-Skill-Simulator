# Rivals-Skill-Simulator 개발 단축 명령.
#
# README의 실행 방법을 짧게 부르는 얇은 래퍼다. 새 실행 경로를 만들지 않고
# docker compose / gradlew / npm 명령을 그대로 감싸기만 한다.
#
#   make up     로컬 전체 스택 기동 -> http://localhost:8081
#   make help   전체 목록
#
# 셸을 sh로 못 박는다. 윈도우의 GNU make는 PATH에 sh.exe가 있으면 그걸 쓰고
# 없으면 cmd.exe로 떨어지는데, 그러면 같은 레시피가 PowerShell에서 실행할 때와
# Git Bash에서 실행할 때 다르게 동작한다. Git이 깔린 환경이면 sh.exe는 항상
# PATH에 있다(C:\Program Files\Git\usr\bin\sh.exe).
SHELL := sh

COMPOSE := docker compose
TUNNEL  := docker compose -f docker-compose.yml -f docker-compose.tunnel.yml

# 윈도우에서는 ./gradlew(셸 스크립트)가 아니라 gradlew.bat을 써야 한다.
ifeq ($(OS),Windows_NT)
GRADLEW := ./gradlew.bat
else
GRADLEW := ./gradlew
endif

.DEFAULT_GOAL := help

.PHONY: help up up-d down restart logs ps build rebuild clean tunnel-up tunnel-down backend app

# --- 도커 전체 스택 ---------------------------------------------------------

# 로컬 개발 기본값. docker-compose.override.yml이 자동으로 얹혀 8081(앱)과
# 8080(API)이 호스트에 열린다. 포그라운드라 로그가 그대로 보이고 Ctrl+C로 내려간다.
# 첫 빌드는 오래 걸린다. 컨테이너 안에서 Gradle/npm 의존성을 처음부터 받고
# 백엔드 이미지는 빌드 중에 전체 테스트까지 돌린다.
up:
	$(COMPOSE) up --build

# 같은 스택을 백그라운드로. 로그는 make logs.
up-d:
	$(COMPOSE) up --build -d

down:
	$(COMPOSE) down --remove-orphans

restart:
	$(MAKE) down
	$(MAKE) up-d

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

build:
	$(COMPOSE) build

# 캐시를 버리고 처음부터. 의존성이 꼬였을 때만 쓴다. 매우 오래 걸린다.
rebuild:
	$(COMPOSE) build --no-cache

# 컨테이너/네트워크/로컬 이미지까지 정리. 백엔드는 클래스패스 CSV만 읽고 상태가
# 없으므로 지웠다 다시 만들어도 잃을 데이터가 없다.
clean:
	$(COMPOSE) down --remove-orphans --volumes --rmi local

# --- 개인 서버 (Cloudflare 터널) --------------------------------------------

# override를 빼고 터널 오버레이만 얹는다. .env에 TUNNEL_TOKEN이 있어야 한다.
tunnel-up:
	$(TUNNEL) up -d --build

tunnel-down:
	$(TUNNEL) down --remove-orphans

# --- 도커 없이 직접 ---------------------------------------------------------

# JDK 17 필요.
backend:
	cd backend && $(GRADLEW) bootRun

# Node 20+ 필요. 백엔드가 8080에 떠 있어야 API 호출이 성공한다.
app:
	cd app && npm install && npm run web

# --- 도움말 ----------------------------------------------------------------

help:
	@echo "Rivals-Skill-Simulator"
	@echo ""
	@echo "  make up            full docker stack, foreground -> http://localhost:8081"
	@echo "  make up-d          full docker stack, detached"
	@echo "  make down          stop and remove containers"
	@echo "  make restart       down, then up-d"
	@echo "  make logs          follow logs"
	@echo "  make ps            container status"
	@echo "  make build         build images"
	@echo "  make rebuild       build images without cache"
	@echo "  make clean         down + remove volumes and local images"
	@echo ""
	@echo "  make tunnel-up     cloudflare tunnel stack, needs TUNNEL_TOKEN in .env"
	@echo "  make tunnel-down   stop tunnel stack"
	@echo ""
	@echo "  make backend       run Spring Boot without docker, needs JDK 17"
	@echo "  make app           run Expo web without docker, needs Node 20+"
