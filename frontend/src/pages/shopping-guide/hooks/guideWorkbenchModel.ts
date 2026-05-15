import type {
  AgentStepItem,
  GuideAgentFinishPayload,
  GuideAgentPlanPayload,
  GuideAgentTimelineItem,
  GuideBusinessSignalCoverage,
  GuideCitation,
  GuideIntentPayload,
  GuidePersistedEvidence,
  GuidePersistedRecommendation,
  GuideProductCard,
  GuideSessionDetail,
  GuideToolCallPayload,
  GuideToolObservationPayload,
} from '../../../types';

export interface HydratedGuideWorkbench {
  currentRunId: string | null;
  intent: GuideIntentPayload | null;
  products: GuideProductCard[];
  citations: GuideCitation[];
}

export function applyAgentPlan(items: GuideAgentTimelineItem[], payload: GuideAgentPlanPayload) {
  return upsertTimelineItem(items, payload.runId, payload.stepNo, (existing) => ({
    ...existing,
    action: payload.action || existing?.action || '规划下一步',
    thought: payload.thought ?? existing?.thought ?? null,
    arguments: payload.arguments ?? existing?.arguments,
    status: terminalStatus(existing?.status) ? existing?.status || 'running' : 'running',
  }));
}

export function applyToolCall(items: GuideAgentTimelineItem[], payload: GuideToolCallPayload) {
  return upsertTimelineItem(items, payload.runId, payload.stepNo, (existing) => ({
    ...existing,
    action: existing?.action || payload.toolName || '调用工具',
    toolName: payload.toolName || existing?.toolName,
    arguments: payload.argumentsSummary ?? existing?.arguments,
    status: 'running',
  }));
}

export function applyToolObservation(items: GuideAgentTimelineItem[], payload: GuideToolObservationPayload) {
  return upsertTimelineItem(items, payload.runId, payload.stepNo, (existing) => ({
    ...existing,
    action: existing?.action || payload.toolName || '工具返回',
    toolName: payload.toolName || existing?.toolName,
    observation: payload.observation ?? existing?.observation ?? null,
    durationMs: payload.durationMs ?? existing?.durationMs ?? null,
    status: normalizeTimelineStatus(payload.status),
    error: payload.error ?? existing?.error ?? null,
  }));
}

export function applyAgentFinish(items: GuideAgentTimelineItem[], payload: GuideAgentFinishPayload) {
  const finalStatus = normalizeTimelineStatus(payload.status);
  return items.map((item) => {
    if (item.runId !== payload.runId || terminalStatus(item.status)) {
      return item;
    }
    return {
      ...item,
      status: finalStatus === 'failed' || finalStatus === 'cancelled' ? finalStatus : 'success',
      observation: item.observation || payload.finalAction || null,
    };
  });
}

export function applyAgentCancel(items: GuideAgentTimelineItem[], runId?: string | null) {
  if (!runId) return items;
  return items.map((item) => (
    item.runId === runId && !terminalStatus(item.status)
      ? { ...item, status: 'cancelled' }
      : item
  ));
}

export function stepsToTimeline(steps: AgentStepItem[]) {
  return [...steps]
    .sort((left, right) => (left.stepNo || 0) - (right.stepNo || 0))
    .map((step) => ({
      id: step.id || timelineId(step.runId, step.stepNo || 0),
      runId: step.runId,
      stepNo: step.stepNo || 0,
      action: step.action || 'Agent Step',
      thought: step.thought || null,
      observation: step.observation || null,
      durationMs: step.durationMs ?? null,
      status: normalizeTimelineStatus(step.status),
      arguments: parseJsonObject(step.argumentsJson),
      error: step.errorMessage || null,
      createdAt: step.createTime || null,
    }));
}

export function hydrateGuideWorkbenchFromSession(detail: GuideSessionDetail): HydratedGuideWorkbench {
  const state = detail.state || null;
  const recommendations = state?.recommendations?.length
    ? state.recommendations
    : detail.recommendations || [];
  const products = recommendations.map(recommendationToProduct);
  const stateCitations = collectRecommendationCitations(recommendations);
  const looseCitations = (state?.evidences || []).map((evidence) => evidenceToCitation(evidence));
  const currentRunId = latestRunId(detail);

  return {
    currentRunId,
    intent: state?.intent || intentFromSession(detail),
    products,
    citations: uniqueCitations([...stateCitations, ...looseCitations]),
  };
}

