// src/pages/shopping-guide/hooks/guideWorkbenchModel.test.ts
import test from "node:test";
import assert from "node:assert/strict";

// src/pages/shopping-guide/hooks/guideWorkbenchModel.ts
function applyAgentPlan(items, payload) {
  return upsertTimelineItem(items, payload.runId, payload.stepNo, (existing) => ({
    ...existing,
    action: payload.action || existing?.action || "\u89C4\u5212\u4E0B\u4E00\u6B65",
    thought: payload.thought ?? existing?.thought ?? null,
    arguments: payload.arguments ?? existing?.arguments,
    status: terminalStatus(existing?.status) ? existing?.status || "running" : "running"
  }));
}
function applyToolCall(items, payload) {
  return upsertTimelineItem(items, payload.runId, payload.stepNo, (existing) => ({
    ...existing,
    action: existing?.action || payload.toolName || "\u8C03\u7528\u5DE5\u5177",
    toolName: payload.toolName || existing?.toolName,
    arguments: payload.argumentsSummary ?? existing?.arguments,
    status: "running"
  }));
}
function applyToolObservation(items, payload) {
  return upsertTimelineItem(items, payload.runId, payload.stepNo, (existing) => ({
    ...existing,
    action: existing?.action || payload.toolName || "\u5DE5\u5177\u8FD4\u56DE",
    toolName: payload.toolName || existing?.toolName,
    observation: payload.observation ?? existing?.observation ?? null,
    durationMs: payload.durationMs ?? existing?.durationMs ?? null,
    status: normalizeTimelineStatus(payload.status),
    error: payload.error ?? existing?.error ?? null
  }));
}
function applyAgentFinish(items, payload) {
  const finalStatus = normalizeTimelineStatus(payload.status);
  return items.map((item) => {
    if (item.runId !== payload.runId || terminalStatus(item.status)) {
      return item;
    }
    return {
      ...item,
      status: finalStatus === "failed" || finalStatus === "cancelled" ? finalStatus : "success",
      observation: item.observation || payload.finalAction || null
    };
  });
}
function applyAgentCancel(items, runId) {
  if (!runId) return items;
  return items.map((item) => item.runId === runId && !terminalStatus(item.status) ? { ...item, status: "cancelled" } : item);
}
function stepsToTimeline(steps) {
  return [...steps].sort((left, right) => (left.stepNo || 0) - (right.stepNo || 0)).map((step) => ({
    id: step.id || timelineId(step.runId, step.stepNo || 0),
    runId: step.runId,
    stepNo: step.stepNo || 0,
    action: step.action || "Agent Step",
    thought: step.thought || null,
    observation: step.observation || null,
    durationMs: step.durationMs ?? null,
    status: normalizeTimelineStatus(step.status),
    arguments: parseJsonObject(step.argumentsJson),
    error: step.errorMessage || null,
    createdAt: step.createTime || null
  }));
}
function hydrateGuideWorkbenchFromSession(detail) {
  const state = detail.state || null;
  const recommendations = state?.recommendations?.length ? state.recommendations : detail.recommendations || [];
  const products = recommendations.map(recommendationToProduct);
  const stateCitations = collectRecommendationCitations(recommendations);
  const looseCitations = (state?.evidences || []).map((evidence) => evidenceToCitation(evidence));
  const currentRunId = latestRunId(detail);
  return {
    currentRunId,
    intent: state?.intent || intentFromSession(detail),
    products,
    citations: uniqueCitations([...stateCitations, ...looseCitations])
  };
}
function analyzeBusinessSignalCoverage(products, citations, intent, timeline, evaluationMetrics) {
  const hasProductSignals = products.some((product) => Boolean(product.productId));
  const hasToolSignals = timeline.some((item) => Boolean(item.toolName || item.action));
  const hasBusinessData = hasProductSignals && (citations.length > 0 || hasToolSignals);
  const hasIntentUnderstanding = Boolean(
    intent && (intent.intentType !== "unknown" || intent.category || intent.budgetMin != null || intent.budgetMax != null || intent.hardConstraints?.length || intent.softPreferences?.length)
  );
  const hasPrice = products.some((product) => product.priceMin != null || product.priceMax != null);
  const hasStock = products.some((product) => Boolean(product.stockStatus && product.stockStatus !== "unknown"));
  const hasPromotion = products.some((product) => Boolean(product.promotions?.length || product.promotionCount));
  const hasPriceStockPromotion = hasPrice && hasStock && hasPromotion;
  const hasExplainableReasons = products.some((product) => product.reasons?.length) && (citations.length > 0 || products.some((product) => product.evidences?.length));
  const hasEvaluationSignals = timeline.length > 0 || Boolean(evaluationMetrics && Object.keys(evaluationMetrics).length > 0);
  const missingSignals = [
    !hasBusinessData ? "\u771F\u5B9E\u4E1A\u52A1\u6570\u636E" : null,
    !hasIntentUnderstanding ? "\u8D2D\u4E70\u610F\u56FE" : null,
    !hasPriceStockPromotion ? "\u4EF7\u683C/\u5E93\u5B58/\u4F18\u60E0" : null,
    !hasExplainableReasons ? "\u63A8\u8350\u7406\u7531\u4E0E\u8BC1\u636E" : null,
    !hasEvaluationSignals ? "\u8BC4\u6D4B\u6307\u6807\u6216\u8FD0\u884C\u8F68\u8FF9" : null
  ].filter(Boolean);
  return {
    hasBusinessData,
    hasIntentUnderstanding,
    hasPriceStockPromotion,
    hasExplainableReasons,
    hasEvaluationSignals,
    missingSignals
  };
}
function buildReasonableFallbackReply(input) {
  const normalized = input.trim();
  if (!normalized || /^[\s?!？。,.，、~·…-]+$/.test(normalized)) {
    return "\u6211\u4F1A\u5148\u5E2E\u4F60\u6F84\u6E05\u4E00\u4E0B\uFF1A\u4F60\u53EF\u4EE5\u544A\u8BC9\u6211\u60F3\u4E70\u7684\u54C1\u7C7B\u3001\u9884\u7B97\u548C\u4F7F\u7528\u573A\u666F\uFF1B\u5982\u679C\u53EA\u53D1\u56FE\u7247\uFF0C\u6211\u4F1A\u5148\u8BC6\u522B\u5546\u54C1\u7C7B\u578B\uFF0C\u518D\u7ED3\u5408\u4EF7\u683C\u3001\u5E93\u5B58\u548C\u4F18\u60E0\u7ED9\u51FA\u5EFA\u8BAE\u3002";
  }
  return `\u6211\u5DF2\u7ECF\u6536\u5230\u201C${abbreviate(normalized, 28)}\u201D\u3002\u4E3A\u4E86\u7ED9\u51FA\u53EF\u9760\u63A8\u8350\uFF0C\u6211\u4F1A\u5148\u8BC6\u522B\u54C1\u7C7B\u3001\u9884\u7B97\u3001\u7528\u9014\u548C\u786C\u6027\u7EA6\u675F\uFF0C\u518D\u7528\u5546\u54C1\u5E93\u91CC\u7684\u4EF7\u683C\u3001\u5E93\u5B58\u3001\u4F18\u60E0\u548C\u8BC1\u636E\u505A\u6392\u5E8F\u3002`;
}
function createGuideOutboundText(input, imageCount = 0) {
  const normalized = input.trim();
  if (normalized) {
    return normalized;
  }
  if (imageCount > 0) {
    return "\u8BF7\u6839\u636E\u6211\u4E0A\u4F20\u7684\u56FE\u7247\u8BC6\u522B\u5546\u54C1\uFF0C\u5E76\u7ED3\u5408\u771F\u5B9E\u5546\u54C1\u6570\u636E\u7ED9\u51FA\u8D2D\u4E70\u5EFA\u8BAE\u3002";
  }
  return "";
}
function upsertTimelineItem(items, runId, stepNo, build) {
  const id = timelineId(runId, stepNo);
  const index = items.findIndex((item) => item.runId === runId && item.stepNo === stepNo);
  const existing = index >= 0 ? items[index] : void 0;
  const next = {
    id,
    runId,
    stepNo,
    action: "Agent Step",
    status: "running",
    ...existing,
    ...build(existing)
  };
  if (index < 0) {
    return [...items, next].sort((left, right) => left.stepNo - right.stepNo);
  }
  return items.map((item, itemIndex) => itemIndex === index ? next : item);
}
function timelineId(runId, stepNo) {
  return `${runId}-${stepNo}`;
}
function terminalStatus(status) {
  return status === "success" || status === "failed" || status === "cancelled";
}
function normalizeTimelineStatus(status) {
  const normalized = String(status || "").toLowerCase();
  if (["succeeded", "success", "completed", "complete"].includes(normalized)) return "success";
  if (["failed", "error", "timeout"].includes(normalized)) return "failed";
  if (["cancelled", "canceled"].includes(normalized)) return "cancelled";
  return normalized || "running";
}
function parseJsonObject(value) {
  if (!value) return void 0;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : void 0;
  } catch {
    return void 0;
  }
}
function latestRunId(detail) {
  const fromMessages = [...detail.messages || []].reverse().find((message) => Boolean(message.agentRunId))?.agentRunId || null;
  return fromMessages || detail.state?.agentRunId || detail.runId || null;
}
function intentFromSession(detail) {
  if (!detail.intent) return null;
  return {
    intentType: detail.intent
  };
}
function recommendationToProduct(recommendation) {
  const evidences = (recommendation.evidences || []).map((evidence) => evidenceToCitation(evidence, recommendation.productId));
  return {
    productId: recommendation.productId,
    name: recommendation.name || `\u5546\u54C1 ${abbreviate(recommendation.productId, 8)}`,
    brand: recommendation.brand || null,
    priceMin: toNumber(recommendation.priceMin),
    priceMax: toNumber(recommendation.priceMax),
    imageUrl: recommendation.imageUrl || null,
    stockStatus: recommendation.stockStatus || null,
    promotions: recommendation.promotions || [],
    promotionCount: recommendation.promotionCount ?? recommendation.promotions?.length ?? null,
    score: toNumber(recommendation.score),
    reasons: recommendation.reasons || [],
    recommendationRole: recommendation.recommendationRole || null,
    scoreBreakdown: recommendation.scoreBreakdown || {},
    riskFlags: recommendation.riskFlags || [],
    evidences
  };
}
function collectRecommendationCitations(recommendations) {
  return recommendations.flatMap((recommendation) => (recommendation.evidences || []).map((evidence) => evidenceToCitation(evidence, recommendation.productId)));
}
function evidenceToCitation(evidence, fallbackProductId) {
  const documentId = evidence.documentId || "unknown-document";
  const chunkId = evidence.chunkId || `chunk-${evidence.chunkIndex ?? 0}`;
  const snippet = evidence.text || evidence.snippet || evidence.highlight || "";
  return {
    productId: evidence.productId || fallbackProductId || null,
    documentId,
    chunkId,
    docType: evidence.docType || null,
    chunkIndex: evidence.chunkIndex ?? null,
    sourceType: evidence.sourceType || null,
    highlight: evidence.highlight || null,
    evidenceType: evidence.evidenceType || null,
    score: toNumber(evidence.score),
    snippet,
    text: evidence.text || null,
    scoreBreakdown: evidence.scoreBreakdown || {}
  };
}
function uniqueCitations(citations) {
  const seen = /* @__PURE__ */ new Set();
  return citations.filter((citation) => {
    const key = `${citation.productId || ""}:${citation.documentId}:${citation.chunkId}:${citation.snippet}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
function toNumber(value) {
  if (value == null) return null;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}
function abbreviate(value, length) {
  return value.length <= length ? value : `${value.slice(0, length)}...`;
}

// src/pages/shopping-guide/hooks/guideWorkbenchModel.test.ts
test("agent timeline reducer keeps plan, tool call and observation as one step", () => {
  const planned = applyAgentPlan([], {
    runId: "run-1",
    stepNo: 1,
    thought: "need business data",
    action: "search_products",
    arguments: { category: "\u8033\u673A", budgetMax: 800 }
  });
  const running = applyToolCall(planned, {
    runId: "run-1",
    stepNo: 1,
    toolName: "product_search",
    argumentsSummary: { filters: ["price", "stock"] }
  });
  const observed = applyToolObservation(running, {
    runId: "run-1",
    stepNo: 1,
    toolName: "product_search",
    observation: "matched=3 with coupon=2",
    durationMs: 128,
    status: "succeeded"
  });
  assert.equal(observed.length, 1);
  assert.equal(observed[0].action, "search_products");
  assert.equal(observed[0].toolName, "product_search");
  assert.equal(observed[0].status, "success");
  assert.equal(observed[0].durationMs, 128);
  assert.deepEqual(observed[0].arguments, { filters: ["price", "stock"] });
});
test("agent finish and cancel events close running steps without losing errors", () => {
  const running = applyToolCall(
    applyAgentPlan([], { runId: "run-2", stepNo: 1, action: "rank_products" }),
    { runId: "run-2", stepNo: 1, toolName: "ranker" }
  );
  const failed = applyToolObservation(running, {
    runId: "run-2",
    stepNo: 1,
    toolName: "ranker",
    observation: "coupon service timeout",
    durationMs: 3e3,
    status: "failed",
    error: "timeout"
  });
  const finished = applyAgentFinish(failed, {
    runId: "run-2",
    status: "failed",
    totalSteps: 1,
    finalAction: "fallback_answer"
  });
  assert.equal(finished[0].status, "failed");
  assert.equal(finished[0].error, "timeout");
  const cancelled = applyAgentCancel(running, "run-2");
  assert.equal(cancelled[0].status, "cancelled");
});
test("server session detail hydrates real intent, recommendations, citations and latest run id", () => {
  const detail = {
    sessionId: "session-1",
    conversationId: "conversation-1",
    title: "\u8033\u673A\u63A8\u8350",
    lastTime: "2026-05-13T10:00:00.000Z",
    messages: [
      { id: "m1", role: "user", content: "800 \u5185\u901A\u52E4\u964D\u566A\u8033\u673A", createTime: "2026-05-13T10:00:00.000Z" },
      {
        id: "m2",
        role: "assistant",
        content: "\u63A8\u8350 A",
        createTime: "2026-05-13T10:00:01.000Z",
        agentRunId: "run-9"
      }
    ],
    state: {
      intent: {
        intentType: "recommend",
        category: "\u964D\u566A\u8033\u673A",
        budgetMax: 800,
        hardConstraints: ["\u901A\u52E4"],
        softPreferences: ["\u7EED\u822A\u957F"],
        confidence: 0.91
      },
      recommendations: [
        {
          productId: "p1",
          name: "Quiet Go",
          brand: "Acme",
          priceMin: 699,
          priceMax: 799,
          stockStatus: "in_stock",
          promotions: ["\u6EE1 700 \u51CF 80"],
          promotionCount: 1,
          score: 92,
          reasons: ["\u9884\u7B97\u5185", "\u6709\u73B0\u8D27", "\u4F18\u60E0\u540E\u6027\u4EF7\u6BD4\u9AD8"],
          evidences: [
            {
              productId: "p1",
              documentId: "doc-1",
              chunkId: "chunk-1",
              score: 0.87,
              text: "\u652F\u6301\u4E3B\u52A8\u964D\u566A\uFF0C\u5F53\u524D\u6709\u4F18\u60E0\u5238\u3002"
            }
          ]
        }
      ]
    }
  };
  const hydrated = hydrateGuideWorkbenchFromSession(detail);
  assert.equal(hydrated.currentRunId, "run-9");
  assert.equal(hydrated.intent?.category, "\u964D\u566A\u8033\u673A");
  assert.equal(hydrated.products[0].priceMin, 699);
  assert.equal(hydrated.products[0].promotions?.[0], "\u6EE1 700 \u51CF 80");
  assert.equal(hydrated.citations[0].documentId, "doc-1");
});
test("business signal coverage proves recommendations are not model-only answers", () => {
  const coverage = analyzeBusinessSignalCoverage(
    [
      {
        productId: "p1",
        name: "Quiet Go",
        priceMin: 699,
        priceMax: 799,
        stockStatus: "in_stock",
        promotions: ["\u6EE1\u51CF\u5238"],
        score: 90,
        reasons: ["\u4EF7\u683C\u6EE1\u8DB3\u9884\u7B97", "\u5E93\u5B58\u53EF\u4E70", "\u4F18\u60E0\u540E\u5212\u7B97"]
      }
    ],
    [{ productId: "p1", documentId: "doc", chunkId: "chunk", snippet: "\u5546\u54C1\u8BC1\u636E" }],
    { intentType: "recommend", category: "\u8033\u673A", confidence: 0.8 },
    [{ id: "run-1-1", runId: "run-1", stepNo: 1, action: "search", status: "success" }]
  );
  assert.equal(coverage.hasBusinessData, true);
  assert.equal(coverage.hasIntentUnderstanding, true);
  assert.equal(coverage.hasPriceStockPromotion, true);
  assert.equal(coverage.hasExplainableReasons, true);
  assert.equal(coverage.hasEvaluationSignals, true);
});
test("persisted agent steps convert to replayable timeline with parsed arguments", () => {
  const steps = [
    {
      id: "s1",
      runId: "run-1",
      stepNo: 2,
      action: "retrieve_evidence",
      thought: "need citations",
      argumentsJson: '{"productId":"p1"}',
      observation: "citations=2",
      status: "succeeded",
      durationMs: 64,
      createTime: "2026-05-13T10:00:00.000Z"
    }
  ];
  const timeline = stepsToTimeline(steps);
  assert.equal(timeline[0].status, "success");
  assert.deepEqual(timeline[0].arguments, { productId: "p1" });
});
test("fallback reply is useful for unclear or interrupted messages", () => {
  assert.match(buildReasonableFallbackReply("???"), /我会先帮你澄清/);
  assert.match(buildReasonableFallbackReply("5000\u4EE5\u5185\u7B14\u8BB0\u672C"), /预算|品类|用途/);
});
test("outbound text keeps image-only turns understandable to the backend", () => {
  assert.equal(
    createGuideOutboundText("", 2),
    "\u8BF7\u6839\u636E\u6211\u4E0A\u4F20\u7684\u56FE\u7247\u8BC6\u522B\u5546\u54C1\uFF0C\u5E76\u7ED3\u5408\u771F\u5B9E\u5546\u54C1\u6570\u636E\u7ED9\u51FA\u8D2D\u4E70\u5EFA\u8BAE\u3002"
  );
  assert.equal(createGuideOutboundText("  \u9884\u7B97 300 \u8033\u673A  ", 0), "\u9884\u7B97 300 \u8033\u673A");
});
