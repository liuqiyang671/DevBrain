import test from 'node:test';
import assert from 'node:assert/strict';
import {
  applyAgentCancel,
  applyAgentFinish,
  applyAgentPlan,
  applyToolCall,
  applyToolObservation,
  analyzeBusinessSignalCoverage,
  buildReasonableFallbackReply,
  createGuideOutboundText,
  hydrateGuideWorkbenchFromSession,
  stepsToTimeline,
} from './guideWorkbenchModel';
import type { AgentStepItem, GuideSessionDetail } from '../../../types';

test('agent timeline reducer keeps plan, tool call and observation as one step', () => {
  const planned = applyAgentPlan([], {
    runId: 'run-1',
    stepNo: 1,
    thought: 'need business data',
    action: 'search_products',
    arguments: { category: '耳机', budgetMax: 800 },
  });
  const running = applyToolCall(planned, {
    runId: 'run-1',
    stepNo: 1,
    toolName: 'product_search',
    argumentsSummary: { filters: ['price', 'stock'] },
  });
  const observed = applyToolObservation(running, {
    runId: 'run-1',
    stepNo: 1,
    toolName: 'product_search',
    observation: 'matched=3 with coupon=2',
    durationMs: 128,
    status: 'succeeded',
  });

  assert.equal(observed.length, 1);
  assert.equal(observed[0].action, 'search_products');
  assert.equal(observed[0].toolName, 'product_search');
  assert.equal(observed[0].status, 'success');
  assert.equal(observed[0].durationMs, 128);
  assert.deepEqual(observed[0].arguments, { filters: ['price', 'stock'] });
});

test('agent finish and cancel events close running steps without losing errors', () => {
  const running = applyToolCall(
    applyAgentPlan([], { runId: 'run-2', stepNo: 1, action: 'rank_products' }),
    { runId: 'run-2', stepNo: 1, toolName: 'ranker' },
  );
  const failed = applyToolObservation(running, {
    runId: 'run-2',
    stepNo: 1,
    toolName: 'ranker',
    observation: 'coupon service timeout',
    durationMs: 3000,
    status: 'failed',
    error: 'timeout',
  });
  const finished = applyAgentFinish(failed, {
    runId: 'run-2',
    status: 'failed',
    totalSteps: 1,
    finalAction: 'fallback_answer',
  });

  assert.equal(finished[0].status, 'failed');
  assert.equal(finished[0].error, 'timeout');

  const cancelled = applyAgentCancel(running, 'run-2');
  assert.equal(cancelled[0].status, 'cancelled');
});

test('server session detail hydrates real intent, recommendations, citations and latest run id', () => {
  const detail: GuideSessionDetail = {
    sessionId: 'session-1',
    conversationId: 'conversation-1',
    title: '耳机推荐',
    lastTime: '2026-05-13T10:00:00.000Z',
    messages: [
      { id: 'm1', role: 'user', content: '800 内通勤降噪耳机', createTime: '2026-05-13T10:00:00.000Z' },
      {
        id: 'm2',
        role: 'assistant',
        content: '推荐 A',
        createTime: '2026-05-13T10:00:01.000Z',
        agentRunId: 'run-9',
      },
    ],
    state: {
      intent: {
        intentType: 'recommend',
        category: '降噪耳机',
        budgetMax: 800,
        hardConstraints: ['通勤'],
        softPreferences: ['续航长'],
        confidence: 0.91,
      },
      recommendations: [
        {
          productId: 'p1',
          name: 'Quiet Go',
          brand: 'Acme',
          priceMin: 699,
          priceMax: 799,
          stockStatus: 'in_stock',
          promotions: ['满 700 减 80'],
          promotionCount: 1,
          score: 92,
          reasons: ['预算内', '有现货', '优惠后性价比高'],
          evidences: [
            {
              productId: 'p1',
              documentId: 'doc-1',
              chunkId: 'chunk-1',
              score: 0.87,
              text: '支持主动降噪，当前有优惠券。',
            },
          ],
        },
      ],
    },
  };

  const hydrated = hydrateGuideWorkbenchFromSession(detail);

  assert.equal(hydrated.currentRunId, 'run-9');
  assert.equal(hydrated.intent?.category, '降噪耳机');
  assert.equal(hydrated.products[0].priceMin, 699);
  assert.equal(hydrated.products[0].promotions?.[0], '满 700 减 80');
  assert.equal(hydrated.citations[0].documentId, 'doc-1');
});

test('business signal coverage proves recommendations are not model-only answers', () => {
  const coverage = analyzeBusinessSignalCoverage(
    [
      {
        productId: 'p1',
        name: 'Quiet Go',
        priceMin: 699,
        priceMax: 799,
        stockStatus: 'in_stock',
        promotions: ['满减券'],
        score: 90,
        reasons: ['价格满足预算', '库存可买', '优惠后划算'],
      },
    ],
    [{ productId: 'p1', documentId: 'doc', chunkId: 'chunk', snippet: '商品证据' }],
    { intentType: 'recommend', category: '耳机', confidence: 0.8 },
    [{ id: 'run-1-1', runId: 'run-1', stepNo: 1, action: 'search', status: 'success' }],
  );

  assert.equal(coverage.hasBusinessData, true);
  assert.equal(coverage.hasIntentUnderstanding, true);
  assert.equal(coverage.hasPriceStockPromotion, true);
  assert.equal(coverage.hasExplainableReasons, true);
  assert.equal(coverage.hasEvaluationSignals, true);
});

test('persisted agent steps convert to replayable timeline with parsed arguments', () => {
  const steps: AgentStepItem[] = [
    {
      id: 's1',
      runId: 'run-1',
      stepNo: 2,
      action: 'retrieve_evidence',
      thought: 'need citations',
      argumentsJson: '{"productId":"p1"}',
      observation: 'citations=2',
      status: 'succeeded',
      durationMs: 64,
      createTime: '2026-05-13T10:00:00.000Z',
    },
  ];

  const timeline = stepsToTimeline(steps);

  assert.equal(timeline[0].status, 'success');
  assert.deepEqual(timeline[0].arguments, { productId: 'p1' });
});

test('fallback reply is useful for unclear or interrupted messages', () => {
  assert.match(buildReasonableFallbackReply('???'), /我会先帮你澄清/);
  assert.match(buildReasonableFallbackReply('5000以内笔记本'), /预算|品类|用途/);
});

test('outbound text keeps image-only turns understandable to the backend', () => {
  assert.equal(
    createGuideOutboundText('', 2),
    '请根据我上传的图片识别商品，并结合真实商品数据给出购买建议。',
  );
  assert.equal(createGuideOutboundText('  预算 300 耳机  ', 0), '预算 300 耳机');
});
