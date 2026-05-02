import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090/api/devbrain';

let csrfToken: string | null = null;

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
  withCredentials: true,
});

export async function ensureCsrfToken() {
  if (!csrfToken) {
    csrfToken = await api.get<string, string>('/auth/csrf');
  }
  return csrfToken;
}

api.interceptors.request.use(async (config) => {
  const method = config.method?.toUpperCase();
  // JWT 在 HttpOnly Cookie 中，前端只负责把 CSRF token 放到写请求头里。
  if (method && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    config.headers.set('X-XSRF-TOKEN', await ensureCsrfToken());
  }
  return config;
});

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
