import axios from 'axios';

/** API 基础地址，优先读取环境变量，默认指向本地开发服务 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090/api/devbrain';

/** 缓存的 CSRF Token，避免重复请求 */
let csrfToken: string | null = null;

/**
 * 全局 Axios 实例
 * - 统一设置基础地址、超时时间
 * - withCredentials 为 true 以携带 HttpOnly Cookie（JWT 存储位置）
 */
export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
  withCredentials: true,
});

/**
 * 确保 CSRF Token 可用
 * 首次调用时从服务端获取并缓存，后续直接返回缓存值
 * @returns CSRF Token 字符串
 */
export async function ensureCsrfToken() {
  if (!csrfToken) {
    csrfToken = await api.get<string, string>('/auth/csrf');
  }
  return csrfToken;
}

/**
 * 请求拦截器
 * - JWT 存储在 HttpOnly Cookie 中，前端无法直接读取
 * - 对写操作（POST/PUT/PATCH/DELETE）自动附加 X-XSRF-TOKEN 请求头
 */
api.interceptors.request.use(async (config) => {
  const method = config.method?.toUpperCase();
  // JWT 在 HttpOnly Cookie 中，前端只负责把 CSRF token 放到写请求头里。
  if (method && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    config.headers.set('X-XSRF-TOKEN', await ensureCsrfToken());
  }
  return config;
});

/**
 * 响应拦截器
 * - 成功响应：解析后端统一包装格式 { code, message, data }，提取 data 字段
 * - 失败响应：code !== '0' 时抛出错误；401 状态码触发全局登录过期事件
 */
api.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code !== '0') {
        return Promise.reject(new Error(payload.message || '请求失败'));
      }
      return payload.data;
    }
    return payload;
  },
  (error) => {
    if (error?.response?.status === 401) {
      window.dispatchEvent(new Event('devbrain-auth-expired'));
    }
    const payload = error?.response?.data;
    return Promise.reject(new Error(payload?.message || error?.message || '网络错误'));
  },
);
