import type { GuideBusinessSignalCoverage } from '../../../types';
import type { EvaluationRunItem } from '../../../services/evaluation';

interface GuideEvaluationPanelProps {
  signals: GuideBusinessSignalCoverage;
  latestRun?: EvaluationRunItem | null;
}

export function GuideEvaluationPanel({ signals, latestRun }: GuideEvaluationPanelProps) {
  const items = [
    ['真实业务数据', signals.hasBusinessData],
    ['意图理解', signals.hasIntentUnderstanding],
    ['价格库存优惠', signals.hasPriceStockPromotion],
    ['理由可解释', signals.hasExplainableReasons],
    ['测试和指标', signals.hasEvaluationSignals],
  ] as const;

  return (
    <div className="guide-evaluation-panel">
      <div className="guide-signal-grid">
        {items.map(([label, ok]) => (
          <span className={ok ? 'ok' : 'missing'} key={label}>
            <b>{ok ? '已覆盖' : '待补齐'}</b>
            {label}
          </span>
        ))}
      </div>
      {signals.missingSignals.length > 0 && (
        <p>缺口：{signals.missingSignals.join('、')}</p>
      )}
      {latestRun ? (
        <section className="guide-latest-eval">
          <strong>最近评测：{latestRun.status}</strong>
          <MetricLine metrics={latestRun.summaryMetrics || {}} name="intentAccuracy" label="意图准确率" />
          <MetricLine metrics={latestRun.summaryMetrics || {}} name="recommendationHit" label="推荐命中" />
          <MetricLine metrics={latestRun.summaryMetrics || {}} name="recommendationExplainability" label="解释性" />
        </section>
      ) : (
        <div className="guide-panel-empty">暂无可展示的评测运行。管理员可在评测反馈页持续跑数据集。</div>
      )}
    </div>
  );
}

function MetricLine({ metrics, name, label }: { metrics: Record<string, unknown>; name: string; label: string }) {
  const value = Number(metrics[name]);
  const percent = Number.isFinite(value) ? Math.round(value * 100) : null;
  return (
    <div className="guide-metric-line">
      <span>{label}</span>
      <strong>{percent == null ? '--' : `${percent}%`}</strong>
    </div>
  );
}
