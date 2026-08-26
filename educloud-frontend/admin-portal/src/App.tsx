import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { useEffect, lazy, Suspense, type JSX } from 'react';
import AdminLayout from './layouts/AdminLayout';
import Login from './pages/Login';
import { useAuthStore } from './stores/useAuthStore';

// 采用 React.lazy 动态拆包，消除首屏过大依赖
const Dashboard = lazy(() => import('./pages/Dashboard'));
const UserManage = lazy(() => import('./pages/UserManage'));
const CourseAudit = lazy(() => import('./pages/CourseAudit'));
const ContentAudit = lazy(() => import('./pages/ContentAudit'));
const OrderManage = lazy(() => import('./pages/OrderManage'));
const Finance = lazy(() => import('./pages/Finance'));
const SystemConfig = lazy(() => import('./pages/SystemConfig'));
const Logs = lazy(() => import('./pages/Logs'));
const SearchAdmin = lazy(() => import('./pages/SearchAdmin'));

function LoadingFallback() {
  return (
    <div className="flex h-[60vh] items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-indigo-800 border-t-transparent" />
    </div>
  );
}

function SessionExpiryRedirect() {
  const navigate = useNavigate();
  useEffect(() => {
    const handler = () => navigate('/login', { replace: true });
    window.addEventListener('auth:session-expired', handler);
    return () => window.removeEventListener('auth:session-expired', handler);
  }, [navigate]);
  return null;
}

function ProtectedRoute({ children }: { children: JSX.Element }) {
  const token = useAuthStore((s) => s.token);
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function PublicRoute({ children }: { children: JSX.Element }) {
  const token = useAuthStore((s) => s.token);
  if (token) {
    return <Navigate to="/" replace />;
  }
  return children;
}

export default function App() {
  useEffect(() => {
    // 已有本地 token 时恢复真实用户信息（失败则自动清理过期登录态）。
    void useAuthStore.getState().restore();
  }, []);

  return (
    <BrowserRouter>
      <SessionExpiryRedirect />
      <Routes>
        <Route
          path="/login"
          element={
            <PublicRoute>
              <Login />
            </PublicRoute>
          }
        />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <AdminLayout />
            </ProtectedRoute>
          }
        >
          <Route
            index
            element={
              <Suspense fallback={<LoadingFallback />}>
                <Dashboard />
              </Suspense>
            }
          />
          <Route
            path="users"
            element={
              <Suspense fallback={<LoadingFallback />}>
                <UserManage />
              </Suspense>
            }
          />
          <Route
            path="course-audit"
            element={
              <Suspense fallback={<LoadingFallback />}>
                <CourseAudit />
              </Suspense>
            }
          />
          <Route
            path="content-audit"
            element={
              <Suspense fallback={<LoadingFallback />}>
                <ContentAudit />
              </Suspense>
            }
          />
          <Route
            path="orders"
            element={
              <Suspense fallback={<LoadingFallback />}>
                <OrderManage />
              </Suspense>
            }
          />
          <Route
            path="finance"
            element={
              <Suspense fallback={<LoadingFallback />}>
                <Finance />
              </Suspense>
            }
          />
          <Route
            path="search-engine"
            element={
              <Suspense fallback={<LoadingFallback />}>
                <SearchAdmin />
              </Suspense>
            }
          />
          <Route
            path="config"
            element={
              <Suspense fallback={<LoadingFallback />}>
                <SystemConfig />
              </Suspense>
            }
          />
          <Route
            path="logs"
            element={
              <Suspense fallback={<LoadingFallback />}>
                <Logs />
              </Suspense>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
