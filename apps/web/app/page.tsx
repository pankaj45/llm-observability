"use client";

import {
  Badge,
  Button,
  Collapse,
  Loader,
  Select,
  Text,
  Textarea,
  TextInput,
  ThemeIcon,
  Title,
} from "@mantine/core";
import {
  IconAlertTriangle,
  IconBrandHipchat,
  IconPlayerStop,
  IconSend,
  IconSettings,
  IconPlus,
} from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import styles from "./page.module.css";

// ─── Constants ───────────────────────────────────────────────────────────────

const INFERENCE_BASE =
  process.env.NEXT_PUBLIC_INFERENCE_API_BASE ?? "http://localhost:8080";

const DEFAULT_TENANT = "tenant-a";
const DEFAULT_PROJECT = "project-a";
const PROVIDER = "gemini";

// ─── Types ────────────────────────────────────────────────────────────────────

type MessageStatus = "complete" | "streaming" | "cancelled" | "failed";

interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  status: MessageStatus;
  tokenCount?: number;
  errorCode?: string;
}

interface Conversation {
  conversationId: string;
  title: string;
  status: string;
  messageCount: number;
  updatedAt: string;
  latestRequestStatus?: string | null;
}

interface SseEventPayload {
  id: string;
  type: string;
  requestId: string;
  conversationId: string;
  sequence: number;
  occurredAt: string;
  data: Record<string, unknown>;
}

interface ModelCatalogResponse {
  providers: Array<{
    id: string;
    name: string;
    models: Array<{
      id: string;
      name: string;
      contextWindowTokens: number;
      maxOutputTokens: number;
      supportsStreaming: boolean;
      supportsCancellation: boolean;
    }>;
  }>;
}

// ─── SSE helpers ─────────────────────────────────────────────────────────────

/**
 * Parse a raw SSE text frame into event name + JSON payload.
 * The gateway emits: id:, event:, data: lines separated by \n\n.
 */
function parseSseFrame(frame: string): { event: string; payload: SseEventPayload } | null {
  let event = "";
  let dataLine = "";
  for (const line of frame.split("\n")) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    if (line.startsWith("data:")) dataLine = line.slice(5).trim();
  }
  if (!event || !dataLine) return null;
  try {
    return { event, payload: JSON.parse(dataLine) as SseEventPayload };
  } catch {
    return null;
  }
}

// ─── Settings persistence ─────────────────────────────────────────────────────

function loadSetting(key: string, fallback: string) {
  if (typeof window === "undefined") return fallback;
  return window.localStorage.getItem(key) ?? fallback;
}

function saveSetting(key: string, value: string) {
  window.localStorage.setItem(key, value);
}

// ─── Relative time ────────────────────────────────────────────────────────────

