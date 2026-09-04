"use client";

import Link from "next/link";
import { AlertTriangle, Bot, Check, ChevronRight, Clock3, MessageCircle, Plus, RotateCcw, Send, ShieldCheck, Sparkles, ThumbsDown, ThumbsUp, X } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { agentApi, type AgentAssistResponse, type AgentSourceReference, type AgentToolCall } from "@/lib/api/agent";
import { CurmerceApiError } from "@/lib/api/client";
import { getAccessToken } from "@/lib/auth/storage";

type Message = { id: string; role: "user" | "assistant"; text: string; response?: AgentAssistResponse };
type Conversation = { id: string; title: string; updatedAt: number; messages: Message[] };

const HISTORY_KEY = "curmerce.agent.conversations.v1";

function createId() {
  return typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
}

function listFromNode(value: unknown): Record<string, unknown>[] {
  if (Array.isArray(value)) return value.filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"));
  if (value && typeof value === "object" && Array.isArray((value as { list?: unknown }).list)) {
    return listFromNode((value as { list: unknown }).list);
  }
  return [];
}

function displayText(value: unknown, fallback: string) {
  if (typeof value === "string" && value.trim()) return value;
  if (typeof value === "number") return String(value);
  return fallback;
}

function sourceLabel(source: string) {
  if (source === "product") return "商品";
  if (source === "community") return "社区";
  return "知识库";
}

function newConversation(): Conversation {
  return { id: createId(), title: "新对话", updatedAt: Date.now(), messages: [] };
}

