# ADR-0016: Phase 6 Production Hardening Boundary

## Context

Phase 6 moves the platform from feature-complete MVP workflows toward a production-ready baseline. Earlier phases intentionally deferred authentication, production deployment, Docker image validation, live container-backed integration validation, and advanced operational controls. By Phase 5, the platform has inference streaming, conversation continuity, lifecycle ingestion, ClickHouse analytics, and an operator UI. The remaining architectural boundary is how production identity, deployment, secrets, validation, and compliance controls should be introduced without coupling the platform to one cloud or identity vendor.

## Decision

Phase 6 will standardize on OIDC-compatible JWT validation for application authentication and authorization. The platform will not implement a built-in username/password identity provider. Local development will use a mock issuer or signed development tokens.

Deployment artifacts will be cloud-neutral Helm charts targeting Kubernetes. Phase 6 will not provision cloud infrastructure or managed dependencies. Charts will define application workloads, services, probes, resources, autoscaling, disruption budgets, secret references, network policies, telemetry configuration, and migration jobs.

Kubernetes Secrets will be the baseline secret mechanism. Chart values and documentation must remain compatible with External Secrets, Vault, or cloud secret managers for production installations.

Phase 6 will include Docker image validation and live container-backed integration tests for PostgreSQL, Kafka, Redis, and ClickHouse. These checks end the prior deferral of image and live dependency validation.

Baseline compliance controls are in scope: retention configuration, redaction verification, and audit logs for authentication/authorization and admin-sensitive actions. Advanced data residency, legal export, legal hold, compliance certification, and customer-managed keys remain deferred.

## Alternatives Considered

- Implement a built-in username/password identity provider.
- Choose a specific hosted identity provider.
- Create cloud-specific deployment modules for AWS, GCP, or Azure.
- Use Docker Compose as the production deployment target.
- Require External Secrets or Vault as the only supported secret mechanism.
- Continue deferring live dependency and image validation.
- Document compliance controls without implementing retention, redaction, or audit foundations.

## Tradeoffs

- OIDC/JWT keeps identity provider choice flexible, but claim mapping and JWKS behavior must be configurable and tested.
- Cloud-neutral Helm charts are portable, but cloud-specific ingress, certificates, and managed dependency provisioning remain customer/environment work.
- Kubernetes Secrets are universally available, but production operators should still prefer external secret managers for rotation and auditability.
- Live container-backed validation increases confidence, but adds test runtime and local resource requirements.
- Baseline retention and audit controls reduce privacy risk, but advanced compliance workflows still need future product and legal decisions.
- Migration jobs make rollout safer, but require more deployment orchestration than implicit app startup migrations.

## Consequences

- Business APIs must reject missing or invalid bearer tokens after Phase 6 hardening is enabled.
- Tenant/project request scope must be authorized against JWT claims.
- OpenAPI contracts must document bearer security requirements.
- Helm charts become the authoritative production deployment entry point.
- Docker Compose remains local-development only.
- Secrets must not be rendered inline in production manifests.
- Alerts and SLO dashboards must be delivered with runbooks.
- Redaction tests become release gates for logs, traces, metrics, events, and analytics responses.
- Future cloud-specific provisioning, advanced compliance, customer-managed keys, or identity-provider-specific behavior requires new ADRs or updates to this one.
