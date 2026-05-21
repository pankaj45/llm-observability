SHELL := /bin/sh

.PHONY: contracts test test-backend test-frontend build-backend build-frontend dev down

contracts:
	@node scripts/validate-contracts.mjs

test: contracts test-backend test-frontend

test-backend:
	@mvn test

test-frontend:
	@npm run typecheck --workspace apps/web

build-backend:
	@mvn -DskipTests package

build-frontend:
	@npm run build --workspace apps/web

dev:
	@docker compose -f infra/docker-compose/docker-compose.yml up --build

down:
	@docker compose -f infra/docker-compose/docker-compose.yml down --remove-orphans

