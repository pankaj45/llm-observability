# Runbook: Inference Gateway High Error Rate

## Impact

Streaming inference requests may fail or return degraded responses.

## Triage

- Check recent deploys and configuration changes.
- Inspect traces for provider, PostgreSQL, Redis, and Kafka failures.
- Confirm provider credentials and rate limits.
- Check whether failures are tenant/project-specific.

## Mitigation

- Roll back the latest gateway deployment if errors started after rollout.
- Disable Kafka publishing only if request serving is blocked by event publication failures and data-loss risk is accepted.
- Scale gateway replicas if saturation is the cause.

## Escalation

Escalate to the platform owner if failures persist for more than 15 minutes or affect multiple tenants.
