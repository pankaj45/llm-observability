# Phase 06 Specification: Production Hardening

## Implementation Status

Status as of 2026-05-23: implementation in progress. Phase 6 turns the Phase 5 platform into a deployable, operable, and security-reviewed production baseline. Docker image validation is in scope for the implementation pass. Live container-backed integration validation for PostgreSQL, Kafka, Redis, and ClickHouse is documented for manual execution.

## Goals

- Make the platform deployable through cloud-neutral Kubernetes and Helm.
- Add an OIDC/JWT authentication and authorization boundary for APIs and the operator UI.
- Add production-grade secrets, configuration, probes, resource controls, rollout, rollback, and migration practices.
- Validate Docker images and live container-backed dependencies.
- Add SLO dashboards, alert rules, and runbooks for critical user journeys.
- Add baseline retention, redaction, and audit controls for sensitive inference observability data.
- Preserve clean architecture and provider/data boundaries while hardening runtime behavior.

## Scope

- Helm chart structure for:
  - web app
  - inference gateway
  - analytics query service
  - ingestion worker
  - OpenTelemetry collector
  - Prometheus and Grafana integration hooks
- Environment overlays for local-like, staging, and production values.
- Kubernetes manifests rendered through Helm:
  - Deployments
  - Services
  - ConfigMaps
  - Secret references
  - ServiceAccounts
  - readiness, liveness, and startup probes
  - resource requests and limits
  - PodDisruptionBudgets
  - HorizontalPodAutoscalers where scaling behavior is known
  - NetworkPolicies for service-to-service and dependency access
- OIDC/JWT validation for backend services.
- Operator UI authentication integration with OIDC and browser-to-backend token propagation.
- Tenant/project authorization checks derived from JWT claims and existing request scope.
- Local development mock OIDC issuer or signed test-token workflow.
- Secret handling for provider API keys, database credentials, Kafka credentials, Redis credentials, OIDC settings, and telemetry exporters.
- Docker image validation:
  - image builds for all services
  - non-root runtime checks
  - configuration smoke checks
  - vulnerability/configuration scanning where tooling is available
- Live container-backed integration validation:
  - inference gateway with PostgreSQL, Redis, and Kafka
  - ingestion worker with Kafka, PostgreSQL, and ClickHouse
  - analytics query service with ClickHouse
  - web app with analytics and inference APIs
- Production migrations:
  - controlled migration jobs
  - rollback notes
  - schema compatibility checks
- Observability hardening:
  - SLO definitions
  - Prometheus alert rules
  - Grafana dashboards
  - runbooks
  - trace/log/metric redaction checks
- Data protection baseline:
  - retention configuration
  - protected-content redaction checks
  - audit logs for authentication, authorization denial, cancellation, and admin-sensitive configuration actions.

## Non-Goals

- No cloud-provider provisioning module.
- No marketplace or public SaaS onboarding.
- No multi-region active-active deployment.
- No custom identity provider or built-in username/password auth system.
- No full compliance certification package.
- No advanced legal hold, data residency, or customer-managed-key workflow.
- No chargeback billing or payment workflow.
- No arbitrary SQL workbench.

## Assumptions

- Production deployment runs on Kubernetes and is installed through Helm.
- Runtime dependencies can be managed services or separately installed in-cluster dependencies, but Phase 6 does not provision them.
- Authentication is OIDC-compatible JWT validation.
- Local development uses a mock issuer or signed development tokens.
- Tenant/project authorization can be represented by JWT claims without changing the current tenant/project data model.
- Kubernetes Secrets are the baseline implementation; values and chart structure remain compatible with External Secrets, Vault, or cloud secret managers.
- Existing raw prompt/completion content remains in protected PostgreSQL `conversation_message` records and is not sent to Kafka, metrics, traces, or logs.
- Live provider testing remains explicit and credential-gated; Phase 6 validates platform wiring without requiring real Gemini calls by default.

## Resolved Decisions

