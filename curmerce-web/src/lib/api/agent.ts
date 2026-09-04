import { appApi } from "@/lib/api/client";

export interface AgentToolCall {
  id?: string;
  name: string;
  arguments?: Record<string, unknown>;
}

export interface AgentToolResult {
  callId?: string;
  name: string;
  success: boolean;
  content?: string;
}

export interface AgentUsage {
  promptTokens: number;
  completionTokens: number;
  latencyMillis: number;
  cost: number;
  provider: string;
}

export interface AgentAssistResponse {
  query: string;
  summary: string;
  products?: unknown;
  communityPosts?: unknown;
  degradedSources: string[];
  modelBacked: boolean;
  modelAnswer?: string | null;
  usage?: AgentUsage | null;
  toolCalls?: AgentToolCall[] | null;
  toolResults?: AgentToolResult[] | null;
  groundingWarnings?: string[] | null;
  references?: AgentSourceReference[] | null;
}

export interface AgentSourceReference {
  source: string;
  id: string;
  title: string;
  excerpt: string;
  path?: string | null;
}

export interface AgentConfirmation {
  token: string;
  expiresInSeconds: number;
}

export const agentApi = {
  assist(query: string, conversationId?: string) {
    return appApi<AgentAssistResponse>("/agent/assist", {
      method: "POST",
      body: JSON.stringify({ query, conversationId }),
    });
  },
  issueConfirmation(action: string, target: string) {
    return appApi<AgentConfirmation>("/agent/confirmations", {
      method: "POST",
      body: JSON.stringify({ action, target }),
    });
  },
  execute(tool: string, argumentsValue: Record<string, unknown>, confirmationToken?: string) {
    return appApi<unknown>("/agent/execute", {
      method: "POST",
      body: JSON.stringify({ tool, arguments: argumentsValue, confirmationToken }),
    });
  },
  feedback(conversationId: string, messageId: string, helpful: boolean, category = "answer") {
    return appApi<boolean>("/agent/feedback", {
      method: "POST",
      body: JSON.stringify({ conversationId, messageId, helpful, category }),
    });
  },
  capabilities() {
    return appApi<Record<string, unknown>>("/agent/capabilities");
  },
};