export default function AgentPage() {
  const [conversation, setConversation] = useState<Conversation | null>(null);
  const [history, setHistory] = useState<Conversation[]>([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hydrated, setHydrated] = useState(false);
  const [executingTool, setExecutingTool] = useState<string | null>(null);
  const [toolNotice, setToolNotice] = useState<string | null>(null);
  const [feedbackByMessage, setFeedbackByMessage] = useState<Record<string, boolean>>({});
  const [sendingFeedback, setSendingFeedback] = useState<string | null>(null);

  useEffect(() => {
    try {
      const saved = JSON.parse(window.localStorage.getItem(HISTORY_KEY) ?? "[]") as Conversation[];
      const valid = Array.isArray(saved) ? saved.filter((item) => item && typeof item.id === "string") : [];
      setHistory(valid.slice(0, 12));
      setConversation(valid[0] ?? newConversation());
    } catch {
      setConversation(newConversation());
    } finally {
      setHydrated(true);
    }
  }, []);

  useEffect(() => {
    if (!hydrated || !conversation) return;
    const next = [conversation, ...history.filter((item) => item.id !== conversation.id)].sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 12);
    setHistory(next);
    window.localStorage.setItem(HISTORY_KEY, JSON.stringify(next));
  // Persist only after a conversation changes. The history state is read from
  // the closure to avoid a second write loop when the sidebar is refreshed.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation, hydrated]);

  const canSend = Boolean(draft.trim()) && !loading && Boolean(conversation);
  const hasMessages = Boolean(conversation?.messages.length);

  function startConversation() {
    setConversation(newConversation());
    setDraft("");
    setError(null);
    setToolNotice(null);
  }

  function selectConversation(id: string) {
    const selected = history.find((item) => item.id === id);
    if (selected) {
      setConversation(selected);
      setError(null);
      setToolNotice(null);
    }
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    if (!canSend || !conversation) return;
    if (!getAccessToken()) {
      setError("登录后才能使用 Curmerce 助手。");
      return;
    }
    const query = draft.trim();
    const userMessage: Message = { id: createId(), role: "user", text: query };
    setConversation((current) => current ? { ...current, title: current.messages.length ? current.title : query.slice(0, 24), updatedAt: Date.now(), messages: [...current.messages, userMessage] } : current);
    setDraft("");
    setLoading(true);
    setError(null);
    setToolNotice(null);
    try {
      const response = await agentApi.assist(query, conversation.id);
      const answer = response.modelAnswer?.trim() || response.summary || "暂时没有可确认的答案。";
      setConversation((current) => current ? { ...current, updatedAt: Date.now(), messages: [...current.messages, { id: createId(), role: "assistant", text: answer, response }] } : current);
    } catch (cause) {
      setError(cause instanceof CurmerceApiError ? cause.message : "助手暂时不可用，请稍后重试。");
    } finally {
      setLoading(false);
    }
  }

  async function confirmTool(call: AgentToolCall) {
    if (call.name !== "refund-request") return;
    const orderId = call.arguments?.orderId;
    const reason = displayText(call.arguments?.reason, "用户通过助手发起退款");
    if (typeof orderId !== "number" && typeof orderId !== "string") {
      setToolNotice("退款工具缺少有效订单号，已阻止执行。");
      return;
    }
    const target = String(orderId);
    setExecutingTool(call.id ?? target);
    setToolNotice(null);
    try {
      const issued = await agentApi.issueConfirmation("refund-request", target);
      await agentApi.execute("refund-request", { orderId: Number(orderId), reason }, issued.token);
      setToolNotice(`订单 ${target} 的退款请求已提交。`);
    } catch (cause) {
      setToolNotice(cause instanceof CurmerceApiError ? cause.message : "退款工具执行失败。");
    } finally {
      setExecutingTool(null);
    }
  }

  async function submitFeedback(messageId: string, helpful: boolean) {
    if (!conversation || !getAccessToken()) {
      setToolNotice("登录后才能提交反馈。");
      return;
    }
    setSendingFeedback(messageId);
    setToolNotice(null);
    try {
      await agentApi.feedback(conversation.id, messageId, helpful);
      setFeedbackByMessage((current) => ({ ...current, [messageId]: helpful }));
      setToolNotice("反馈已记录，将用于改进回答质量。");
    } catch (cause) {
      setToolNotice(cause instanceof CurmerceApiError ? cause.message : "反馈暂未提交成功，请稍后重试。");
    } finally {
      setSendingFeedback(null);
    }
  }

  return (
    <section className="content-section agent-page">
      <header className="agent-heading">
        <div className="agent-heading__icon"><Sparkles aria-hidden="true" size={24} /></div>
        <div><p className="eyebrow">CURMERCE · ASSISTANT</p><h1>购物助手</h1><p>基于商品、社区体验和你的订单信息，给出可追溯的消费建议。</p></div>
        <button className="button button--secondary button--icon-label" type="button" onClick={startConversation}><Plus aria-hidden="true" size={16} />新对话</button>
      </header>
      <div className="agent-layout">
        <aside className="agent-history" aria-label="对话历史">
          <div className="agent-panel-title"><MessageCircle aria-hidden="true" size={17} /><strong>最近对话</strong></div>
          {history.length ? <div className="agent-history-list">{history.map((item) => <button className={conversation?.id === item.id ? "agent-history-item agent-history-item--active" : "agent-history-item"} key={item.id} type="button" onClick={() => selectConversation(item.id)}><span>{item.title}</span><small>{new Date(item.updatedAt).toLocaleDateString("zh-CN")}</small></button>)}</div> : <p className="agent-muted">还没有历史对话</p>}
          <div className="agent-trust-note"><ShieldCheck aria-hidden="true" size={16} /><span>订单和退款工具始终按当前用户授权执行。</span></div>
        </aside>
        <div className="agent-conversation">
          {!hasMessages ? <div className="agent-empty"><Bot aria-hidden="true" size={34} /><h2>从一个具体问题开始</h2><p>例如：帮我找适合手冲咖啡的磨豆机，或查询我的订单状态。</p><div className="agent-prompts"><button type="button" onClick={() => setDraft("帮我找适合手冲咖啡的磨豆机")}>找商品</button><button type="button" onClick={() => setDraft("查询我最近的订单状态")}>查订单</button><button type="button" onClick={() => setDraft("平台退款规则是什么？")}>问规则</button></div></div> : <div className="agent-messages">{conversation?.messages.map((message) => <article className={message.role === "user" ? "agent-message agent-message--user" : "agent-message"} key={message.id}><div className="agent-message__avatar">{message.role === "user" ? "我" : <Bot aria-hidden="true" size={16} />}</div><div className="agent-message__body"><p className="agent-message__text">{message.text}</p>{message.response ? <ResponseDetails response={message.response} executingTool={executingTool} onConfirm={confirmTool} feedback={feedbackByMessage[message.id]} feedbackPending={sendingFeedback === message.id} onFeedback={(helpful) => submitFeedback(message.id, helpful)} /> : null}</div></article>)}</div>}
          {error ? <div className="notice notice--error"><AlertTriangle aria-hidden="true" size={17} />{error}<button type="button" aria-label="关闭错误提示" onClick={() => setError(null)}><X aria-hidden="true" size={15} /></button></div> : null}
          {toolNotice ? <div className="notice notice--info"><ShieldCheck aria-hidden="true" size={17} />{toolNotice}<button type="button" aria-label="关闭工具提示" onClick={() => setToolNotice(null)}><X aria-hidden="true" size={15} /></button></div> : null}
          <form className="agent-composer" onSubmit={send}><textarea aria-label="输入你的问题" value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="问问商品、社区体验、订单或平台规则…" rows={2} disabled={loading} /><button className="button button--primary" type="submit" disabled={!canSend}>{loading ? <Clock3 aria-hidden="true" size={17} /> : <Send aria-hidden="true" size={17} />}<span>{loading ? "检索中" : "发送"}</span></button></form>
          <p className="agent-disclaimer">助手只基于当前可用数据回答；涉及退款等敏感操作会在执行前再次确认。</p>
        </div>
      </div>
    </section>
  );
}

function ResponseDetails({ response, executingTool, onConfirm, feedback, feedbackPending, onFeedback }: { response: AgentAssistResponse; executingTool: string | null; onConfirm: (call: AgentToolCall) => void; feedback?: boolean; feedbackPending: boolean; onFeedback: (helpful: boolean) => void }) {
  const products = useMemo(() => listFromNode(response.products), [response.products]);
  const posts = useMemo(() => listFromNode(response.communityPosts), [response.communityPosts]);
  const references = useMemo<AgentSourceReference[]>(() => {
    if (response.references?.length) return response.references;
    return [
      ...products.map((item, index) => ({ source: "product", id: String(item.id ?? index), title: displayText(item.name, "未命名商品"), excerpt: displayText(item.description, "暂无商品描述"), path: item.id ? `/products/${item.id}` : null })),
      ...posts.map((item, index) => ({ source: "community", id: String(item.id ?? index), title: displayText(item.title, "社区分享"), excerpt: displayText(item.content, "暂无帖子摘要"), path: item.id ? `/community/${item.id}` : null })),
    ];
  }, [response.references, products, posts]);
  const calls = response.toolCalls ?? [];
  return <div className="agent-response-details">
    {!response.modelBacked ? <div className="agent-degraded"><RotateCcw aria-hidden="true" size={14} />当前使用检索摘要，模型回答暂不可用。</div> : null}
    {response.degradedSources?.length ? <div className="agent-degraded"><AlertTriangle aria-hidden="true" size={14} />部分来源不可用：{response.degradedSources.join("、")}</div> : null}
    {response.groundingWarnings?.length ? <div className="agent-warning-list">{response.groundingWarnings.map((warning) => <span key={warning}><AlertTriangle aria-hidden="true" size={13} />{warning}</span>)}</div> : null}
    {calls.filter((call) => call.name === "refund-request").map((call) => <div className="agent-confirm-card" key={call.id ?? call.name}><div><strong>需要确认退款操作</strong><small>助手准备处理订单 {displayText(call.arguments?.orderId, "未知")}，提交后仍以平台审核结果为准。</small></div><button className="button button--danger" type="button" disabled={Boolean(executingTool)} onClick={() => onConfirm(call)}>{executingTool ? "处理中" : "确认执行"}</button></div>)}
    {references.length ? <div className="agent-references"><div className="agent-panel-title"><ChevronRight aria-hidden="true" size={16} /><strong>参考内容</strong><small className="agent-reference-count">{references.length} 条可核对来源</small></div><div className="agent-reference-grid">{references.slice(0, 12).map((reference, index) => { const content = <><span className={reference.source === "product" ? "agent-reference__type" : reference.source === "community" ? "agent-reference__type agent-reference__type--post" : "agent-reference__type agent-reference__type--knowledge"}>{sourceLabel(reference.source)} · {reference.id}</span><strong>{reference.title}</strong><small>{reference.excerpt}</small></>; return reference.path ? <Link className="agent-reference" href={reference.path} key={`${reference.source}-${reference.id}-${index}`}>{content}</Link> : <div className="agent-reference" key={`${reference.source}-${reference.id}-${index}`}>{content}</div>; })}</div></div> : null}
    <div className="agent-feedback" aria-label="回答反馈"><span>这条回答有帮助吗？</span><button className={feedback === true ? "agent-feedback__button agent-feedback__button--selected" : "agent-feedback__button"} type="button" aria-label="有帮助" title="有帮助" disabled={feedbackPending} onClick={() => onFeedback(true)}><ThumbsUp aria-hidden="true" size={15} /></button><button className={feedback === false ? "agent-feedback__button agent-feedback__button--selected" : "agent-feedback__button"} type="button" aria-label="没有帮助" title="没有帮助" disabled={feedbackPending} onClick={() => onFeedback(false)}><ThumbsDown aria-hidden="true" size={15} /></button>{feedbackPending ? <small>提交中</small> : feedback !== undefined ? <small><Check aria-hidden="true" size={13} />已记录</small> : null}</div>
    {response.usage ? <small className="agent-usage">{response.usage.provider} · {response.usage.promptTokens + response.usage.completionTokens} tokens · {response.usage.latencyMillis} ms</small> : null}
  </div>;
}
