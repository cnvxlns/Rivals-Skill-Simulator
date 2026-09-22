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
NGROK   := docker compose -f docker-compose.yml -f docker-compose.ngrok.yml

# 윈도우에서는 ./gradlew(셸 스크립트)가 아니라 gradlew.bat을 써야 한다.
ifeq ($(OS),Windows_NT)
GRADLEW := ./gradlew.bat
else
GRADLEW := ./gradlew
endif

.DEFAULT_GOAL := help

.PHONY: help ensure-env up up-d down restart logs ps build rebuild clean nuke db-dump tunnel-up tunnel-down ngrok-up ngrok-down backend app validate-data

# --- 환경 파일 --------------------------------------------------------------

# APP_JWT_SECRET을 비워 두면 백엔드가 부팅마다 임의 서명 키를 만든다. 개발은
# 되지만 컨테이너를 다시 띄울 때마다 발급된 토큰이 전부 죽어 로그인이 풀린다.
# 그래서 스택을 올리기 전에 한 번 만들어 .env에 박아 둔다. .env는 gitignore
# 대상이라 커밋되지 않는다.
#
# 이미 값이 있으면 건드리지 않는다. 개인 서버에 쓰던 키를 덮어쓰면 그쪽 로그인이
# 전부 풀리기 때문이다.
#
# openssl은 맥·리눅스에 기본으로 있고 Git for Windows에도 딸려 오지만 확실하지
# 않다. 없으면 docker로 떨어진다. 어차피 docker 없이는 이 타깃을 부를 일이 없다.
ensure-env:
	@if [ ! -f .env ]; then \
		[ -f .env.example ] && cp .env.example .env || : > .env; \
		echo "made .env"; \
	fi
	@if grep -q '^APP_JWT_SECRET=.\+' .env; then \
		:; \
	else \
		secret=$$(openssl rand -base64 48 2>/dev/null \
			|| docker run --rm alpine sh -c 'head -c 48 /dev/urandom | base64 | tr -d "\n"'); \
		if [ -z "$$secret" ]; then \
			echo "could not generate APP_JWT_SECRET. install openssl or start docker."; \
			exit 1; \
		fi; \
		grep -v '^APP_JWT_SECRET=' .env > .env.tmp || :; \
		echo "APP_JWT_SECRET=$$secret" >> .env.tmp; \
		mv .env.tmp .env; \
		echo "set APP_JWT_SECRET in .env"; \
	fi

# --- 도커 전체 스택 ---------------------------------------------------------

# 로컬 개발 기본값. docker-compose.override.yml이 자동으로 얹혀 8081(앱)과
# 8080(API)이 호스트에 열린다. 포그라운드라 로그가 그대로 보이고 Ctrl+C로 내려간다.
# 첫 빌드는 오래 걸린다. 컨테이너 안에서 Gradle/npm 의존성을 처음부터 받고
# 백엔드 이미지는 빌드 중에 전체 테스트까지 돌린다.
up: ensure-env
	$(COMPOSE) up --build

# 같은 스택을 백그라운드로. 로그는 make logs.
up-d: ensure-env
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

# 컨테이너/네트워크/로컬 이미지를 정리한다. 볼륨은 건드리지 않는다.
# 예전에는 --volumes가 붙어 있었다. 상태가 없던 시절에는 안전했지만 이제 계정과 덱이
# pgdata에 살기 때문에 그대로 두면 데이터가 지워진다. 볼륨까지 지우려면 make nuke.
clean:
	$(COMPOSE) down --remove-orphans --rmi local

# 볼륨까지 전부 삭제한다. 계정과 덱이 함께 사라지며 되돌릴 수 없다.
nuke:
	@printf 'pgdata 볼륨을 삭제한다. 계정과 덱이 모두 사라진다. 계속하려면 yes 입력: '
	@read answer && [ "$$answer" = "yes" ] || (echo "취소했다."; exit 1)
	$(COMPOSE) down --remove-orphans --volumes --rmi local

# DB를 파일로 받아 둔다. 개인 서버에는 백업이 이것뿐이다.
db-dump:
	@mkdir -p backups
	$(COMPOSE) exec -T db pg_dump -U $${POSTGRES_USER:-skillsim} $${POSTGRES_DB:-skillsim} \
		> backups/skillsim-$$(date +%Y%m%d-%H%M%S).sql
	@ls -lh backups | tail -1

# --- 개인 서버 (Cloudflare 터널) --------------------------------------------

# override를 빼고 터널 오버레이만 얹는다. .env에 TUNNEL_TOKEN이 있어야 한다.
tunnel-up:
	$(TUNNEL) up -d --build

tunnel-down:
	$(TUNNEL) down --remove-orphans

# --- 개인 서버 (ngrok) ------------------------------------------------------

# Cloudflare 도메인이 없을 때. .env에 NGROK_AUTHTOKEN과 NGROK_DOMAIN이 있어야 한다.
ngrok-up:
	$(NGROK) up -d --build

ngrok-down:
	$(NGROK) down --remove-orphans

# --- 도커 없이 직접 ---------------------------------------------------------

# JDK 17 필요.
backend:
	cd backend && $(GRADLEW) bootRun

# Node 20+ 필요. 백엔드가 8080에 떠 있어야 API 호출이 성공한다.
# 백엔드 주소를 꼭 넘겨야 한다. 비워 두면 앱이 상대경로 /api로 요청하는데, 그 경로를
# 백엔드로 넘겨 주는 건 도커 스택의 nginx뿐이라 개발 서버에서는 전부 실패한다.
# 다른 백엔드를 보려면 make app API_URL=https://... 로 덮어쓴다.
API_URL ?= http://localhost:8080
app:
	cd app && npm install && EXPO_PUBLIC_API_URL='$(API_URL)' npm run web

# --- 데이터 ------------------------------------------------------------------

# 스킬 CSV의 불변식을 본다. 백엔드 로더는 깨진 행을 경고만 남기고 넘어가므로
# 잘못된 데이터가 런타임에는 "조용히 틀린 점수"로만 드러난다. CSV를 고쳤으면
# 커밋 전에 한 번 돌린다. CI도 같은 것을 돌린다(validate-data.yml).
# 파이썬 3.12+ 필요. 의존성은 없다.
validate-data:
	python3 tools/validate_skill_csv.py --warn
	python3 tools/validate_deck_data.py --warn
	cd tools && python3 -m unittest discover -p 'test_*.py'

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
	@echo "  make clean         down + remove local images (keeps the database)"
	@echo "  make nuke          clean + DELETE the database volume (destroys accounts/decks)"
	@echo "  make db-dump       pg_dump the database into backups/"
	@echo ""
	@echo "  make tunnel-up     cloudflare tunnel stack, needs TUNNEL_TOKEN in .env"
	@echo "  make tunnel-down   stop tunnel stack"
	@echo "  make ngrok-up      ngrok stack, needs NGROK_AUTHTOKEN and NGROK_DOMAIN in .env"
	@echo "  make ngrok-down    stop ngrok stack"
	@echo ""
	@echo "  make backend       run Spring Boot without docker, needs JDK 17"
	@echo "  make app           run Expo web without docker, needs Node 20+ (API_URL=... to change backend)"
	@echo ""
	@echo "  make validate-data check the skill CSVs before committing"
