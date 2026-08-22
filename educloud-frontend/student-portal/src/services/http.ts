import axios, { AxiosError, AxiosRequestConfig } from 'axios';

/**
 * EduCloud 真实 API 客户端（M03 联调）。
 * 经 Vite 代理转发到 Gateway（/api -> VITE_GATEWAY_TARGET，默认本机 8080）；
 * refresh_token 为 HttpOnly Cookie（Path=/api/v1/auth），请求带 withCredentials；
 * 401 时自动用 Cookie 刷新一次并重放，失败则清除本地登录态。
 */

export interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  requestId?: string;
  timestamp?: string;
}

export const TOKEN_KEY = 'student_token';

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: true,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshPromise: Promise<string | null> | null = null;

function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    try {
      const resp = await axios.post<ApiEnvelope<{ accessToken: string }>>(
        '/api/v1/auth/refresh',
        null,
        { withCredentials: true, timeout: 15000 },
      );
      const token = resp.data?.data?.accessToken ?? null;
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
      return token;
    } catch {
      localStorage.removeItem(TOKEN_KEY);
      return null;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

http.interceptors.response.use(
  (resp) => resp,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const config = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;
    const status = error.response?.status;
    const hadToken = Boolean(config?.headers?.Authorization);
    // 仅带 Bearer 的请求做一次 401 刷新重放（登录失败/刷新失败不触发）。
    if (status === 401 && hadToken && config && !config._retried) {
      config._retried = true;
      const fresh = await refreshAccessToken();
      if (fresh) {
        config.headers = { ...(config.headers ?? {}), Authorization: `Bearer ${fresh}` };
        return http.request(config);
      }
    }
    const envelope = error.response?.data;
    const message =
      envelope && typeof envelope === 'object' && 'message' in envelope
        ? String((envelope as { message?: unknown }).message ?? '请求失败')
        : status === 0
          ? '无法连接服务器'
          : '请求失败';
    const err = new Error(message);
    (err as Error & { code?: string }).code =
      (envelope as { code?: string } | undefined)?.code ?? String(status ?? 'NETWORK');
    return Promise.reject(err);
  },
);

/** 后端业务错误码 -> 中文提示。 */
export function apiErrorText(e: unknown): string {
  const code = (e as { code?: string } | null)?.code;
  const message = e instanceof Error ? e.message : '登录失败，请重试';
  switch (code) {
    case 'INVALID_CREDENTIALS':
      return '用户名或密码错误';
    case 'ACCOUNT_LOCKED':
      return '账号已锁定，请稍后再试';
    case 'ACCOUNT_DISABLED':
      return '账号已被禁用，请联系管理员';
    case 'USERNAME_TAKEN':
      return '用户名已被占用';
    case 'EMAIL_TAKEN':
      return '该邮箱已被注册';
    case 'PHONE_TAKEN':
      return '该手机号已被注册';
    case 'PASSWORD_WEAK':
      return '密码不符合安全要求（至少 8 位）';
    case 'REFRESH_ALREADY_ROTATED':
    case 'SESSION_REVOKED':
    case 'SESSION_REUSE_DETECTED':
    case 'TOKEN_EXPIRED':
      return '登录已过期，请重新登录';
    default:
      return message || '登录失败，请重试';
  }
}

export { http };
