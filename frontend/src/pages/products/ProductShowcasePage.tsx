/**
 * 前台商品展示页。
 * 面向普通用户浏览商品、查看详情，并把商品上下文带入 AI 导购。
 */
import { FormEvent, ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as commerceApi from '../../services/commerce';
import type { ProductAttributeItem, ProductDetail, ProductListItem, ProductSkuItem } from '../../services/commerce';

const pageSize = 12;

interface ProductFilters {
  keyword: string;
  brand: string;
  categoryId: string;
  priceMin: string;
  priceMax: string;
}

const emptyFilters: ProductFilters = {
  keyword: '',
  brand: '',
  categoryId: '',
  priceMin: '',
  priceMax: '',
};

export function ProductShowcasePage() {
  const navigate = useNavigate();
  const [records, setRecords] = useState<ProductListItem[]>([]);
  const [filters, setFilters] = useState<ProductFilters>(emptyFilters);
  const [appliedFilters, setAppliedFilters] = useState<ProductFilters>(emptyFilters);
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ProductDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadProducts = useCallback(() => {
    setLoading(true);
    setError(null);
    commerceApi.listProducts({
      pageNo,
      pageSize,
      keyword: appliedFilters.keyword || undefined,
      brand: appliedFilters.brand || undefined,
      categoryId: appliedFilters.categoryId || undefined,
      priceMin: parseOptionalNumber(appliedFilters.priceMin),
      priceMax: parseOptionalNumber(appliedFilters.priceMax),
      status: 'enabled',
    })
      .then((page) => {
        setRecords(page.records || []);
        setTotal(page.total || 0);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : '商品加载失败'))
      .finally(() => setLoading(false));
  }, [appliedFilters, pageNo]);

  useEffect(() => {
    loadProducts();
  }, [loadProducts]);

  const brands = useMemo(() => uniqueValues(records.map((item) => item.brand)), [records]);
  const categoryCount = useMemo(() => uniqueValues(records.map((item) => item.categoryId)).length, [records]);
  const priceText = useMemo(() => summarizePriceRange(records), [records]);
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  function submitFilters(event: FormEvent) {
    event.preventDefault();
    setPageNo(1);
    setAppliedFilters(filters);
  }

  function resetFilters() {
    setFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
    setPageNo(1);
  }

  async function openDetail(productId: string) {
    setDetailLoading(true);
    setError(null);
    try {
      setSelected(await commerceApi.getProduct(productId));
    } catch (err) {
      setError(err instanceof Error ? err.message : '商品详情加载失败');
    } finally {
      setDetailLoading(false);
    }
  }

  function askGuide(product: ProductListItem | ProductDetail) {
    const prompt = `我正在看「${product.name}」${product.brand ? `（${product.brand}）` : ''}，请结合预算、适用场景、优缺点和替代选择帮我判断是否值得买。`;
    sessionStorage.setItem('devbrain.guide.prefill', prompt);
    navigate('/shopping-guide');
  }

  return (
    <section className="product-showcase-page">
      <header className="product-showcase-hero">
        <div>
          <span>PRODUCT DISCOVERY</span>
          <h2>商品库</h2>
          <p>浏览已接入导购知识库的商品，先看清信息，再交给 AI 导购做取舍。</p>
        </div>
        <div className="product-hero-actions">
          <Link className="btn btn-light" to="/shopping-guide">直接问导购</Link>
        </div>
      </header>

      <section className="product-showcase-metrics" aria-label="商品概览">
        <MetricCard label="可选商品" value={`${total}`} helper="当前筛选结果" />
        <MetricCard label="本页品牌" value={`${brands.length}`} helper={brands.slice(0, 3).join(' / ') || '等待商品数据'} />
        <MetricCard label="本页类目" value={`${categoryCount}`} helper="按商品目录聚合" />
        <MetricCard label="价格区间" value={priceText} helper="基于本页商品估算" />
      </section>

      <form className="product-filter-panel" onSubmit={submitFilters}>
        <label>
          <span>搜索</span>
          <input
            value={filters.keyword}
            placeholder="商品名、SPU、卖点"
            onChange={(event) => setFilters((prev) => ({ ...prev, keyword: event.target.value }))}
          />
        </label>
        <label>
          <span>品牌</span>
          <input
            value={filters.brand}
            placeholder="例如 Dyson"
            onChange={(event) => setFilters((prev) => ({ ...prev, brand: event.target.value }))}
          />
        </label>
        <label>
          <span>类目</span>
          <input
            value={filters.categoryId}
            placeholder="类目 ID"
            onChange={(event) => setFilters((prev) => ({ ...prev, categoryId: event.target.value }))}
          />
        </label>
        <label>
          <span>最低价</span>
          <input
            value={filters.priceMin}
            inputMode="decimal"
            placeholder="0"
            onChange={(event) => setFilters((prev) => ({ ...prev, priceMin: event.target.value }))}
          />
        </label>
        <label>
          <span>最高价</span>
          <input
            value={filters.priceMax}
            inputMode="decimal"
            placeholder="9999"
            onChange={(event) => setFilters((prev) => ({ ...prev, priceMax: event.target.value }))}
          />
        </label>
        <div className="product-filter-actions">
          <button className="btn btn-primary" type="submit">筛选</button>
          <button className="btn btn-light" type="button" onClick={resetFilters}>重置</button>
        </div>
      </form>

      {error && <div className="guide-error-banner">{error}</div>}

      <section className="product-workbench">
        <main className="product-grid-panel">
          <div className="product-section-title">
            <div>
              <h3>前台商品</h3>
              <p>{loading ? '正在同步商品数据' : `第 ${pageNo} / ${totalPages} 页`}</p>
            </div>
            <button className="btn btn-light" type="button" onClick={loadProducts} disabled={loading}>刷新</button>
          </div>
          {loading ? (
            <div className="loading-state">正在加载商品...</div>
          ) : records.length === 0 ? (
            <div className="empty-state">
              <h3>还没有可展示的商品</h3>
              <p>请在后台商品管理中启用商品，或调整筛选条件。</p>
            </div>
          ) : (
            <div className="product-card-grid">
              {records.map((product) => (
                <article className="front-product-card" key={product.id}>
                  <ProductImage product={product} />
                  <div className="front-product-card-body">
                    <div className="front-product-card-title">
                      <div>
                        <span>{product.brand || '未标注品牌'}</span>
                        <h3>{product.name}</h3>
                      </div>
                      <strong>{formatPrice(product.priceMin, product.priceMax)}</strong>
                    </div>
                    <p>{product.summary || '暂无商品摘要，建议进入详情查看属性和资料完整度。'}</p>
                    <div className="front-product-meta">
                      <span>{product.categoryId || '未分类'}</span>
                      <span>{product.spuCode}</span>
                    </div>
                    <div className="front-product-actions">
                      <button className="btn btn-light" type="button" onClick={() => openDetail(product.id)}>
                        查看详情
                      </button>
                      <button className="btn btn-primary" type="button" onClick={() => askGuide(product)}>
                        问导购
                      </button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
          <div className="pagination-bar">
            <button className="btn btn-light" type="button" disabled={pageNo <= 1 || loading} onClick={() => setPageNo((value) => value - 1)}>上一页</button>
            <span>共 {total} 条</span>
            <button className="btn btn-light" type="button" disabled={pageNo >= totalPages || loading} onClick={() => setPageNo((value) => value + 1)}>下一页</button>
          </div>
        </main>

        <aside className="product-detail-panel">
          <div className="product-section-title">
            <div>
              <h3>商品详情</h3>
              <p>{detailLoading ? '详情加载中' : selected ? '属性、SKU 和资料证据' : '选择一个商品查看'}</p>
            </div>
          </div>
          {detailLoading ? (
            <div className="loading-state compact">正在读取详情...</div>
          ) : selected ? (
            <ProductDetailView product={selected} onAsk={() => askGuide(selected)} />
          ) : (
            <div className="product-detail-empty">
              <strong>从左侧选择商品</strong>
              <p>详情会显示 AI 导购可用的结构化属性、SKU 和资料绑定情况。</p>
            </div>
          )}
        </aside>
      </section>
    </section>
  );
}

function ProductDetailView({ product, onAsk }: { product: ProductDetail; onAsk: () => void }) {
  const attributes = prioritizeAttributes(product.attributes || []);
  const skus = product.skus || [];
  const media = product.media || [];

  return (
    <div className="product-detail-view">
      <ProductImage product={product} large />
      <div>
        <span className={`status-pill ${product.status || 'unknown'}`}>{statusLabel(product.status)}</span>
        <h3>{product.name}</h3>
        <p>{product.summary || '暂无商品摘要。'}</p>
      </div>
      <div className="product-detail-price">{formatPrice(product.priceMin, product.priceMax)}</div>
      <div className="product-detail-actions">
        <button className="btn btn-primary" type="button" onClick={onAsk}>围绕它提问</button>
      </div>
      <DetailBlock title="核心卖点" emptyText="暂无卖点信息">
        <TextList value={product.sellingPoints} emptyText="暂无卖点信息" />
      </DetailBlock>
      <DetailBlock title="适合人群" emptyText="暂无人群标签">
        <TextList value={product.targetUsers} emptyText="暂无人群标签" />
      </DetailBlock>
      <DetailBlock title="关键属性" emptyText="暂无结构化属性">
        {attributes.length > 0 ? (
          <div className="product-attribute-list">
            {attributes.map((attr) => (
              <div key={attr.id || `${attr.attributeKey}-${attr.attributeValue}`}>
                <span>{attr.attributeName || attr.attributeKey}</span>
                <strong>{attr.attributeValue}{attr.attributeUnit || ''}</strong>
              </div>
            ))}
          </div>
        ) : null}
      </DetailBlock>
      <DetailBlock title="SKU" emptyText="暂无 SKU">
        {skus.length > 0 ? (
          <div className="product-sku-list">
            {skus.slice(0, 4).map((sku) => <SkuRow key={sku.id || sku.skuCode} sku={sku} />)}
          </div>
        ) : null}
      </DetailBlock>
      <DetailBlock title="资料完整度" emptyText="暂无资料绑定">
        {(product.documents?.length || media.length) ? (
          <div className="product-evidence-grid">
            <span><strong>{product.documents?.length || 0}</strong> 份文档</span>
            <span><strong>{media.length}</strong> 个媒体</span>
          </div>
        ) : null}
      </DetailBlock>
    </div>
  );
}

function DetailBlock({ title, emptyText, children }: { title: string; emptyText: string; children: ReactNode }) {
  return (
    <section className="product-detail-block">
      <h4>{title}</h4>
      {children || <p>{emptyText}</p>}
    </section>
  );
}

function TextList({ value, emptyText }: { value?: string | null; emptyText: string }) {
  const items = splitLooseList(value);
  if (items.length === 0) return <p>{emptyText}</p>;
  return (
    <ul>
      {items.map((item) => <li key={item}>{item}</li>)}
    </ul>
  );
}

function SkuRow({ sku }: { sku: ProductSkuItem }) {
  return (
    <div className="product-sku-row">
      <div>
        <strong>{sku.title || sku.skuCode}</strong>
        <span>{sku.stockStatus || sku.status || '库存待确认'}</span>
      </div>
      <em>{sku.price != null ? formatCurrency(sku.price, sku.currency) : '价格待确认'}</em>
    </div>
  );
}

function MetricCard({ label, value, helper }: { label: string; value: string; helper: string }) {
  return (
    <article>
      <span>{label}</span>
      <strong>{value}</strong>
      <p>{helper}</p>
    </article>
  );
}

function ProductImage({ product, large = false }: { product: ProductListItem | ProductDetail; large?: boolean }) {
  const imageUrl = product.mainImageUrl || ('media' in product ? product.media?.find((item) => item.url)?.url : null);
  const className = large ? 'front-product-image large' : 'front-product-image';
  if (imageUrl) {
    return <img className={className} src={imageUrl} alt={product.name} />;
  }
  return <div className={className}>{product.name.slice(0, 2)}</div>;
}

function parseOptionalNumber(value: string) {
  if (!value.trim()) return '';
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : '';
}

function formatPrice(min?: number | null, max?: number | null) {
  if (min == null && max == null) return '价格待确认';
  if (min != null && max != null && min !== max) return `¥${formatAmount(min)} - ¥${formatAmount(max)}`;
  return `¥${formatAmount(min ?? max ?? 0)}`;
}

function formatCurrency(value: number, currency?: string | null) {
  const symbol = currency && currency !== 'CNY' ? `${currency} ` : '¥';
  return `${symbol}${formatAmount(value)}`;
}

function formatAmount(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}

function statusLabel(status?: string | null) {
  if (status === 'enabled') return '上架展示';
  if (status === 'disabled') return '暂不展示';
  return status || '未知状态';
}

function splitLooseList(value?: string | null) {
  if (!value) return [];
  return value
    .split(/[\n,，;；、]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 6);
}

function prioritizeAttributes(attributes: ProductAttributeItem[]) {
  return [...attributes]
    .sort((a, b) => (b.confidence || 0) - (a.confidence || 0))
    .slice(0, 8);
}

function uniqueValues(values: Array<string | null | undefined>) {
  return Array.from(new Set(values.map((item) => item?.trim()).filter(Boolean) as string[]));
}

function summarizePriceRange(products: ProductListItem[]) {
  const values = products.flatMap((item) => [item.priceMin, item.priceMax]).filter((value): value is number => value != null);
  if (values.length === 0) return '待确认';
  return `¥${formatAmount(Math.min(...values))} - ¥${formatAmount(Math.max(...values))}`;
}
