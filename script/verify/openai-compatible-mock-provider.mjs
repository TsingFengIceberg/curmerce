#!/usr/bin/env node
"use strict";

// Local-only, deterministic OpenAI-compatible provider used by integration
// checks. It intentionally has no external dependency, credential, logging of
// request bodies, or non-loopback listener.
import http from "node:http";

const host = process.env.CURMERCE_AGENT_MOCK_HOST || process.env.CURMERCE_AGENT_MOCK_PROVIDER_HOST || "127.0.0.1";
const port = Number.parseInt(process.env.CURMERCE_AGENT_MOCK_PORT || process.env.CURMERCE_AGENT_MOCK_PROVIDER_PORT || "48185", 10);
const model = process.env.CURMERCE_AGENT_MOCK_MODEL || "curmerce-mock";
const embeddingModel = process.env.CURMERCE_AGENT_MOCK_EMBEDDING_MODEL || "curmerce-mock-embedding";
const expectedKey = process.env.CURMERCE_AGENT_MOCK_API_KEY || "";
const maxBodyBytes = 1024 * 1024;
const chatFailureMode = (process.env.CURMERCE_AGENT_MOCK_CHAT_FAILURE || "none").trim().toLowerCase();
const chatFailureDelayMs = Math.max(0, Number.parseInt(process.env.CURMERCE_AGENT_MOCK_CHAT_DELAY_MS || "0", 10) || 0);
const failureSequence = (process.env.CURMERCE_AGENT_MOCK_CHAT_FAILURE_SEQUENCE || "")
  .split(",").map((value) => value.trim().toLowerCase()).filter(Boolean);
let failureSequenceIndex = 0;

if (!Number.isInteger(port) || port < 1024 || port > 65535) {
  throw new Error("CURMERCE_AGENT_MOCK_PORT must be a non-privileged TCP port");
}

function json(response, status, value) {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  response.end(JSON.stringify(value));
}

function authorized(request) {
  return !expectedKey || request.headers.authorization === `Bearer ${expectedKey}`;
}

function vector(value) {
  const result = Array(8).fill(0);
  for (let index = 0; index < value.length; index += 1) {
    result[index % result.length] += value.charCodeAt(index) % 97;
  }
  const length = Math.hypot(...result) || 1;
  return result.map((entry) => Number((entry / length).toFixed(8)));
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size > maxBodyBytes) {
        reject(new Error("request body too large"));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on("end", () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}"));
      } catch {
        reject(new Error("invalid JSON request"));
      }
    });
    request.on("error", reject);
  });
}

function completion(body) {
  const messages = Array.isArray(body.messages) ? body.messages : [];
  const hasToolResult = messages.some((message) => message && message.role === "tool");
  const user = [...messages].reverse().find((message) => message && message.role === "user");
  const prompt = typeof user?.content === "string" ? user.content : "";
  let message;
  if (prompt.includes("只回复 OK，不调用任何工具。")) {
    message = { role: "assistant", content: "OK" };
  } else if (hasToolResult) {
    message = { role: "assistant", content: "已根据已授权的工具结果完成查询。" };
  } else {
    // The platform-rule tool is read-only and needs no user-specific data. It
    // exercises the Agent's standard tool transcript and result loop.
    message = {
      role: "assistant",
      content: null,
      tool_calls: [{
        id: "mock-platform-rules-1",
        type: "function",
        function: { name: "platform-rules", arguments: "{}" },
      }],
    };
  }
  return {
    id: "chatcmpl-curmerce-mock",
    object: "chat.completion",
    created: Math.floor(Date.now() / 1000),
    model: typeof body.model === "string" && body.model ? body.model : model,
    choices: [{ index: 0, finish_reason: message.tool_calls ? "tool_calls" : "stop", message }],
    usage: { prompt_tokens: Math.max(1, Math.ceil(prompt.length / 4)), completion_tokens: 8, total_tokens: 8 },
  };
}

function failureMode(request) {
  const header = request.headers["x-curmerce-mock-failure"];
  if (typeof header === "string" && header.trim()) return header.trim().toLowerCase();
  if (failureSequenceIndex < failureSequence.length) return failureSequence[failureSequenceIndex++];
  return chatFailureMode;
}

function sendChatFailure(response, mode) {
  if (mode === "429" || mode === "rate-limit") {
    json(response, 429, { error: { message: "mock rate limited" } });
    return true;
  }
  if (mode === "500" || mode === "server-error") {
    json(response, 500, { error: { message: "mock provider unavailable" } });
    return true;
  }
  if (mode === "bad-json" || mode === "invalid-json") {
    response.writeHead(200, { "content-type": "application/json; charset=utf-8" });
    response.end("{not-json");
    return true;
  }
  if (mode === "oversized" || mode === "too-large") {
    response.writeHead(200, { "content-type": "application/json; charset=utf-8" });
    response.end("x".repeat(maxBodyBytes + 1));
    return true;
  }
  return false;
}

const server = http.createServer(async (request, response) => {
  const path = new URL(request.url || "/", `http://${host}`).pathname;
  if (!authorized(request)) {
    json(response, 401, { error: { message: "invalid API key" } });
    return;
  }
  if (request.method === "GET" && path === "/v1/models") {
    json(response, 200, { object: "list", data: [{ id: model, object: "model" }, { id: embeddingModel, object: "model" }] });
    return;
  }
  if (request.method !== "POST" || (path !== "/v1/chat/completions" && path !== "/v1/embeddings")) {
    json(response, 404, { error: { message: "not found" } });
    return;
  }
  try {
    const body = await readJson(request);
    if (path === "/v1/chat/completions") {
      const mode = failureMode(request);
      if (chatFailureDelayMs > 0 || mode === "timeout") {
        const delay = chatFailureDelayMs > 0 ? chatFailureDelayMs : 10_000;
        setTimeout(() => {
          if (!response.writableEnded && !sendChatFailure(response, mode)) json(response, 200, completion(body));
        }, delay);
        return;
      }
      if (sendChatFailure(response, mode)) return;
      json(response, 200, completion(body));
      return;
    }
    const inputs = Array.isArray(body.input) ? body.input : [body.input];
    json(response, 200, {
      object: "list",
      model: typeof body.model === "string" && body.model ? body.model : embeddingModel,
      data: inputs.map((input, index) => ({ object: "embedding", index, embedding: vector(String(input ?? "")) })),
      usage: { prompt_tokens: inputs.length, total_tokens: inputs.length },
    });
  } catch (error) {
    json(response, 400, { error: { message: error.message === "request body too large" ? error.message : "invalid request" } });
  }
});

server.listen(port, host, () => process.stdout.write(`Curmerce mock provider listening on http://${host}:${port}/v1\n`));
function close() { server.close(() => process.exit(0)); }
process.on("SIGINT", close);
process.on("SIGTERM", close);