export function analyzeBusinessSignalCoverage(
  products: GuideProductCard[],
  citations: GuideCitation[],
  intent: GuideIntentPayload | null,
  timeline: GuideAgentTimelineItem[],
  evaluationMetrics?: Record<string, unknown> | null,
): GuideBusinessSignalCoverage {
  const hasProductSignals = products.some((product) => Boolean(product.productId));
  const hasToolSignals = timeline.some((item) => Boolean(item.toolName || item.action));
  const hasBusinessData = hasProductSignals && (citations.length > 0 || hasToolSignals);
  const hasIntentUnderstanding = Boolean(
    intent && (
      intent.intentType !== 'unknown'
      || intent.category
      || intent.budgetMin != null
      || intent.budgetMax != null
      || intent.hardConstraints?.length
      || intent.softPreferences?.length
    ),
  );
  const hasPrice = products.some((product) => product.priceMin != null || product.priceMax != null);
  const hasStock = products.some((product) => Boolean(product.stockStatus && product.stockStatus !== 'unknown'));
  const hasPromotion = products.some((product) => Boolean(product.promotions?.length || product.promotionCount));
  const hasPriceStockPromotion = hasPrice && hasStock && hasPromotion;
  const hasExplainableReasons = products.some((product) => product.reasons?.length)
    && (citations.length > 0 || products.some((product) => product.evidences?.length));
  const hasEvaluationSignals = timeline.length > 0 || Boolean(evaluationMetrics && Object.keys(evaluationMetrics).length > 0);
  const missingSignals = [
    !hasBusinessData ? '真实业务数据' : null,
    !hasIntentUnderstanding ? '购买意图' : null,
    !hasPriceStockPromotion ? '价格/库存/优惠' : null,
    !hasExplainableReasons ? '推荐理由与证据' : null,
    !hasEvaluationSignals ? '评测指标或运行轨迹' : null,
  ].filter(Boolean) as string[];

  return {
    hasBusinessData,
    hasIntentUnderstanding,
    hasPriceStockPromotion,
    hasExplainableReasons,
    hasEvaluationSignals,
    missingSignals,
  };
}

export function buildReasonableFallbackReply(input: string) {
  const normalized = input.trim();
  if (!normalized || /^[\s?!？。,.，、~·…-]+$/.test(normalized)) {
    return '我会先帮你澄清一下：你可以告诉我想买的品类、预算和使用场景；如果只发图片，我会先识别商品类型，再结合价格、库存和优惠给出建议。';
  }
  return `我已经收到“${abbreviate(normalized, 28)}”。为了给出可靠推荐，我会先识别品类、预算、用途和硬性约束，再用商品库里的价格、库存、优惠和证据做排序。`;
}

export function createGuideOutboundText(input: string, imageCount = 0) {
  const normalized = input.trim();
  if (normalized) {
    return normalized;
  }
  if (imageCount > 0) {
    return '请根据我上传的图片识别商品，并结合真实商品数据给出购买建议。';
  }
  return '';
}

function upsertTimelineItem(
  items: GuideAgentTimelineItem[],
  runId: string,
  stepNo: number,
  build: (existing?: GuideAgentTimelineItem) => Partial<GuideAgentTimelineItem>,
) {
  const id = timelineId(runId, stepNo);
  const index = items.findIndex((item) => item.runId === runId && item.stepNo === stepNo);
  const existing = index >= 0 ? items[index] : undefined;
  const next: GuideAgentTimelineItem = {
    id,
    runId,
    stepNo,
    action: 'Agent Step',
    status: 'running',
    ...existing,
    ...build(existing),
  };
  if (index < 0) {
    return [...items, next].sort((left, right) => left.stepNo - right.stepNo);
  }
  return items.map((item, itemIndex) => (itemIndex === index ? next : item));
}

function timelineId(runId: string, stepNo: number) {
  return `${runId}-${stepNo}`;
}

function terminalStatus(status?: string) {
  return status === 'success' || status === 'failed' || status === 'cancelled';
}

function normalizeTimelineStatus(status?: string | null) {
  const normalized = String(status || '').toLowerCase();
  if (['succeeded', 'success', 'completed', 'complete'].includes(normalized)) return 'success';
  if (['failed', 'error', 'timeout'].includes(normalized)) return 'failed';
  if (['cancelled', 'canceled'].includes(normalized)) return 'cancelled';
  return normalized || 'running';
}

function parseJsonObject(value?: string | null) {
  if (!value) return undefined;
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : undefined;
  } catch {
    return undefined;
  }
}

function latestRunId(detail: GuideSessionDetail) {
  const fromMessages = [...(detail.messages || [])]
    .reverse()
    .find((message) => Boolean(message.agentRunId))
    ?.agentRunId || null;
  return fromMessages || detail.state?.agentRunId || detail.runId || null;
}

function intentFromSession(detail: GuideSessionDetail): GuideIntentPayload | null {
  if (!detail.intent) return null;
  return {
    intentType: detail.intent,
  };
}

function recommendationToProduct(recommendation: GuidePersistedRecommendation): GuideProductCard {
  const evidences = (recommendation.evidences || []).map((evidence) => evidenceToCitation(evidence, recommendation.productId));
  return {
    productId: recommendation.productId,
    name: recommendation.name || `商品 ${abbreviate(recommendation.productId, 8)}`,
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
    evidences,
  };
}

function collectRecommendationCitations(recommendations: GuidePersistedRecommendation[]) {
  return recommendations.flatMap((recommendation) => (
    recommendation.evidences || []
  ).map((evidence) => evidenceToCitation(evidence, recommendation.productId)));
}

function evidenceToCitation(evidence: GuidePersistedEvidence, fallbackProductId?: string | null): GuideCitation {
  const documentId = evidence.documentId || 'unknown-document';
  const chunkId = evidence.chunkId || `chunk-${evidence.chunkIndex ?? 0}`;
  const snippet = evidence.text || evidence.snippet || evidence.highlight || '';
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
    scoreBreakdown: evidence.scoreBreakdown || {},
  };
}

function uniqueCitations(citations: GuideCitation[]) {
  const seen = new Set<string>();
  return citations.filter((citation) => {
    const key = `${citation.productId || ''}:${citation.documentId}:${citation.chunkId}:${citation.snippet}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function toNumber(value?: number | string | null) {
  if (value == null) return null;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function abbreviate(value: string, length: number) {
  return value.length <= length ? value : `${value.slice(0, length)}...`;
}
