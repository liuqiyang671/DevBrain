/**
 * 引用证据和决策轨迹面板组件。
 * 展示推荐结论的文档引用依据和工作流各节点的执行详情。
 */
import type { GuideCitation, GuideTraceStep } from '../../../types';

interface CitationPanelProps {
  citations: GuideCitation[];
  traces: GuideTraceStep[];
}

export function CitationPanel({ citations, traces }: CitationPanelProps) {
  return (
    <div className="guide-evidence-stack">
      <section>
        {citations.length === 0 ? (
          <div className="guide-panel-empty">暂无引用证据。</div>
        ) : citations.map((citation) => (
          <article className="guide-citation" key={`${citation.documentId}-${citation.chunkId}-${citation.productId}`}>
            <strong>{citation.documentId}</strong>
            <span>
              Chunk {citation.chunkId}
              {citation.productId ? ` · 商品 ${citation.productId}` : ''}
              {' · '}
              {citation.score != null ? Math.round(citation.score * 100) : '--'}%
            </span>
            <p>{citation.snippet || citation.text || '证据片段为空'}</p>
          </article>
        ))}
      </section>
      <section>
        <h3>兼容轨迹</h3>
        {traces.length === 0 ? (
          <div className="guide-panel-empty">等待本轮分析。</div>
        ) : traces.slice(-7).map((trace) => (
          <article className={trace.error ? 'guide-trace error' : 'guide-trace'} key={`${trace.node}-${trace.durationMs}`}>
            <strong>{trace.node}</strong>
            <span>{trace.durationMs ?? 0} ms</span>
            <p>{trace.error || trace.outputSummary || trace.inputSummary}</p>
          </article>
        ))}
      </section>
    </div>
  );
}
