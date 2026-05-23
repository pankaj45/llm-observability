"use client";

import {
  Badge,
  Box,
  Button,
  Group,
  Loader,
  Paper,
  PasswordInput,
  ScrollArea,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  TextInput,
  ThemeIcon,
  Title
} from "@mantine/core";
import {
  IconAlertTriangle,
  IconChartBar,
  IconClock,
  IconDatabaseSearch,
  IconKey,
  IconRefresh,
  IconSearch,
  IconServerBolt
} from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
import styles from "./page.module.css";


type Totals = {
  requestCount: number;
  successCount: number;
  failedCount: number;
  cancelledCount: number;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  averageDurationMs: number;
  p95DurationMs: number;
  errorRate: number;
  estimatedCostUsd: number;
};

type TimeSeriesPoint = {
  bucket: string;
  requestCount: number;
  successCount: number;
  failedCount: number;
  cancelledCount: number;
  averageDurationMs: number;
  p95DurationMs: number;
  totalTokens: number;
};

type Breakdown = {
  name: string;
  requestCount: number;
  percentage: number;
  averageDurationMs: number;
  totalTokens: number;
  estimatedCostUsd: number;
};

type ErrorBreakdown = {
  errorCode: string;
  failureStage: string | null;
  providerErrorCode: string | null;
  requestCount: number;
  retryableCount: number;
};

type Summary = {
  tenantId: string;
  projectId: string;
  totals: Totals;
  timeSeries: TimeSeriesPoint[];
  providers: Breakdown[];
  models: Breakdown[];
  statuses: Breakdown[];
  topErrors: ErrorBreakdown[];
  degraded: boolean;
};

type RequestRow = {
  requestId: string;
  conversationId: string;
  provider: string;
  model: string;
  status: string;
  startedAt: string;
  completedAt: string | null;
  durationMs: number;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  estimatedCostUsd: number;
  errorCode: string | null;
  failureStage: string | null;
  correlationId: string | null;
  traceparent: string | null;
  cursor: string;
};

type RequestDetail = RequestRow & {
  tenantId: string;
  projectId: string;
  providerErrorCode: string | null;
  retryable: boolean | null;
  cancellationReason: string | null;
  providerCancellationAttempted: boolean | null;
  providerCancellationSucceeded: boolean | null;
  events: Array<{
    eventId: string;
    eventName: string;
    occurredAt: string;
    status: string;
    durationMs: number | null;
    inputTokens: number | null;
    outputTokens: number | null;
    totalTokens: number | null;
    errorCode: string | null;
    failureStage: string | null;
    cancellationReason: string | null;
  }>;
};

const apiBase = process.env.NEXT_PUBLIC_ANALYTICS_API_BASE ?? "/analytics/api";

function dateDaysAgo(days: number) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  date.setMinutes(0, 0, 0);
  return date;
}

function padDatePart(value: number) {
  return String(value).padStart(2, "0");
}

function toLocalInputValue(value: Date | string) {
  const date = typeof value === "string" ? new Date(value) : value;
  const safeDate = Number.isNaN(date.getTime()) ? new Date() : date;
  return [
    safeDate.getFullYear(),
    padDatePart(safeDate.getMonth() + 1),
    padDatePart(safeDate.getDate())
  ].join("-") + `T${padDatePart(safeDate.getHours())}:${padDatePart(safeDate.getMinutes())}`;
}

function toOffsetDateTime(date: Date) {
  const offsetMinutes = -date.getTimezoneOffset();
  const sign = offsetMinutes >= 0 ? "+" : "-";
  const absoluteOffset = Math.abs(offsetMinutes);
  const offsetHours = Math.floor(absoluteOffset / 60);
  const offsetRemainderMinutes = absoluteOffset % 60;
  const localDateTime = [
    date.getFullYear(),
    padDatePart(date.getMonth() + 1),
    padDatePart(date.getDate())
  ].join("-") + `T${padDatePart(date.getHours())}:${padDatePart(date.getMinutes())}:${padDatePart(date.getSeconds())}`;

  return `${localDateTime}${sign}${padDatePart(offsetHours)}:${padDatePart(offsetRemainderMinutes)}`;
}

function fromInputValue(value: string) {
  const date = new Date(value);
  return toOffsetDateTime(Number.isNaN(date.getTime()) ? new Date() : date);
}

function formatNumber(value: number) {
  return new Intl.NumberFormat("en-US").format(Math.round(value));
}

function formatDuration(value: number) {
  if (value >= 1000) {
    return `${(value / 1000).toFixed(2)}s`;
  }
  return `${Math.round(value)}ms`;
}

function statusColor(status: string) {
  if (status === "COMPLETED") return "teal";
  if (status === "FAILED") return "red";
  if (status === "CANCELLED") return "yellow";
  return "indigo";
}

