/**
 * AI导购主页面。
 * 布局包含左侧会话列表、中间对话区域和右侧推荐商品/引用面板。
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { GuideComposer } from './components/GuideComposer';
import { GuideMessageList } from './components/GuideMessageList';
import { GuideSessionSidebar } from './components/GuideSessionSidebar';
import { ProductCardStream } from './components/ProductCardStream';
import { CitationPanel } from './components/CitationPanel';
import { useGuideStream } from './hooks/useGuideStream';
import { AgentTimelinePanel } from './components/AgentTimelinePanel';
import { ProductEvidenceDrawer } from './components/ProductEvidenceDrawer';
import { GuideFeedbackBar } from './components/GuideFeedbackBar';
import { GuideEvaluationPanel } from './components/GuideEvaluationPanel';
import { useAuthStore } from '../../stores/authStore';
import * as evaluationApi from '../../services/evaluation';
import type { EvaluationRunItem } from '../../services/evaluation';
import type { GuideProductCard } from '../../types';

export function ShoppingGuidePage() {
  const guide = useGuideStream();
  const user = useAuthStore((state) => state.user);
  const threadRef = useRef<HTMLDivElement | null>(null);
  const [prefillSent, setPrefillSent] = useState(false);
  const [activeTab, setActiveTab] = useState<'products' | 'evidence' | 'agent' | 'evaluation'>('products');
  const [drawerProduct, setDrawerProduct] = useState<GuideProductCard | null>(null);
  const [drawerReason, setDrawerReason] = useState<string | null>(null);
  const [latestEvaluationRun, setLatestEvaluationRun] = useState<EvaluationRunItem | null>(null);

  const canInspectAgent = useMemo(() => Boolean(
    user?.roles.includes('admin')
    || user?.permissions.includes('agent:trace:read')
    || user?.permissions.includes('commerce:read')
  ), [user]);

  useEffect(() => {
    threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight, behavior: 'smooth' });
  }, [guide.messages, guide.streaming]);

  useEffect(() => {
    if (prefillSent || guide.streaming || guide.messages.length > 0) return;
    const prefill = sessionStorage.getItem('devbrain.guide.prefill');
    if (!prefill) return;
    sessionStorage.removeItem('devbrain.guide.prefill');
    setPrefillSent(true);
    guide.sendMessage(prefill, []);
  }, [guide, prefillSent]);

  useEffect(() => {
    if (!canInspectAgent) return;
    evaluationApi.listEvaluationRuns({ pageNo: 1, pageSize: 1 })
      .then((page) => setLatestEvaluationRun(page.records?.[0] || null))
      .catch(() => setLatestEvaluationRun(null));
  }, [canInspectAgent]);

  function openEvidence(product: GuideProductCard, reason: string) {
    setDrawerProduct(product);
    setDrawerReason(reason);
    setActiveTab('evidence');
  }

  return (
    <section className="shopping-guide-shell">
      <GuideSessionSidebar
        sessions={guide.sessions}
        activeSessionId={guide.currentSessionId}
        streaming={guide.streaming}
        serverBacked={guide.serverBackedSessions}
        offlineCache={guide.offlineCache}
        onNew={guide.startNewSession}
        onOpen={guide.openSession}
        onArchive={guide.archiveSession}
        onDelete={guide.deleteSession}
      />
      <main className="guide-chat-workspace">
        <header className="guide-workspace-header">
          <div>
            <span>AI SHOPPING GUIDE</span>
            <h2>AI 导购</h2>
          </div>
          <p>按购买意图、价格、库存、优惠和证据做决策辅助。</p>
        </header>
        {guide.error && <div className="guide-error-banner">{guide.error}</div>}
        <GuideIntentStrip intent={guide.currentIntent} runId={guide.currentRunId} />
        <div className="guide-thread" ref={threadRef}>
          <GuideMessageList messages={guide.messages} streaming={guide.streaming} statusText={guide.statusText} />
        </div>
        <GuideComposer
          streaming={guide.streaming}
          sessionId={guide.currentSessionId}
          onSend={guide.sendMessage}
          onStop={guide.stop}
        />
      </main>
      <aside className="guide-inspector">
        <section className="guide-inspector-panel">
          <div className="guide-workbench-tabs" role="tablist" aria-label="导购工作台">
            <button className={activeTab === 'products' ? 'active' : ''} type="button" onClick={() => setActiveTab('products')}>
              推荐 <span>{guide.currentProducts.length}</span>
            </button>
            <button className={activeTab === 'evidence' ? 'active' : ''} type="button" onClick={() => setActiveTab('evidence')}>
              证据 <span>{guide.citations.length}</span>
            </button>
            <button className={activeTab === 'agent' ? 'active' : ''} type="button" onClick={() => setActiveTab('agent')}>
              Agent <span>{guide.agentTimeline.length}</span>
            </button>
            <button className={activeTab === 'evaluation' ? 'active' : ''} type="button" onClick={() => setActiveTab('evaluation')}>
              指标
            </button>
          </div>

          {activeTab === 'products' && (
            <>
              <ProductCardStream
                products={guide.currentProducts}
                onReasonClick={openEvidence}
                onFeedback={(product, feedbackType) => guide.submitFeedback({ productId: product.productId, feedbackType })}
              />
              <GuideFeedbackBar
                disabled={!guide.currentSessionId}
                onSubmit={(feedbackType, comment) => guide.submitFeedback({ feedbackType, comment })}
              />
            </>
          )}

          {activeTab === 'evidence' && (
            <>
              <ProductEvidenceDrawer
                product={drawerProduct}
                reason={drawerReason}
                citations={guide.citations}
                onClose={() => {
                  setDrawerProduct(null);
                  setDrawerReason(null);
                }}
              />
              <CitationPanel citations={guide.citations} traces={guide.traces} />
            </>
          )}

          {activeTab === 'agent' && (
            <AgentTimelinePanel
              timeline={guide.agentTimeline}
              activeStepNo={guide.activeStepNo}
              canInspect={canInspectAgent}
            />
          )}

          {activeTab === 'evaluation' && (
            <GuideEvaluationPanel
              signals={guide.businessSignals}
              latestRun={canInspectAgent ? latestEvaluationRun : null}
            />
          )}

          {guide.feedbackMessage && <div className="guide-feedback-message">{guide.feedbackMessage}</div>}
        </section>
      </aside>
    </section>
  );
}

function GuideIntentStrip({ intent, runId }: { intent: ReturnType<typeof useGuideStream>['currentIntent']; runId: string | null }) {
  if (!intent && !runId) {
    return null;
  }
  return (
    <div className="guide-intent-strip">
      <span>{intent?.intentType || '识别中'}</span>
      <span>{intent?.category || '品类待确认'}</span>
      <span>{formatBudget(intent?.budgetMin, intent?.budgetMax)}</span>
      {intent?.confidence != null && <span>置信度 {Math.round(intent.confidence * 100)}%</span>}
      {runId && <span>Run {runId.slice(-6)}</span>}
    </div>
  );
}

function formatBudget(min?: number | null, max?: number | null) {
  if (min == null && max == null) return '预算待确认';
  if (min != null && max != null) return `¥${min} - ¥${max}`;
  return `¥${min ?? max}`;
}
