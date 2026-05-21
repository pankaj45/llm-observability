# System Context Diagram

## Purpose

This diagram shows the platform boundary and its primary external actors and systems.

```mermaid
flowchart LR
    Dev["Application Developer"]
    Operator["Platform Operator"]
    Admin["Tenant Admin"]
    EndUser["End User of AI Application"]

    Platform["LLM Observability Platform"]

    LLMProviders["LLM Providers<br/>OpenAI, Anthropic, Azure OpenAI,<br/>Bedrock, Vertex AI, local models"]
    Identity["Identity Provider<br/>OIDC or SAML"]
    Alerting["Alerting Destinations<br/>Email, Slack, PagerDuty"]
    CI["CI/CD System"]
    ObjectStorage["Optional Archive Storage"]

    EndUser -->|"Uses AI application that calls platform APIs"| Platform
    Dev -->|"Integrates SDK/API and reviews traces"| Platform
    Operator -->|"Monitors health, latency, cost, failures"| Platform
    Admin -->|"Configures tenants, projects, providers"| Platform

    Platform -->|"Streams and invokes model requests"| LLMProviders
    Platform -->|"Authenticates users and service clients"| Identity
    Platform -->|"Emits alerts and notifications"| Alerting
    CI -->|"Deploys and validates contracts"| Platform
    Platform -->|"Future archival export"| ObjectStorage
```

## Context Notes

- The platform can proxy inference requests directly or receive inference logs from instrumented applications in a later phase.
- LLM providers are external systems with different streaming, cancellation, retry, rate limit, and token accounting semantics.
- Identity integration is assumed but not specified. A later security spec must decide the exact provider, claims, roles, and service account model.
- Alerting destinations are downstream consumers of SLO and anomaly signals, not the source of truth for inference telemetry.

## Production Concerns

- Provider credentials must be isolated by tenant/project and never exposed to the frontend.
- All external calls need timeouts, retry policies, circuit breakers, and trace propagation where supported.
- Identity, provider, and alerting integrations must be tested with failure-mode scenarios.

