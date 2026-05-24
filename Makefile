SHELL := /bin/sh

.PHONY: contracts test test-backend test-frontend build-backend build-frontend dev dev-ready ready down

# Validate OpenAPI and Kafka event schemas
contracts:
	@node scripts/validate-contracts.mjs

# Run contracts + backend + frontend checks
test: contracts test-backend test-frontend

# Run all Maven service tests
test-backend:
	@mvn test

# TypeScript typecheck for the web app
test-frontend:
	@npm run typecheck --workspace apps/web

# Build all backend JARs (skip tests)
build-backend:
	@mvn -DskipTests package

# Build the Next.js production bundle
build-frontend:
	@npm run build --workspace apps/web

# Start the full stack in the foreground (logs stream to terminal)
dev:
	@docker compose -f infra/docker-compose/docker-compose.yml up --build

# Start the full stack in the background, then wait until every service is healthy.
# Prints a URL banner and exits when the platform is ready to use.
dev-ready:
	@docker compose -f infra/docker-compose/docker-compose.yml up --build --detach
	@node scripts/wait-ready.mjs

# Poll service health endpoints until everything is up (use after `make dev` in another terminal).
# Exits 0 when all services are healthy; exits 1 after a 3-minute timeout.
ready:
	@node scripts/wait-ready.mjs

# Stop all containers and remove orphans
down:
	@docker compose -f infra/docker-compose/docker-compose.yml down --remove-orphans

