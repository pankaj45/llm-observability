# Runbook: Kafka Consumer Lag High

## Impact

Analytics freshness may degrade because lifecycle events are not reaching ClickHouse quickly enough.

## Triage

- Check ingestion-worker logs and processing error counters.
- Confirm Kafka broker health and partition availability.
- Check ClickHouse insert latency and failures.
- Inspect PostgreSQL ingestion ledger failures.

## Mitigation

- Scale ingestion-worker if processing is CPU-bound and consumer group behavior allows it.
- Pause replay jobs if they are competing with live traffic.
- Restore ClickHouse availability before resuming consumers if inserts are failing.

## Escalation

Escalate if lag continues to grow for more than 15 minutes or ClickHouse writes are failing.
