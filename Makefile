.PHONY: env infra run run-direct up down reset logs psql test check format

env:
	test -f .env || cp .env.example .env

.env:
	@echo 'There is no .env file. Run make env to make one.' >&2; exit 1

infra: .env
	docker compose up --detach --wait

run: infra
	set -a; . ./.env; set +a; ./gradlew bootRun

run-direct: .env
	set -a; . ./.env; set +a; ./gradlew bootRun

up: .env
	docker compose --profile app up --detach --build --wait

down:
	docker compose --profile app down

reset:
	@printf 'Stop all containers and delete the PostgreSQL data volume? [y/N] '; read answer; \
	if [ "$$answer" = y ]; then docker compose --profile app down --volumes; else echo 'Kept the data volume.'; fi

logs:
	docker compose --profile app logs --follow

psql:
	docker compose exec postgres psql --username=westpac_app --dbname=westpac

test:
	./gradlew test

check:
	./gradlew check

format:
	./gradlew spotlessApply