1. Phase 6 standardizes on OIDC-compatible JWT validation for API and operator UI authentication.
2. Phase 6 does not implement a built-in username/password identity system.
3. Phase 6 deployment artifacts are cloud-neutral Helm charts and Kubernetes manifests.
4. Kubernetes Secrets are the baseline secret mechanism; External Secrets/Vault compatibility is required in chart values and documentation.
5. Docker image validation is in scope; live container-backed PostgreSQL/Kafka/Redis/ClickHouse tests are documented for manual execution.
6. Baseline retention configuration, redaction verification, and audit logging are in scope.
7. Advanced data residency, legal export, legal hold, and customer-managed keys are deferred.
8. Production migrations run as controlled jobs, not as implicit app startup side effects.
9. Alerts are symptom-first and mapped to runbooks.
10. Phase 6 hardening must not introduce raw prompt/completion leakage into logs, metrics, traces, Kafka events, or analytics APIs.

## Ambiguities

Resolved by approved defaults:

- Auth provider: OIDC/JWT with local mock issuer.
- Deployment target: generic Kubernetes and Helm.
- Secrets strategy: Kubernetes Secrets baseline with External Secrets/Vault-compatible chart shape.
- Production validation: include Docker image validation and live container-backed dependency tests.
- Compliance baseline: implement retention, redaction, and audit controls; defer advanced compliance workflows.

Remaining future ambiguities:

- Exact enterprise identity provider configuration by customer.
- Managed service choices and cloud-specific ingress/certificate implementation.
- Data residency and regional deployment obligations.
- Formal compliance frameworks and evidence requirements.
- Production SLO targets after load-test calibration.

## API Contract Impact

Existing business APIs keep their paths and request/response shapes, but Phase 6 adds authentication and authorization requirements.

Required API behavior:

- All non-health business APIs require a bearer token.
- JWTs must be validated for issuer, audience, expiration, signature, and required claims.
- Tenant/project request parameters or body fields must match authorized claims.
- Authorization failures return the deterministic error envelope.
- Health, readiness, liveness, and Prometheus endpoints remain controlled by deployment/network policy and do not expose secrets or raw content.

Recommended JWT claims:

- `sub`
- `iss`
- `aud`
- `exp`
- `iat`
- `tenant_ids` or `tenants`
- `project_ids` or `projects`
- `roles`
- `scope`

Required roles/scopes:

- `inference:write`
- `inference:read`
- `conversation:read`
- `conversation:write`
- `analytics:read`
- `admin:read`

OpenAPI updates during implementation must document bearer auth schemes and endpoint-level security requirements.

## Event Schema Impact

No new Kafka topics or lifecycle event names are required.

Producer and consumer requirements:

- Lifecycle events continue to exclude raw prompt/completion content.
- Events may include authenticated subject, actor, or service principal identifiers only when they are non-sensitive and stable enough for audit correlation.
- Event schema changes must be backward compatible or versioned.
- Dead-letter envelopes remain deferred unless implementation chooses to add operational replay tooling.

## Data Model Impact

Expected PostgreSQL impact:

- audit event table for security and admin-sensitive actions
- optional retention policy table or configuration-backed retention registry
- optional tenant/project auth mapping if JWT claims cannot directly map to request scope

Expected ClickHouse impact:

- no required schema change for lifecycle facts
- optional retention TTL configuration or documented operational policy

Expected Redis impact:

- no required schema change
- confirm active stream and cancellation keys have bounded TTLs

Expected Kafka impact:

- retention and topic configuration documentation
- consumer lag and replay runbook coverage

## Security and Privacy Impact

Phase 6 must implement:

- OIDC/JWT validation.
- Tenant/project authorization at every business API boundary.
- Least-privilege Kubernetes service accounts.
- Secret references instead of inline secret values in rendered production manifests.
- NetworkPolicies for application-to-dependency access.
- Redaction tests for logs, traces, metrics, Kafka payloads, and analytics responses.
- Audit logs for:
  - authentication success/failure when observable at the app boundary
  - authorization denial
  - inference cancellation
  - provider credential configuration changes where represented in platform config
  - retention policy changes
- Retention configuration for protected conversation content and analytics facts.

Phase 6 must not log or expose:

- raw prompts
- raw completions
- provider API keys
- authorization headers
- refresh tokens or access tokens
- database passwords
- Kafka credentials
- Redis credentials

## Observability Impact

SLO candidates for Phase 6 implementation:

