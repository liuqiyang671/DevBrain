/**
 * 推荐商品卡片流组件。
 * 以卡片列表形式展示AI推荐的商品，包含排名、价格、评分和推荐理由。
 */
import type { GuideProductCard } from '../../../types';

interface ProductCardStreamProps {
  products: GuideProductCard[];
  onReasonClick?: (product: GuideProductCard, reason: string) => void;
  onFeedback?: (product: GuideProductCard, feedbackType: string) => void;
}

export function ProductCardStream({ products, onReasonClick, onFeedback }: ProductCardStreamProps) {
  if (products.length === 0) {
    return <div className="guide-panel-empty">商品卡片会在检索完成后出现在这里。</div>;
  }
  return (
    <div className="guide-product-stack">
      {products.map((product, index) => (
        <article className="guide-product-card" key={product.productId}>
          <div className="guide-product-rank">{index + 1}</div>
          {product.imageUrl ? (
            <img src={product.imageUrl} alt={product.name} />
          ) : (
            <div className="guide-product-image">{product.name.slice(0, 2)}</div>
          )}
          <div className="guide-product-body">
            <div>
              <strong>{product.name}</strong>
              <span>{product.brand || '未标注品牌'}</span>
            </div>
            <p>{formatPrice(product.priceMin, product.priceMax)}</p>
            <div className="guide-product-signals">
              <span>{formatStock(product.stockStatus)}</span>
              <span>{formatPromotions(product.promotions)}</span>
            </div>
            <BusinessSignalStrip product={product} />
            <div className="guide-score-line">
              <meter min={0} max={100} value={product.score || 0} />
              <em>{product.score != null ? `${Math.round(product.score)} 分` : '待评分'}</em>
            </div>
            <ul>
              {product.reasons.slice(0, 3).map((reason) => (
                <li key={reason}>
                  <button type="button" onClick={() => onReasonClick?.(product, reason)}>{reason}</button>
                </li>
              ))}
            </ul>
            <div className="guide-card-actions">
              <button type="button" onClick={() => onReasonClick?.(product, product.reasons[0] || '')}>看证据</button>
              <button type="button" onClick={() => onFeedback?.(product, 'not_interested')}>不感兴趣</button>
            </div>
          </div>
        </article>
      ))}
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

function formatPromotions(values?: string[]) {
  if (!values || values.length === 0) return '暂无明确优惠';
  return values.slice(0, 2).join(' / ');
}

function BusinessSignalStrip({ product }: { product: GuideProductCard }) {
  const signals = [
    product.priceMin != null || product.priceMax != null ? '价格已接入' : '价格待确认',
    product.stockStatus && product.stockStatus !== 'unknown' ? '库存已接入' : '库存待确认',
    product.promotions?.length || product.promotionCount ? '优惠已接入' : '优惠待确认',
  ];
  return (
    <div className="guide-business-strip">
      {signals.map((signal) => <span key={signal}>{signal}</span>)}
    </div>
  );
}
