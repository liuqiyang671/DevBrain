/**
 * 导购评测仪表盘页面。
 * 管理评测数据集、运行质量评测，并处理用户反馈闭环。
 */
import { FormEvent, useCallback, useEffect, useState } from 'react';
import * as evaluationApi from '../../services/evaluation';
import type { EvaluationDatasetItem, EvaluationReport, EvaluationRunItem, GuideFeedbackItem } from '../../services/evaluation';

export function EvaluationDashboardPage() {
  const [datasets, setDatasets] = useState<EvaluationDatasetItem[]>([]);
  const [runs, setRuns] = useState<EvaluationRunItem[]>([]);
  const [feedback, setFeedback] = useState<GuideFeedbackItem[]>([]);
  const [selectedReport, setSelectedReport] = useState<EvaluationReport | null>(null);
  const [draft, setDraft] = useState({ name: '', description: '' });
  const [selectedDatasetId, setSelectedDatasetId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.all([
      evaluationApi.listEvaluationDatasets({ pageNo: 1, pageSize: 20 }),
      evaluationApi.listEvaluationRuns({ pageNo: 1, pageSize: 20 }),
      evaluationApi.listGuideFeedback({ pageNo: 1, pageSize: 20, reviewStatus: 'pending' }),
    ])
      .then(([datasetPage, runPage, feedbackPage]) => {
        setDatasets(datasetPage.records || []);
        setRuns(runPage.records || []);
        setFeedback(feedbackPage.records || []);
        setSelectedDatasetId((current) => current || datasetPage.records?.[0]?.id || '');
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : '评测数据加载失败'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  async function createDataset(event: FormEvent) {
    event.preventDefault();
    try {
      await evaluationApi.createEvaluationDataset({ ...draft, status: 'enabled' });
      setDraft({ name: '', description: '' });
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : '评测集创建失败');
    }
  }

  async function runEvaluation() {
    if (!selectedDatasetId) return;
    try {
      await evaluationApi.runEvaluation({ datasetId: selectedDatasetId, promptVersion: 'current' });
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : '评测运行启动失败');
    }
  }

  async function openReport(runId: string) {
    try {
      const report = await evaluationApi.getEvaluationReport(runId);
      setSelectedReport(report);
    } catch (err) {
      setError(err instanceof Error ? err.message : '评测报告加载失败');
    }
  }

  async function cancelRun(runId: string) {
    try {
      await evaluationApi.cancelEvaluationRun(runId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : '评测取消失败');
    }
  }

  return (
    <section className="admin-commerce-layout">
      <div className="commerce-toolbar">
        <div>
          <h2>导购评测</h2>
          <p>管理评测集、运行质量评测，并处理用户反馈闭环。</p>
        </div>
        <button className="btn btn-light" type="button" onClick={load} disabled={loading}>刷新</button>
      </div>
      {error && <div className="guide-error-banner">{error}</div>}
      <section className="evaluation-metrics">
        <article><span>评测集</span><strong>{datasets.length}</strong></article>
        <article><span>运行记录</span><strong>{runs.length}</strong></article>
        <article><span>待处理反馈</span><strong>{feedback.length}</strong></article>
      </section>
      <section className="admin-commerce-grid">
        <form className="card stack-form" onSubmit={createDataset}>
          <h3>新建评测集</h3>
          <label>名称<input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} required /></label>
          <label>描述<textarea value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
          <button className="btn btn-primary" type="submit">创建评测集</button>
        </form>
        <article className="card">
          <div className="card-title"><div><h3>运行评测</h3><p>选择一个评测集立即运行。</p></div></div>
          <div className="stack-form">
            <label>评测集
              <select value={selectedDatasetId} onChange={(event) => setSelectedDatasetId(event.target.value)}>
                <option value="">请选择</option>
                {datasets.map((dataset) => <option key={dataset.id} value={dataset.id}>{dataset.name}</option>)}
              </select>
            </label>
            <button className="btn btn-primary" type="button" onClick={runEvaluation} disabled={!selectedDatasetId}>启动评测</button>
          </div>
        </article>
      </section>
      <section className="admin-commerce-grid">
        <article className="card table-card">
          <div className="card-title"><div><h3>评测集</h3><p>{datasets.length} 条</p></div></div>
          <table className="data-table">
            <thead><tr><th>名称</th><th>状态</th><th>更新时间</th></tr></thead>
            <tbody>{datasets.length ? datasets.map((item) => <tr key={item.id}><td>{item.name}</td><td>{item.status}</td><td>{item.updateTime || '--'}</td></tr>) : <tr><td colSpan={3}>暂无评测集</td></tr>}</tbody>
          </table>
        </article>
        <article className="card table-card">
          <div className="card-title"><div><h3>运行记录</h3><p>{runs.length} 条</p></div></div>
          <table className="data-table">
            <thead><tr><th>运行 ID</th><th>状态</th><th>进度</th><th>核心指标</th><th>操作</th></tr></thead>
            <tbody>{runs.length ? runs.map((item) => (
              <tr key={item.id}>
                <td>{item.id}</td>
                <td><span className={`status-pill doc-status-${statusTone(item.status)}`}>{item.status}</span></td>
                <td>{progressText(item)}</td>
                <td>{metricLine(item.summaryMetrics)}</td>
                <td>
                  <div className="commerce-actions">
                    <button className="btn btn-light" type="button" onClick={() => openReport(item.id)}>报告</button>
                    {item.status === 'running' && <button className="btn btn-danger" type="button" onClick={() => cancelRun(item.id)}>取消</button>}
                  </div>
                </td>
              </tr>
            )) : <tr><td colSpan={5}>暂无运行记录</td></tr>}</tbody>
          </table>
        </article>
      </section>
      {selectedReport && (
        <article className="card table-card evaluation-report-card">
          <div className="card-title">
            <div><h3>评测报告</h3><p>{selectedReport.runId}</p></div>
            <button className="btn btn-light" type="button" onClick={() => setSelectedReport(null)}>关闭</button>
          </div>
          <section className="evaluation-report-metrics">
            {reportMetric(selectedReport.summaryMetrics, 'intentAccuracy', '意图')}
            {reportMetric(selectedReport.summaryMetrics, 'slotF1', '槽位')}
            {reportMetric(selectedReport.summaryMetrics, 'businessDataUsage', '业务数据')}
            {reportMetric(selectedReport.summaryMetrics, 'recommendationExplainability', '解释')}
            {reportMetric(selectedReport.summaryMetrics, 'mrr', 'MRR')}
            {reportMetric(selectedReport.summaryMetrics, 'latencyP95', 'P95ms', false)}
          </section>
          <div className="evaluation-failure-board">
            <div>
              <h4>失败类型</h4>
              <div className="failure-tags">
                {failureDistribution(selectedReport).map((item) => <span key={item.type}>{failureLabel(item.type)} × {item.count}</span>)}
                {!failureDistribution(selectedReport).length && <span>暂无失败归因</span>}
              </div>
            </div>
            <div>
              <h4>改进建议</h4>
              <ul>
                {(selectedReport.improvementHints || []).map((hint) => <li key={hint}>{hint}</li>)}
              </ul>
            </div>
          </div>
          <table className="data-table">
            <thead><tr><th>用例</th><th>失败归因</th><th>Agent Run</th><th>调试提示</th></tr></thead>
            <tbody>{(selectedReport.failedCases || []).length ? (selectedReport.failedCases || []).map((item) => (
              <tr key={item.id}>
                <td>{item.caseId}</td>
                <td>{failureLabel(item.failureType)}</td>
                <td>{item.agentRunId ? <a href={`/api/devbrain/commerce/guide/runs/${item.agentRunId}`}>{item.agentRunId}</a> : '--'}</td>
                <td>{(item.debugHints || []).join('；') || item.errorMessage || '--'}</td>
              </tr>
            )) : <tr><td colSpan={4}>暂无失败用例</td></tr>}</tbody>
          </table>
        </article>
      )}
      <article className="card table-card">
        <div className="card-title"><div><h3>待处理反馈</h3><p>{feedback.length} 条</p></div></div>
        <table className="data-table">
          <thead><tr><th>会话</th><th>目标</th><th>类型</th><th>Agent Run</th><th>说明</th><th>状态</th></tr></thead>
          <tbody>{feedback.length ? feedback.map((item) => <tr key={item.id}><td>{item.conversationId}</td><td>{item.targetType || '--'} {item.targetId || item.productId || ''}</td><td>{item.feedbackType}</td><td>{item.agentRunId || '--'}</td><td>{item.comment || item.improvementSuggestion || '--'}</td><td>{item.reviewStatus}</td></tr>) : <tr><td colSpan={6}>暂无待处理反馈</td></tr>}</tbody>
        </table>
      </article>
    </section>
  );
}

