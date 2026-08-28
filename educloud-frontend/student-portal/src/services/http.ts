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

const DEVICE_KEY = 'educloud_device_id';

function randomUuid(): string {
  // crypto.randomUUID 仅在安全上下文（https 或 localhost）可用；
  // 局域网 http 访问必须回退到 RFC4122 v4 生成器，否则注册请求会中断。
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function deviceId(): string {
  let id = localStorage.getItem(DEVICE_KEY);
  if (!id) {
    id = randomUuid();
    localStorage.setItem(DEVICE_KEY, id);
  }
  return id;
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (config.url?.startsWith('/auth/register')) {
    config.headers = config.headers ?? {};
    config.headers['X-Device-Id'] = deviceId();
  }
  return config;
});

let refreshPromise: Promise<string | null> | null = null;

function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    try {
      const resp = await axios.post<ApiEnvelope<{ accessToken: string; user?: { userType?: string } }>>(
        '/api/v1/auth/refresh',
        null,
        { withCredentials: true, timeout: 15000 },
      );
      const token = resp.data?.data?.accessToken ?? null;
      const userType = resp.data?.data?.user?.userType;
      // 跨端隔离校验：仅当身份明确为 STUDENT 时才采纳刷新结果，防止多标签页串号
      if (token && (!userType || userType === 'STUDENT')) {
        localStorage.setItem(TOKEN_KEY, token);
        return token;
      } else {
        localStorage.removeItem(TOKEN_KEY);
        window.dispatchEvent(new Event('auth:session-expired'));
        return null;
      }
    } catch {
      localStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event('auth:session-expired'));
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
    case 'RATE_LIMITED':
      return '操作太频繁，请稍后再试';
    case 'DEPENDENCY_UNAVAILABLE':
      return '服务暂不可用，请稍后重试';
    // ---- Course 服务错误码（M05） ----
    case 'COURSE_NOT_FOUND':
      return '课程不存在';
    case 'COURSE_NOT_FREE':
      return '该课程为付费课程';
    case 'COURSE_OFFLINE_OR_ARCHIVED':
      return '课程已下架';
    case 'COURSE_ACCESS_DENIED':
      return '无权访问';
    case 'NOT_ENROLLED':
      return '需先选课';
    case 'VERSION_NOT_DRAFT':
      return '当前状态不可编辑';
    case 'SUBMISSION_NOT_PENDING':
      return '审核状态已变更';
    case 'REVIEW_REJECT_REASON_REQUIRED':
      return '请填写驳回原因';
    case 'COURSE_STATE_CONFLICT':
      return '课程状态冲突';
    case 'REVIEW_NOT_FOUND':
      return '评价不存在';
    // ---- AI 助教错误码（M15） ----
    case 'AI_QUOTA_EXCEEDED':
      return '今日提问次数已用完，明天再来';
    case 'AI_GLOBAL_BUDGET_EXCEEDED':
    case 'AI_PROVIDER_UNAVAILABLE':
      return 'AI 服务暂时不可用，请稍后重试';
    case 'AI_STREAM_NOT_SUPPORTED':
      return '当前版本暂不支持流式输出';
    case 'AI_QUESTION_TOO_LONG':
      return '提问请控制在 1000 字以内';
    case 'AI_CONVERSATION_NOT_FOUND':
      return '会话不存在或已删除';
    case 'AI_CONVERSATION_NOT_OWNED':
      return '无法访问他人的会话';
    default:
      return message || '登录失败，请重试';
  }
}

export { http };