function relativeTime(iso: string) {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

// ─── Component ────────────────────────────────────────────────────────────────

export default function ChatPage() {
  // ── settings ──
  const [tenantId, setTenantId] = useState(DEFAULT_TENANT);
  const [projectId, setProjectId] = useState(DEFAULT_PROJECT);
  const [settingsOpen, setSettingsOpen] = useState(false);

  // ── conversation list ──
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [convListError, setConvListError] = useState<string | null>(null);
  const [convListLoading, setConvListLoading] = useState(false);

  // ── active chat ──
  const [activeConvId, setActiveConvId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);

  // ── input ──
  const [input, setInput] = useState("");
  const [model, setModel] = useState("");
  const [modelOptions, setModelOptions] = useState<{ value: string; label: string; group?: string }[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);

  // ── internals ──
  const abortRef = useRef<AbortController | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  // ── hydrate settings from localStorage ──
  useEffect(() => {
    setTenantId(loadSetting("llm-obs.tenantId", DEFAULT_TENANT));
    setProjectId(loadSetting("llm-obs.projectId", DEFAULT_PROJECT));
  }, []);

  // ── load conversation list on mount and when tenant/project changes ──
  useEffect(() => {
    void loadConversations();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tenantId, projectId]);

  // ── load model catalog ──
  useEffect(() => {
    void loadCatalog();
  }, []);

  // ── scroll to bottom on new messages ──
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // ─── API helpers ────────────────────────────────────────────────────────────

  function inferenceHeaders(): HeadersInit {
    return { "Content-Type": "application/json" };
  }

  async function loadCatalog() {
    try {
      const res = await fetch(`${INFERENCE_BASE}/v1/catalog/models`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const body = (await res.json()) as ModelCatalogResponse;
      
      const options = body.providers.flatMap((p) =>
        p.models.map((m) => ({
          value: m.id,
          label: m.name,
          group: p.name,
        }))
      );
      setModelOptions(options);
      
      // Default to the first available model if none selected
      if (options.length > 0 && !model) {
        // Try to pick a flash model as default, else just the first one
        const defaultOpt = options.find(o => o.value.includes('flash')) ?? options[0];
        setModel(defaultOpt.value);
      }
    } catch (err) {
      console.error("Failed to load model catalog:", err);
      // Fallback
      setModelOptions([{ value: "gemini-1.5-flash", label: "Gemini 1.5 Flash (Fallback)" }]);
      if (!model) setModel("gemini-1.5-flash");
    }
  }

  async function loadConversations() {
    setConvListLoading(true);
    setConvListError(null);
    try {
      const params = new URLSearchParams({ tenantId, projectId, limit: "30" });
      const res = await fetch(`${INFERENCE_BASE}/v1/conversations?${params}`, {
        headers: inferenceHeaders(),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const body = (await res.json()) as { items: Conversation[] };
      setConversations(body.items);
    } catch (err) {
      setConvListError(err instanceof Error ? err.message : "Failed to load conversations");
    } finally {
      setConvListLoading(false);
    }
  }

  async function loadConversationHistory(convId: string) {
    setHistoryLoading(true);
    setHistoryError(null);
    setMessages([]);
    try {
      const params = new URLSearchParams({ tenantId, projectId, limit: "200" });
      const res = await fetch(
        `${INFERENCE_BASE}/v1/conversations/${convId}/messages?${params}`,
        { headers: inferenceHeaders() }
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const body = (await res.json()) as {
        items: Array<{
          messageId: string;
          role: "USER" | "ASSISTANT" | "SYSTEM" | "TOOL";
          content: string;
          metadata?: Record<string, string>;
        }>;
      };
      const loaded: ChatMessage[] = body.items
        .filter((m) => m.role === "USER" || m.role === "ASSISTANT")
        .map((m) => ({
          id: m.messageId,
          role: m.role === "USER" ? "user" : "assistant",
          content: m.content,
          status:
            m.metadata?.terminalState === "cancelled"
              ? "cancelled"
              : m.metadata?.terminalState === "failed"
              ? "failed"
              : "complete",
          errorCode: undefined,
        }));
      setMessages(loaded);
    } catch (err) {
      setHistoryError(
        err instanceof Error ? err.message : "Failed to load conversation history"
      );
    } finally {
      setHistoryLoading(false);
    }
  }

  // ─── Send message ────────────────────────────────────────────────────────────

  async function handleSend() {
    const text = input.trim();
    if (!text || streaming) return;

    setSendError(null);
    setInput("");

    // Append user message immediately
    const userMsgId = crypto.randomUUID();
    const userMsg: ChatMessage = {
      id: userMsgId,
      role: "user",
      content: text,
      status: "complete",
    };

    // Placeholder assistant message while streaming
    const assistantMsgId = crypto.randomUUID();
    const assistantMsg: ChatMessage = {
      id: assistantMsgId,
      role: "assistant",
      content: "",
      status: "streaming",
    };

    setMessages((prev) => [...prev, userMsg, assistantMsg]);
    setStreaming(true);

    const idempotencyKey = crypto.randomUUID();
    const abort = new AbortController();
    abortRef.current = abort;

    try {
      let url: string;
      let body: Record<string, unknown>;

      if (activeConvId) {
        // Continue existing conversation — send only the new user turn
        url = `${INFERENCE_BASE}/v1/conversations/${activeConvId}/messages/stream`;
        body = {
          tenantId,
          projectId,
          provider: PROVIDER,
          model,
          messages: [{ role: "user", content: text }],
          parameters: {},
          idempotencyKey,
        };
      } else {
        // Start new conversation
        url = `${INFERENCE_BASE}/v1/inference/stream`;
        body = {
          tenantId,
          projectId,
          provider: PROVIDER,
          model,
          messages: [{ role: "user", content: text }],
          parameters: {},
          idempotencyKey,
        };
      }

      const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
        signal: abort.signal,
      });

      if (!res.ok || !res.body) {
        throw new Error(`Server error: HTTP ${res.status}`);
      }

      // ── Parse SSE stream ──
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let newConvId: string | null = null;
      let totalOutputTokens = 0;

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // SSE frames are separated by double newlines
        const frames = buffer.split("\n\n");
        buffer = frames.pop() ?? "";

        for (const frame of frames) {
          const parsed = parseSseFrame(frame);
          if (!parsed) continue;

          const { event, payload } = parsed;
          const data = payload.data;

          if (event === "request.accepted" || event === "token.delta" || event === "usage.delta") {
            // Extract conversationId from first event (new conversation)
            if (!newConvId && payload.conversationId) {
              newConvId = payload.conversationId;
              setActiveConvId(payload.conversationId);
            }
          }

          if (event === "token.delta") {
            const delta = typeof data.delta === "string" ? data.delta : "";
            if (typeof data.outputTokens === "number") totalOutputTokens = data.outputTokens;
            setMessages((prev) =>
              prev.map((m) =>
                m.id === assistantMsgId
                  ? { ...m, content: m.content + delta, status: "streaming" }
                  : m
              )
            );
          }

          if (event === "usage.delta") {
            if (typeof data.outputTokens === "number") totalOutputTokens = data.outputTokens;
          }

          if (event === "request.completed") {
            const inputToks = typeof data.inputTokens === "number" ? data.inputTokens : 0;
            const outputToks = typeof data.outputTokens === "number" ? data.outputTokens : totalOutputTokens;
            setMessages((prev) =>
              prev.map((m) =>
                m.id === assistantMsgId
                  ? { ...m, status: "complete", tokenCount: inputToks + outputToks }
                  : m
              )
            );
          }

          if (event === "request.cancelled") {
            setMessages((prev) =>
              prev.map((m) =>
                m.id === assistantMsgId ? { ...m, status: "cancelled" } : m
              )
            );
          }

          if (event === "request.failed") {
            const errorCode =
              typeof data.errorCode === "string" ? data.errorCode : "UNKNOWN_ERROR";
            setMessages((prev) =>
              prev.map((m) =>
                m.id === assistantMsgId ? { ...m, status: "failed", errorCode } : m
              )
            );
          }
        }
      }
    } catch (err) {
      if ((err as { name?: string }).name === "AbortError") {
        // User clicked Stop — the request.cancelled event handles the UI update
      } else {
        setSendError(err instanceof Error ? err.message : "Failed to send message");
        setMessages((prev) =>
          prev.map((m) =>
            m.id === assistantMsgId ? { ...m, status: "failed", errorCode: "NETWORK_ERROR" } : m
          )
        );
      }
    } finally {
      setStreaming(false);
      abortRef.current = null;
      // Refresh conversation list to show new/updated conversation
      void loadConversations();
    }
  }

  // ─── Stop active stream ──────────────────────────────────────────────────────

  async function handleStop() {
    if (!activeConvId) {
      // Abort the fetch if we don't have a conversationId yet
      abortRef.current?.abort();
      return;
    }
    try {
      const params = new URLSearchParams({ tenantId, projectId });
      await fetch(`${INFERENCE_BASE}/v1/conversations/${activeConvId}/stream?${params}`, {
        method: "DELETE",
        headers: { "X-Requested-By": "chatbot-ui", "X-Cancel-Reason": "user_requested" },
      });
      // The SSE stream will emit request.cancelled which updates the UI
    } catch {
      // Fallback: abort the fetch connection
      abortRef.current?.abort();
    }
  }

  // ─── Select conversation ─────────────────────────────────────────────────────

  function handleSelectConversation(conv: Conversation) {
    if (conv.conversationId === activeConvId) return;
    if (streaming) return; // Don't switch while streaming
    setActiveConvId(conv.conversationId);
    void loadConversationHistory(conv.conversationId);
  }

  // ─── New chat ────────────────────────────────────────────────────────────────

  function handleNewChat() {
    if (streaming) return;
    setActiveConvId(null);
    setMessages([]);
    setSendError(null);
    setHistoryError(null);
  }

  // ─── Settings ────────────────────────────────────────────────────────────────

  function handleTenantChange(value: string) {
    setTenantId(value);
    saveSetting("llm-obs.tenantId", value);
  }

  function handleProjectChange(value: string) {
    setProjectId(value);
    saveSetting("llm-obs.projectId", value);
  }

  // ─── Handle Enter key ────────────────────────────────────────────────────────

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void handleSend();
    }
  }

  // ─── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className={styles.shell}>
      {/* ── Sidebar ── */}
      <aside className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <Button
            id="new-chat-btn"
            className={styles.newChatBtn}
            leftSection={<IconPlus size={14} />}
            variant="light"
            size="sm"
            onClick={handleNewChat}
            disabled={streaming}
          >
            New Chat
          </Button>
        </div>

        <div className={styles.convList}>
          {convListLoading && (
            <div style={{ padding: "12px", textAlign: "center" }}>
              <Loader size="sm" />
            </div>
          )}
          {convListError && (
            <Text size="xs" c="red" p="sm">
              {convListError}
            </Text>
          )}
          {!convListLoading &&
            conversations.map((conv) => (
              <div
                key={conv.conversationId}
                id={`conv-${conv.conversationId}`}
                className={
                  conv.conversationId === activeConvId
                    ? `${styles.convItem} ${styles.convItemActive}`
                    : styles.convItem
                }
                onClick={() => handleSelectConversation(conv)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => e.key === "Enter" && handleSelectConversation(conv)}
              >
                <div className={styles.convTitle}>{conv.title}</div>
                <div className={styles.convMeta}>
                  {conv.messageCount} messages · {relativeTime(conv.updatedAt)}
                </div>
              </div>
            ))}
          {!convListLoading && conversations.length === 0 && !convListError && (
            <Text size="xs" c="dimmed" p="sm" ta="center">
              No conversations yet
            </Text>
          )}
        </div>

        <div className={styles.sidebarFooter}>
          <button
            className={styles.settingsToggle}
            onClick={() => setSettingsOpen((o) => !o)}
            aria-expanded={settingsOpen}
          >
            <IconSettings size={13} />
            {settingsOpen ? "Hide settings" : "Settings"}
          </button>
          <Collapse in={settingsOpen}>
            <div className={styles.settingsPanel}>
              <TextInput
                id="tenant-id-input"
                label="Tenant ID"
                size="xs"
                value={tenantId}
                onChange={(e) => handleTenantChange(e.currentTarget.value)}
              />
              <TextInput
                id="project-id-input"
                label="Project ID"
                size="xs"
                value={projectId}
                onChange={(e) => handleProjectChange(e.currentTarget.value)}
              />
            </div>
          </Collapse>
        </div>
      </aside>

      {/* ── Chat area ── */}
      <div className={styles.chatArea}>
        <div className={styles.messagesScroll}>
          <div className={styles.messagesInner}>
            {historyLoading && (
              <div style={{ textAlign: "center", padding: 32 }}>
                <Loader size="sm" />
                <Text size="xs" c="dimmed" mt={8}>
                  Loading history…
                </Text>
              </div>
            )}
            {historyError && (
              <div className={styles.errorNotice}>
                <IconAlertTriangle size={14} style={{ verticalAlign: "middle", marginRight: 6 }} />
                {historyError}
              </div>
            )}
            {!historyLoading && messages.length === 0 && !historyError && (
              <div className={styles.emptyState}>
                <ThemeIcon color="indigo" variant="light" size={56} radius="xl">
                  <IconBrandHipchat size={28} />
                </ThemeIcon>
                <Title order={4} c="dimmed">
                  Start a conversation
                </Title>
                <Text size="sm" c="dimmed" ta="center" maw={300}>
                  Type a message below and press Enter. Your conversations are saved and you
                  can resume them from the sidebar.
                </Text>
              </div>
            )}
            {messages.map((msg) => (
              <MessageBubble key={msg.id} msg={msg} />
            ))}
            <div ref={messagesEndRef} />
          </div>
        </div>

        {/* ── Input bar ── */}
        <div className={styles.inputBar}>
          <div className={styles.inputInner}>
            {sendError && (
              <div className={styles.errorNotice}>
                <IconAlertTriangle size={14} style={{ verticalAlign: "middle", marginRight: 6 }} />
                {sendError}
              </div>
            )}
            <div className={styles.inputRow}>
              <Textarea
                id="chat-input"
                className={styles.inputTextarea}
                placeholder="Type a message… (Enter to send, Shift+Enter for newline)"
                value={input}
                onChange={(e) => setInput(e.currentTarget.value)}
                onKeyDown={handleKeyDown}
                autosize
                minRows={1}
                maxRows={6}
                disabled={streaming}
                radius="md"
              />
              <div className={styles.inputActions}>
                <Select
                  id="model-select"
                  data={modelOptions}
                  value={model}
                  onChange={(v) => { if (v) setModel(v); }}
                  size="sm"
                  w={170}
                  disabled={streaming || modelOptions.length === 0}
                  radius="md"
                />
                {streaming ? (
                  <Button
                    id="stop-btn"
                    color="red"
                    variant="light"
                    leftSection={<IconPlayerStop size={15} />}
                    onClick={handleStop}
                    radius="md"
                    size="sm"
                  >
                    Stop
                  </Button>
                ) : (
                  <Button
                    id="send-btn"
                    leftSection={<IconSend size={15} />}
                    onClick={() => void handleSend()}
                    disabled={!input.trim()}
                    radius="md"
                    size="sm"
                  >
                    Send
                  </Button>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Message bubble sub-component ─────────────────────────────────────────────

function MessageBubble({ msg }: { msg: ChatMessage }) {
  const isUser = msg.role === "user";
  const isStreaming = msg.status === "streaming";
  const isCancelled = msg.status === "cancelled";
  const isFailed = msg.status === "failed";

  let bubbleClass = isUser ? styles.bubbleUser : styles.bubbleAssistant;
  if (isCancelled) bubbleClass += ` ${styles.bubbleCancelled}`;
  if (isFailed) bubbleClass += ` ${styles.bubbleFailed}`;

  return (
    <div className={`${styles.msgRow} ${isUser ? styles.msgRowUser : styles.msgRowAssistant}`}>
      <div className={`${styles.bubble} ${bubbleClass}`}>
        {msg.content}
        {isStreaming && <span className={styles.streamingCursor} aria-hidden="true" />}
        {(isCancelled || isFailed) && (
          <div className={styles.bubgeStatusRow}>
            {isCancelled && (
              <Badge color="orange" variant="light" size="xs">
                Cancelled
              </Badge>
            )}
            {isFailed && (
              <Badge color="red" variant="light" size="xs">
                {msg.errorCode ?? "Failed"}
              </Badge>
            )}
          </div>
        )}
        {msg.tokenCount != null && msg.tokenCount > 0 && (
          <div className={styles.bubbleMeta}>{msg.tokenCount} tokens</div>
        )}
      </div>
    </div>
  );
}
