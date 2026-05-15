/**
 * 商品管理API服务层。
 * 提供商品的增删改查、文档绑定和属性抽取等接口调用。
 */
import { api } from './api';
import type { PageResult } from '../types';

export interface ProductPageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  brand?: string;
  categoryId?: string;
  status?: string;
  priceMin?: number | '';
  priceMax?: number | '';
}

export interface ProductListItem {
  id: string;
  knowledgeBaseId: string;
  spuCode: string;
  name: string;
  brand?: string | null;
  categoryId?: string | null;
  summary?: string | null;
  priceMin?: number | null;
  priceMax?: number | null;
  status: string;
  mainImageUrl?: string | null;
  updateTime?: string | null;
  stockStatus?: string | null;
  promotions?: string[];
  promotionCount?: number;
}

export interface ProductAttributeItem {
  id: string;
  attributeKey: string;
  attributeName?: string | null;
  attributeValue: string;
  attributeUnit?: string | null;
  attributeType?: string | null;
  sourceType?: string | null;
  sourceDocumentId?: string | null;
  confidence?: number | null;
  evidenceText?: string | null;
}

export interface ProductSkuItem {
  id: string;
  skuCode: string;
  title?: string | null;
  price?: number | null;
  currency?: string | null;
  stockStatus?: string | null;
  specJson?: string | null;
  status?: string | null;
}

export interface ProductMediaItem {
  id: string;
  mediaType?: string | null;
  url?: string | null;
  objectKey?: string | null;
  altText?: string | null;
  ocrText?: string | null;
  metadata?: string | null;
}

export interface ProductDocumentLinkItem {
  id: string;
  documentId: string;
  chunkId?: string | null;
  bindType?: string | null;
  metadata?: string | null;
}

export interface ProductDetail extends ProductListItem {
  sellingPoints?: string | null;
  targetUsers?: string | null;
  metadata?: string | null;
  skus?: ProductSkuItem[];
  media?: ProductMediaItem[];
  attributes: ProductAttributeItem[];
  documents: ProductDocumentLinkItem[];
}

export interface ProductPayload {
  knowledgeBaseId: string;
  spuCode: string;
  name: string;
  brand?: string;
  categoryId?: string;
  summary?: string;
  priceMin?: number | null;
  priceMax?: number | null;
  sellingPoints?: string | null;
  targetUsers?: string | null;
  status?: string;
}

export interface ProductDocumentBindPayload {
  documentId: string;
  chunkId?: string | null;
  bindType?: 'detail' | 'marketing' | 'faq' | 'policy' | 'review';
  extractAttributes?: boolean;
  metadata?: string | null;
}

export interface ProductExtractionResp {
  productId: string;
  documentId: string;
  attributeCount: number;
  sellingPointCount: number;
  audienceCount: number;
  constraintCount: number;
  promotionCount: number;
  failureReason?: string | null;
}

export function listProducts(params: ProductPageParams) {
  return api.get<PageResult<ProductListItem>, PageResult<ProductListItem>>('/commerce/products', { params });
}

export function getProduct(productId: string) {
  return api.get<ProductDetail, ProductDetail>(`/commerce/products/${productId}`);
}

export function createProduct(payload: ProductPayload) {
  return api.post<ProductDetail, ProductDetail>('/commerce/products', payload);
}

export function updateProduct(productId: string, payload: Partial<ProductPayload>) {
  return api.put<ProductDetail, ProductDetail>(`/commerce/products/${productId}`, payload);
}

export function deleteProduct(productId: string) {
  return api.delete<void, void>(`/commerce/products/${productId}`);
}

export function bindProductDocument(productId: string, payload: ProductDocumentBindPayload) {
  return api.post<void, void>(`/commerce/products/${productId}/documents/${payload.documentId}/bind`, payload);
}

export function triggerProductExtraction(productId: string, documentId: string) {
  return api.post<ProductExtractionResp, ProductExtractionResp>(`/commerce/products/${productId}/documents/${documentId}/extract`);
}