function progressText(run: EvaluationRunItem) {
  const total = run.caseCount || 0;
  const done = run.completedCaseCount || 0;
  const failed = run.failedCaseCount || 0;
  const message = typeof run.progress?.message === 'string' ? run.progress.message : '';
  if (total > 0) return `${done}/${total}，失败 ${failed}`;
  return message || '--';
}

function metricLine(metrics?: Record<string, unknown> | null) {
  if (!metrics || !Object.keys(metrics).length) return '--';
  return `通过 ${formatMetric(metrics.passRate)} · 意图 ${formatMetric(metrics.intentAccuracy)} · 解释 ${formatMetric(metrics.recommendationExplainability)}`;
}

function reportMetric(metrics: Record<string, unknown> | null | undefined, key: string, label: string, percent = true) {
  return <article key={key}><span>{label}</span><strong>{formatMetric(metrics?.[key], percent)}</strong></article>;
}

function formatMetric(value: unknown, percent = true) {
  if (typeof value !== 'number') return '--';
  return percent ? `${Math.round(value * 100)}%` : String(Math.round(value));
}

function statusTone(status: string) {
  if (status === 'completed') return 'success';
  if (status === 'failed' || status === 'cancelled') return 'error';
  return 'running';
}

function failureDistribution(report: EvaluationReport) {
  const counts = new Map<string, number>();
  (report.failedCases || []).forEach((item) => {
    const type = item.failureType || 'unknown';
    counts.set(type, (counts.get(type) || 0) + 1);
  });
  return Array.from(counts.entries()).map(([type, count]) => ({ type, count }));
}

function failureLabel(type?: string | null) {
  const labels: Record<string, string> = {
    intent_mismatch: '意图错误',
    missing_slot: '槽位/追问',
    retrieval_miss: '召回缺失',
    ranking_miss: '排序问题',
    evidence_missing: '证据不足',
    answer_hallucination: '幻觉声明',
    latency_exceeded: '延迟超标',
    tool_failure: '工具失败',
    planner_failure: '规划失败',
  };
  return labels[type || ''] || type || '--';
}
