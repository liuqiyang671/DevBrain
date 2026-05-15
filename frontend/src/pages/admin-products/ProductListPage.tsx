/**
 * 商品管理页面。
 * 提供商品的列表查询、新增、编辑、删除和详情查看功能。
 */
import { FormEvent, useCallback, useEffect, useState } from 'react';
import * as commerceApi from '../../services/commerce';
import type { ProductDetail, ProductListItem, ProductPayload } from '../../services/commerce';

const emptyDraft: ProductPayload = {
  knowledgeBaseId: '',
  spuCode: '',
  name: '',
  brand: '',
  categoryId: '',
  summary: '',
  priceMin: null,
  priceMax: null,
  status: 'enabled',
};

export function ProductListPage() {
  const [records, setRecords] = useState<ProductListItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [brand, setBrand] = useState('');
  const [status, setStatus] = useState('');
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState<ProductPayload>(emptyDraft);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [selected, setSelected] = useState<ProductDetail | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    commerceApi.listProducts({ pageNo, pageSize: 10, keyword, brand, status })
      .then((page) => {
        setRecords(page.records || []);
        setTotal(page.total || 0);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : '商品加载失败'))
      .finally(() => setLoading(false));
  }, [brand, keyword, pageNo, status]);

  useEffect(() => { load(); }, [load]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    try {
      if (editingId) {
        await commerceApi.updateProduct(editingId, draft);
      } else {
        await commerceApi.createProduct(draft);
      }
      setDraft(emptyDraft);
      setEditingId(null);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : '商品保存失败');
    }
  }

  async function openDetail(productId: string) {
    try {
      setSelected(await commerceApi.getProduct(productId));
    } catch (err) {
      setError(err instanceof Error ? err.message : '详情加载失败');
    }
  }

  function edit(item: ProductListItem) {
    setEditingId(item.id);
    setDraft({
      knowledgeBaseId: item.knowledgeBaseId,
      spuCode: item.spuCode,
      name: item.name,
      brand: item.brand || '',
      categoryId: item.categoryId || '',
      summary: item.summary || '',
      priceMin: item.priceMin ?? null,
      priceMax: item.priceMax ?? null,
      status: item.status,
    });
  }

  return (
    <section className="admin-commerce-layout">
      <div className="commerce-toolbar">
        <div>
          <h2>商品管理</h2>
          <p>维护导购可推荐的商品主数据、属性和绑定文档。</p>
        </div>
        <div className="commerce-filters">
          <input value={keyword} placeholder="搜索商品" onChange={(event) => { setPageNo(1); setKeyword(event.target.value); }} />
          <input value={brand} placeholder="品牌" onChange={(event) => { setPageNo(1); setBrand(event.target.value); }} />
          <select value={status} onChange={(event) => { setPageNo(1); setStatus(event.target.value); }}>
            <option value="">全部状态</option>
            <option value="enabled">启用</option>
            <option value="disabled">停用</option>
          </select>
        </div>
      </div>
      {error && <div className="guide-error-banner">{error}</div>}
      <section className="admin-commerce-grid">
        <form className="card stack-form" onSubmit={submit}>
          <h3>{editingId ? '编辑商品' : '新增商品'}</h3>
          <label>知识库 ID<input value={draft.knowledgeBaseId} onChange={(event) => setDraft({ ...draft, knowledgeBaseId: event.target.value })} required /></label>
          <label>SPU 编码<input value={draft.spuCode} onChange={(event) => setDraft({ ...draft, spuCode: event.target.value })} required /></label>
          <label>商品名<input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} required /></label>
          <label>品牌<input value={draft.brand || ''} onChange={(event) => setDraft({ ...draft, brand: event.target.value })} /></label>
          <label>类目<input value={draft.categoryId || ''} onChange={(event) => setDraft({ ...draft, categoryId: event.target.value })} /></label>
          <label>摘要<textarea value={draft.summary || ''} onChange={(event) => setDraft({ ...draft, summary: event.target.value })} /></label>
          <div className="commerce-two-fields">
            <label>最低价<input type="number" value={draft.priceMin ?? ''} onChange={(event) => setDraft({ ...draft, priceMin: event.target.value ? Number(event.target.value) : null })} /></label>
            <label>最高价<input type="number" value={draft.priceMax ?? ''} onChange={(event) => setDraft({ ...draft, priceMax: event.target.value ? Number(event.target.value) : null })} /></label>
          </div>
          <label>状态<select value={draft.status || 'enabled'} onChange={(event) => setDraft({ ...draft, status: event.target.value })}><option value="enabled">启用</option><option value="disabled">停用</option></select></label>
          <div className="commerce-actions">
            <button className="btn btn-primary" type="submit">{editingId ? '保存修改' : '创建商品'}</button>
            {editingId && <button className="btn btn-light" type="button" onClick={() => { setEditingId(null); setDraft(emptyDraft); }}>取消</button>}
          </div>
        </form>
        <article className="card table-card">
          <div className="card-title">
            <div>
              <h3>商品列表</h3>
              <p>{loading ? '加载中' : `${total} 条记录`}</p>
            </div>
          </div>
          <table className="data-table">
            <thead><tr><th>商品</th><th>品牌 / 类目</th><th>价格</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              {records.length === 0 ? <tr><td colSpan={5}>暂无商品</td></tr> : records.map((item) => (
                <tr key={item.id}>
                  <td><strong>{item.name}</strong><small>{item.spuCode}</small></td>
                  <td>{item.brand || '--'} / {item.categoryId || '--'}</td>
                  <td>{formatPrice(item.priceMin, item.priceMax)}</td>
                  <td><span className={`status-pill ${item.status}`}>{item.status}</span></td>
                  <td>
                    <div className="commerce-actions">
                      <button className="btn btn-light" type="button" onClick={() => openDetail(item.id)}>详情</button>
                      <button className="btn btn-light" type="button" onClick={() => edit(item)}>编辑</button>
                      <button className="btn btn-danger" type="button" onClick={() => window.confirm('确定删除该商品？') && commerceApi.deleteProduct(item.id).then(load)}>删除</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="pagination-bar">
            <button className="btn btn-light" type="button" disabled={pageNo <= 1} onClick={() => setPageNo(pageNo - 1)}>上一页</button>
            <span>第 {pageNo} 页</span>
            <button className="btn btn-light" type="button" disabled={records.length < 10} onClick={() => setPageNo(pageNo + 1)}>下一页</button>
          </div>
        </article>
      </section>
      {selected && (
        <section className="card commerce-detail-card">
          <div className="card-title">
            <div><h3>{selected.name}</h3><p>{selected.summary || '暂无摘要'}</p></div>
            <button className="btn btn-light" type="button" onClick={() => setSelected(null)}>关闭</button>
          </div>
          <div className="commerce-detail-grid">
            <div><strong>属性</strong>{selected.attributes?.length ? selected.attributes.map((attr) => <p key={attr.id}>{attr.attributeName || attr.attributeKey}：{attr.attributeValue}</p>) : <p>暂无属性</p>}</div>
            <div><strong>绑定文档</strong>{selected.documents?.length ? selected.documents.map((doc) => <p key={doc.id}>{doc.documentId} · {doc.bindType || 'detail'}</p>) : <p>暂无绑定文档</p>}</div>
          </div>
        </section>
      )}
    </section>
  );
}

function formatPrice(min?: number | null, max?: number | null) {
  if (min == null && max == null) return '--';
  if (min != null && max != null && min !== max) return `¥${min} - ¥${max}`;
  return `¥${min ?? max}`;
}