function analyticsUrl(path: string, params: URLSearchParams) {
  return `${apiBase.replace(/\/$/, "")}${path}?${params.toString()}`;
}

async function errorMessage(response: Response, label: string) {
  const fallback = `${label} failed with HTTP ${response.status}`;
  try {
    const body = (await response.json()) as { error?: { message?: string; code?: string } };
    if (body.error?.message) {
      return `${label} failed: ${body.error.message}`;
    }
    if (body.error?.code) {
      return `${label} failed: ${body.error.code}`;
    }
  } catch {
    // The response may be an HTML proxy error or an empty body.
  }
  return fallback;
}

export default function HomePage() {
  const [tenantId, setTenantId] = useState("tenant-a");
  const [projectId, setProjectId] = useState("project-a");
  const [from, setFrom] = useState(toLocalInputValue(dateDaysAgo(7)));
  const [to, setTo] = useState(toLocalInputValue(new Date()));
  const [provider, setProvider] = useState("");
  const [model, setModel] = useState("");
  const [status, setStatus] = useState<string | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [requests, setRequests] = useState<RequestRow[]>([]);
  const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);
  const [detail, setDetail] = useState<RequestDetail | null>(null);
  const [authToken, setAuthToken] = useState("");
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const params = useMemo(() => {
    const query = new URLSearchParams({
      tenantId,
      projectId,
      from: fromInputValue(from),
      to: fromInputValue(to)
    });
    if (provider.trim()) query.set("provider", provider.trim());
    if (model.trim()) query.set("model", model.trim());
    if (status) query.set("status", status);
    return query;
  }, [tenantId, projectId, from, to, provider, model, status]);

  useEffect(() => {
    setAuthToken(window.localStorage.getItem("llm-observability.authToken") ?? "");
  }, []);

  function requestHeaders() {
    return authToken.trim() ? { Authorization: `Bearer ${authToken.trim()}` } : undefined;
  }

  function updateAuthToken(value: string) {
    setAuthToken(value);
    if (value.trim()) {
      window.localStorage.setItem("llm-observability.authToken", value);
    } else {
      window.localStorage.removeItem("llm-observability.authToken");
    }
  }

  async function loadDashboard() {
    setLoading(true);
    setError(null);
    try {
      const [summaryResponse, requestsResponse] = await Promise.all([
        fetch(analyticsUrl("/v1/analytics/inference/summary", params), { headers: requestHeaders() }),
        fetch(`${analyticsUrl("/v1/analytics/inference/requests", params)}&limit=50`, { headers: requestHeaders() })
      ]);
      if (!summaryResponse.ok) {
        throw new Error(await errorMessage(summaryResponse, "Analytics summary query"));
      }
      if (!requestsResponse.ok) {
        throw new Error(await errorMessage(requestsResponse, "Analytics request search"));
      }
      const nextSummary = (await summaryResponse.json()) as Summary;
      const requestPage = (await requestsResponse.json()) as { items: RequestRow[] };
      setSummary(nextSummary);
      setRequests(requestPage.items);
      setSelectedRequestId(requestPage.items[0]?.requestId ?? null);
    } catch (caught) {
      setSummary(null);
      setRequests([]);
      setSelectedRequestId(null);
      setDetail(null);
      setError(caught instanceof Error ? caught.message : "Analytics query failed");
    } finally {
      setLoading(false);
    }
  }

  async function loadDetail(requestId: string) {
    setDetailLoading(true);
    try {
      const response = await fetch(analyticsUrl(`/v1/analytics/inference/requests/${requestId}`, params), { headers: requestHeaders() });
      if (!response.ok) {
        throw new Error("Request detail failed");
      }
      setDetail((await response.json()) as RequestDetail);
    } catch {
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  }

  useEffect(() => {
    void loadDashboard();
  }, []);

  useEffect(() => {
    if (selectedRequestId) {
      void loadDetail(selectedRequestId);
    }
  }, [selectedRequestId]);

  const totals = summary?.totals;
  const maxSeries = Math.max(1, ...(summary?.timeSeries.map((point) => point.requestCount) ?? [1]));

  return (
    <Box component="main" className={styles.shell}>
      <Stack gap="lg">
        <Group justify="space-between" align="flex-start" gap="md">
          <div>
            <Title order={1} className={styles.title}>LLM Observability</Title>
            <Text c="dimmed">Inference analytics</Text>
          </div>
          <Group gap="xs">
            {summary?.degraded ? <Badge color="yellow">Degraded</Badge> : null}
            <Button leftSection={<IconRefresh size={16} />} onClick={loadDashboard} loading={loading}>
              Refresh
            </Button>
          </Group>
        </Group>

        <Paper withBorder radius="sm" p="md">
          <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }} spacing="sm">
            <TextInput label="Tenant" value={tenantId} onChange={(event) => setTenantId(event.currentTarget.value)} />
            <TextInput label="Project" value={projectId} onChange={(event) => setProjectId(event.currentTarget.value)} />
            <TextInput label="From" type="datetime-local" value={from} onChange={(event) => setFrom(event.currentTarget.value)} />
            <TextInput label="To" type="datetime-local" value={to} onChange={(event) => setTo(event.currentTarget.value)} />
            <TextInput label="Provider" value={provider} onChange={(event) => setProvider(event.currentTarget.value)} />
            <TextInput label="Model" value={model} onChange={(event) => setModel(event.currentTarget.value)} />
            <Select
              label="Status"
              clearable
              value={status}
              onChange={setStatus}
              data={["ACCEPTED", "STREAMING", "COMPLETED", "CANCELLED", "FAILED"]}
            />
            <PasswordInput
              label="Bearer token"
              leftSection={<IconKey size={16} />}
              value={authToken}
              onChange={(event) => updateAuthToken(event.currentTarget.value)}
            />
          </SimpleGrid>
          <Group justify="flex-end" mt="md">
            <Button leftSection={<IconSearch size={16} />} onClick={loadDashboard} loading={loading}>
              Apply
            </Button>
          </Group>
        </Paper>

        {error ? (
          <Paper withBorder radius="sm" p="md" className={styles.errorPanel}>
            <Group gap="sm">
              <ThemeIcon color="red" variant="light"><IconAlertTriangle size={18} /></ThemeIcon>
              <Text fw={600}>{error}</Text>
            </Group>
          </Paper>
        ) : null}

        <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }} spacing="md">
          <MetricCard icon={<IconServerBolt size={18} />} label="Requests" value={formatNumber(totals?.requestCount ?? 0)} sub={`${formatNumber(totals?.successCount ?? 0)} completed`} color="teal" />
          <MetricCard icon={<IconClock size={18} />} label="P95 latency" value={formatDuration(totals?.p95DurationMs ?? 0)} sub={`${formatDuration(totals?.averageDurationMs ?? 0)} average`} color="indigo" />
          <MetricCard icon={<IconChartBar size={18} />} label="Tokens" value={formatNumber(totals?.totalTokens ?? 0)} sub={`${formatNumber(totals?.outputTokens ?? 0)} output`} color="grape" />
          <MetricCard icon={<IconAlertTriangle size={18} />} label="Error rate" value={`${(((totals?.errorRate ?? 0) * 100)).toFixed(1)}%`} sub={`${formatNumber(totals?.failedCount ?? 0)} failed`} color="red" />
        </SimpleGrid>

        <SimpleGrid cols={{ base: 1, lg: 3 }} spacing="md">
          <Paper withBorder radius="sm" p="md" className={styles.panel}>
            <Group justify="space-between" mb="sm">
              <Text fw={700}>Volume Trend</Text>
              {loading ? <Loader size="sm" /> : null}
            </Group>
            <Group align="end" gap={6} className={styles.bars}>
              {(summary?.timeSeries ?? []).map((point) => (
                <div key={point.bucket} className={styles.barWrap}>
                  <div
                    className={styles.bar}
                    style={{ height: `${Math.max(6, (point.requestCount / maxSeries) * 120)}px` }}
                    title={`${new Date(point.bucket).toLocaleString()}: ${point.requestCount}`}
                  />
                </div>
              ))}
              {!summary?.timeSeries.length ? <EmptyState label="No trend data" /> : null}
            </Group>
          </Paper>

          <BreakdownPanel title="Providers" items={summary?.providers ?? []} />
          <BreakdownPanel title="Models" items={summary?.models ?? []} />
        </SimpleGrid>

        <SimpleGrid cols={{ base: 1, lg: 3 }} spacing="md">
          <Paper withBorder radius="sm" p="md" className={styles.requestPanel}>
            <Text fw={700} mb="sm">Requests</Text>
            <ScrollArea h={420}>
              <Table striped highlightOnHover withTableBorder={false}>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Status</Table.Th>
                    <Table.Th>Model</Table.Th>
                    <Table.Th>Latency</Table.Th>
                    <Table.Th>Tokens</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {requests.map((request) => (
                    <Table.Tr
                      key={request.requestId}
                      className={selectedRequestId === request.requestId ? styles.selectedRow : styles.row}
                      onClick={() => setSelectedRequestId(request.requestId)}
                    >
                      <Table.Td><Badge color={statusColor(request.status)} variant="light">{request.status}</Badge></Table.Td>
                      <Table.Td>
                        <Text size="sm" fw={600}>{request.provider}</Text>
                        <Text size="xs" c="dimmed">{request.model}</Text>
                      </Table.Td>
                      <Table.Td>{formatDuration(request.durationMs)}</Table.Td>
                      <Table.Td>{formatNumber(request.totalTokens)}</Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
              {!requests.length && !loading ? <EmptyState label="No requests" /> : null}
            </ScrollArea>
          </Paper>

          <Paper withBorder radius="sm" p="md" className={styles.detailPanel}>
            <Group justify="space-between" mb="sm">
              <Text fw={700}>Request Trace</Text>
              {detailLoading ? <Loader size="sm" /> : null}
            </Group>
            {detail ? (
              <Stack gap="sm">
                <Group gap="xs">
                  <Badge color={statusColor(detail.status)}>{detail.status}</Badge>
                  {detail.errorCode ? <Badge color="red" variant="light">{detail.errorCode}</Badge> : null}
                </Group>
                <Text className={styles.mono}>{detail.requestId}</Text>
                <SimpleGrid cols={2} spacing="xs">
                  <MiniStat label="Duration" value={formatDuration(detail.durationMs)} />
                  <MiniStat label="Tokens" value={formatNumber(detail.totalTokens)} />
                  <MiniStat label="Input" value={formatNumber(detail.inputTokens)} />
                  <MiniStat label="Output" value={formatNumber(detail.outputTokens)} />
                </SimpleGrid>
                <Stack gap={8} mt="xs">
                  {detail.events.map((event) => (
                    <Group key={event.eventId} align="flex-start" gap="sm" wrap="nowrap">
                      <ThemeIcon color={statusColor(event.status)} variant="light" size="sm">
                        <IconDatabaseSearch size={14} />
                      </ThemeIcon>
                      <Box>
                        <Text size="sm" fw={600}>{event.eventName}</Text>
                        <Text size="xs" c="dimmed">{new Date(event.occurredAt).toLocaleString()}</Text>
                      </Box>
                    </Group>
                  ))}
                </Stack>
              </Stack>
            ) : (
              <EmptyState label="Select a request" />
            )}
          </Paper>

          <Paper withBorder radius="sm" p="md" className={styles.panel}>
            <Text fw={700} mb="sm">Top Errors</Text>
            <Stack gap="xs">
              {(summary?.topErrors ?? []).map((item) => (
                <Group key={item.errorCode} justify="space-between" className={styles.errorRow}>
                  <Box>
                    <Text size="sm" fw={600}>{item.errorCode}</Text>
                    <Text size="xs" c="dimmed">{item.failureStage ?? "unknown stage"}</Text>
                  </Box>
                  <Badge color="red" variant="light">{item.requestCount}</Badge>
                </Group>
              ))}
              {!summary?.topErrors.length ? <EmptyState label="No errors" /> : null}
            </Stack>
          </Paper>
        </SimpleGrid>
      </Stack>
    </Box>
  );
}

function MetricCard({ icon, label, value, sub, color }: { icon: ReactNode; label: string; value: string; sub: string; color: string }) {
  return (
    <Paper withBorder radius="sm" p="md" className={styles.metric}>
      <Group justify="space-between" align="flex-start">
        <Box>
          <Text size="sm" c="dimmed">{label}</Text>
          <Text className={styles.metricValue}>{value}</Text>
          <Text size="xs" c="dimmed">{sub}</Text>
        </Box>
        <ThemeIcon color={color} variant="light">{icon}</ThemeIcon>
      </Group>
    </Paper>
  );
}

function BreakdownPanel({ title, items }: { title: string; items: Breakdown[] }) {
  return (
    <Paper withBorder radius="sm" p="md" className={styles.panel}>
      <Text fw={700} mb="sm">{title}</Text>
      <Stack gap="sm">
        {items.map((item) => (
          <Box key={item.name}>
            <Group justify="space-between" mb={4}>
              <Text size="sm" fw={600} truncate="end">{item.name}</Text>
              <Text size="sm" c="dimmed">{formatNumber(item.requestCount)}</Text>
            </Group>
            <div className={styles.progressTrack}>
              <div className={styles.progressValue} style={{ width: `${Math.max(4, item.percentage * 100)}%` }} />
            </div>
          </Box>
        ))}
        {!items.length ? <EmptyState label={`No ${title.toLowerCase()}`} /> : null}
      </Stack>
    </Paper>
  );
}

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <Box className={styles.miniStat}>
      <Text size="xs" c="dimmed">{label}</Text>
      <Text size="sm" fw={700}>{value}</Text>
    </Box>
  );
}

function EmptyState({ label }: { label: string }) {
  return (
    <Box className={styles.empty}>
      <Text size="sm" c="dimmed">{label}</Text>
    </Box>
  );
}