| Journey | Initial SLO |
| --- | --- |
| Inference gateway availability | 99.9% successful non-provider-error requests over 30 days |
| Streaming first event latency | p95 under 1000 ms excluding provider latency where distinguishable |
| Cancellation acknowledgement | p95 under 500 ms after gateway receives cancellation |
| Ingestion freshness | p95 under 30 seconds from Kafka publish to ClickHouse visibility |
| Dashboard summary query latency | p95 under 2 seconds for default windows |
| Authentication decision latency | p95 under 100 ms for cached JWKS validation |

Required dashboards:

- service overview
- inference gateway and streaming
- ingestion pipeline
- analytics query
- provider reliability
- authentication and authorization
- SLO and alert overview

Required alerts:

- API availability burn rate
- API p95 latency burn rate
- SSE stream failure anomaly
- cancellation acknowledgement latency
- Kafka consumer lag sustained above threshold
- ClickHouse ingestion/query failure rate
- PostgreSQL connection saturation
- Redis operation failure rate
- OIDC JWKS refresh failures
- authorization-denial anomaly
- OTel collector scrape/export failure

Each alert must link to a runbook.

## Deployment Impact

Phase 6 Helm charts must support:

- image repository, tag, pull policy, and pull secrets
- environment-specific values
- ConfigMap and Secret references
- mounted provider/API credentials
- service ports
- probes
- resource requests and limits
- autoscaling values
- PodDisruptionBudgets
- NetworkPolicies
- service monitors or Prometheus annotations
- OTLP exporter configuration
- migration jobs
- rollback notes

The production chart must not require Docker Compose.

## Migration and Rollback Strategy

- Database and ClickHouse migrations run as reviewed jobs before application rollout.
- Backward-compatible schema changes deploy before code that depends on them.
- Rollback preserves compatibility with in-flight Kafka events and old schema versions.
- Failed migration jobs block rollout.
- Rollback runbooks must include:
  - application rollback
  - migration failure response
  - Kafka consumer pause/resume
  - analytics replay notes
  - secret rotation recovery

## Test Strategy

Required test layers:

- unit tests for auth claim parsing and scope decisions
- WebFlux security tests for all business endpoints
- contract validation for bearer auth documentation
- redaction tests for logs/traces/events/responses
- Helm template tests
- Kubernetes manifest policy checks
- Docker image build and smoke tests for every service
- live container-backed tests:
  - PostgreSQL migrations and R2DBC connectivity
  - Redis active stream/cancellation state
  - Kafka lifecycle publish/consume
  - ClickHouse ingestion and analytics query
  - web-to-backend request path
- load tests for key SLOs using deterministic seeded data
- chaos/failure-mode tests:
  - Kafka unavailable
  - Redis unavailable
  - ClickHouse unavailable
  - PostgreSQL unavailable
  - OIDC JWKS unavailable
  - provider timeout

## Acceptance Criteria

- Phase 6 spec and ADR are linked from README, roadmap, and ADR index.
- Helm charts render valid manifests for staging and production values.
- Every service deploys with probes, resource requests/limits, service account, secret references, telemetry config, and network policy.
- Business APIs reject missing/invalid JWTs and enforce tenant/project authorization.
- Local development supports a mock OIDC issuer or signed development token workflow.
- Docker images build and pass smoke checks.
- Container-backed PostgreSQL, Kafka, Redis, and ClickHouse integration tests pass.
- Migration jobs are documented and tested.
- SLO dashboards and alert rules exist for critical journeys.
- Every alert has a runbook.
- Retention configuration exists for protected content and analytics facts.
- Redaction tests prove prompts, completions, credentials, and authorization headers are not logged or emitted to metrics/traces/events.
- README, deployment strategy, observability strategy, architecture docs, and setup instructions are updated.

## Risks

- OIDC provider differences can affect claim shape, key rotation, and audience validation.
- Helm chart complexity can grow quickly if managed dependency provisioning is mixed into application deployment.
- NetworkPolicies can break telemetry or dependency access if defaults are too strict.
- Retention jobs can delete data needed for support if policy defaults are not explicit.
- Load-test results may force resource limit and SLO changes.
- Live container-backed tests can be slower and more resource intensive than prior phase tests.
