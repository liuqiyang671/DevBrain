import type { GuideCitation, GuideProductCard } from '../../../types';

interface ProductEvidenceDrawerProps {
  product: GuideProductCard | null;
  reason: string | null;
  citations: GuideCitation[];
  onClose: () => void;
}

export function ProductEvidenceDrawer({ product, reason, citations, onClose }: ProductEvidenceDrawerProps) {
  if (!product) {
    return null;
  }
  const related = citations.filter((citation) => !citation.productId || citation.productId === product.productId);
  return (
    <div className="guide-evidence-drawer" role="dialog" aria-modal="false" aria-label="推荐证据">
      <div className="guide-drawer-header">
        <div>
          <span>推荐证据</span>
          <h3>{product.name}</h3>
        </div>
        <button type="button" onClick={onClose} aria-label="关闭证据">×</button>
      </div>
      {reason && (
        <section className="guide-drawer-block">
          <strong>推荐理由</strong>
          <p>{reason}</p>
        </section>
      )}
      <section className="guide-drawer-block">
        <strong>业务信号</strong>
        <div className="guide-drawer-signals">
          <span>{formatPrice(product.priceMin, product.priceMax)}</span>
          <span>{formatStock(product.stockStatus)}</span>
          <span>{product.promotions?.length ? product.promotions.slice(0, 2).join(' / ') : '暂无明确优惠'}</span>
        </div>
      </section>
      <section className="guide-drawer-block">
        <strong>文档证据</strong>
        {related.length === 0 ? (
          <p>暂无可关联的证据片段。</p>
        ) : related.map((citation) => (
          <article className="guide-drawer-citation" key={`${citation.documentId}-${citation.chunkId}-${citation.snippet}`}>
            <span>{citation.documentId} · Chunk {citation.chunkId}</span>
            <em>{citation.score != null ? `${Math.round(citation.score * 100)}%` : '--'}</em>
            <p>{citation.snippet || citation.text || '证据内容为空'}</p>
          </article>
        ))}
      </section>
    </div>
  );
}

function formatPrice(min?: number | null, max?: number | null) {
  if (min == null && max == null) return '价格待确认';
  if (min != null && max != null && min !== max) return `¥${min} - ¥${max}`;
  return `¥${min ?? max}`;
}

function formatStock(value?: string | null) {
  if (value === 'in_stock') return '有货';
  if (value === 'out_of_stock') return '缺货';
  return '库存待确认';
}
