.PHONY: env infra down reset logs psql

env:
	test -f .env || cp .env.example .env

infra:
	docker compose up --detach --wait

down:
	docker compose down

reset:
	@printf 'Stop all containers and delete the PostgreSQL data volume? [y/N] '; read answer; \
	if [ "$$answer" = y ]; then docker compose down --volumes; else echo 'Kept the data volume.'; fi

logs:
	docker compose logs --follow

psql:
	docker compose exec postgres psql --username=westpac_app --dbname=westpac
