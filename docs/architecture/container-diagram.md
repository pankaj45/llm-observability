# Container Diagram

## Purpose

This diagram shows the planned internal containers and data stores. It is intentionally service-level; implementation package details are documented in [Repository Structure](../repository-structure.md).

```mermaid
flowchart TB
    subgraph Users["Users and Clients"]
        Browser["Operator Browser"]
        SDKClient["Instrumented Application / API Client"]
    end

    subgraph Platform["LLM Observability Platform"]
        Web["Web App<br/>Next.js + Mantine"]
        Gateway["Inference Gateway<br/>Spring Boot WebFlux"]
        Worker["Ingestion Worker<br/>Spring Boot WebFlux/Reactive Consumers"]
        Analytics["Analytics Query Service<br/>Spring Boot WebFlux"]
        OTel["OpenTelemetry Collector"]
    end

    subgraph Data["Data and Messaging"]
        Kafka["Kafka<br/>Domain event bus"]
        Postgres["PostgreSQL<br/>OLTP state"]
        ClickHouse["ClickHouse<br/>Analytics facts"]
        Redis["Redis<br/>Cache, cancellation, resume state"]
        Prometheus["Prometheus<br/>Metrics"]
        Grafana["Grafana<br/>Dashboards"]
    end

    Providers["LLM Providers"]

    Browser -->|"HTTPS"| Web
    Browser -->|"HTTPS/SSE"| Gateway
    Browser -->|"HTTPS"| Analytics
    SDKClient -->|"HTTPS/SSE"| Gateway

    Gateway -->|"Provider API streaming"| Providers
    Gateway -->|"Publish lifecycle events"| Kafka
    Gateway -->|"Conversation/request metadata"| Postgres
    Gateway -->|"Cancellation and resume state"| Redis

    Worker -->|"Consume lifecycle events"| Kafka
    Worker -->|"Upsert authoritative state"| Postgres
    Worker -->|"Batch analytics writes"| ClickHouse

    Analytics -->|"Read metadata"| Postgres
    Analytics -->|"Read metrics and traces"| ClickHouse

    Web -->|"OTLP"| OTel
    Gateway -->|"OTLP"| OTel
    Worker -->|"OTLP"| OTel
    Analytics -->|"OTLP"| OTel
    OTel -->|"Metrics export"| Prometheus
    Prometheus --> Grafana
```

## Container Responsibilities

| Container | Responsibility |
| --- | --- |
| Web App | Operator workflows, dashboards, trace exploration, project/provider settings |
| Inference Gateway | API validation, streaming, provider selection, cancellation, lifecycle event publishing |
| Ingestion Worker | Kafka consumption, idempotent persistence, ClickHouse ingestion, retry/dead-letter handling |
| Analytics Query Service | Dashboard and trace query APIs with authorization and query controls |
| Kafka | Durable event bus for inference and observability lifecycle events |
| PostgreSQL | Authoritative tenant, project, conversation, request, and provider configuration data |
| ClickHouse | High-volume inference analytics, token facts, latency, cost, errors |
| Redis | Stream cursors, cancellation flags, short-lived cache, rate-limit counters if needed |
| OTel Collector | Telemetry collection, processing, and export |
| Prometheus/Grafana | Metrics storage, dashboards, and alerting integration |

## Key Tradeoffs

- Keeping analytics query separate from ingestion avoids coupling dashboard latency to consumer throughput.
- Gateway writes minimal authoritative state and publishes events so streaming remains responsive.
- Redis state is short-lived and must not be the only source of truth for completed conversations.

