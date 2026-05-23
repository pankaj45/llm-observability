# Phase 6 Production Validation

## Purpose

This checklist captures the validation that must be completed before a Phase 6 production rollout. Live container-backed dependency testing is intentionally manual for the current implementation pass.

## Non-Live Checks

```text
npm run contracts
mvn -pl services/inference-gateway test
mvn -pl services/analytics-query test
npm run typecheck --workspace apps/web
npm run build --workspace apps/web
git diff --check
```

## Docker Image Checks

Build each image and confirm it starts as a non-root process with health endpoints exposed:

```text
docker build -f services/inference-gateway/Dockerfile -t llm-observability/inference-gateway:phase-06 .
docker build -f services/analytics-query/Dockerfile -t llm-observability/analytics-query:phase-06 .
docker build -f services/ingestion-worker/Dockerfile -t llm-observability/ingestion-worker:phase-06 .
docker build -f apps/web/Dockerfile -t llm-observability/web:phase-06 .
```

## Manual Live Container-Backed Checks

- Run PostgreSQL, Kafka, Redis, and ClickHouse through Docker Compose.
- Apply PostgreSQL and ClickHouse migrations.
- Verify inference gateway PostgreSQL/Redis/Kafka wiring.
- Verify ingestion worker Kafka/PostgreSQL/ClickHouse wiring.
- Verify analytics query ClickHouse reads.
- Verify the web dashboard sends bearer tokens to analytics APIs.
- Verify missing or invalid bearer tokens are rejected when `SECURITY_ENABLED=true`.

## Helm Checks

Render staging and production values before applying:

```text
helm template llm-observability infra/helm/llm-observability-platform -f infra/helm/llm-observability-platform/values-staging.yaml
helm template llm-observability infra/helm/llm-observability-platform -f infra/helm/llm-observability-platform/values-production.yaml
```

## Rollback Notes

- Roll back application images through Helm release history.
- Stop rollout if migration jobs fail.
- Pause Kafka consumers before replay or schema recovery.
- Rotate exposed secrets before redeploying if a secret leak is suspected.
