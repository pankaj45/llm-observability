# Runbook: Analytics Query Slow

## Impact

Operator dashboards and request drilldowns may load slowly.

## Triage

- Check ClickHouse health and query latency.
- Confirm dashboard query windows are bounded.
- Inspect analytics-query CPU, memory, and connection errors.
- Check for recent lifecycle fact cardinality spikes.

## Mitigation

- Scale analytics-query replicas.
- Reduce default dashboard query windows.
- Add ClickHouse projections or materialized views if repeated scans are the cause.

## Escalation

Escalate to the data platform owner if ClickHouse saturation or schema changes are involved.
