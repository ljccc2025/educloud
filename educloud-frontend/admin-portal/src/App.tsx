import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import AdminLayout from './layouts/AdminLayout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import UserManage from './pages/UserManage';
import CourseAudit from './pages/CourseAudit';
import ContentAudit from './pages/ContentAudit';
import OrderManage from './pages/OrderManage';
import Finance from './pages/Finance';
import SystemConfig from './pages/SystemConfig';
import Logs from './pages/Logs';
import { useEffect, type JSX } from 'react';
import { useAuthStore } from './stores/useAuthStore';

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
          <Route index element={<Dashboard />} />
          <Route path="users" element={<UserManage />} />
          <Route path="course-audit" element={<CourseAudit />} />
          <Route path="content-audit" element={<ContentAudit />} />
          <Route path="orders" element={<OrderManage />} />
          <Route path="finance" element={<Finance />} />
          <Route path="config" element={<SystemConfig />} />
          <Route path="logs" element={<Logs />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
