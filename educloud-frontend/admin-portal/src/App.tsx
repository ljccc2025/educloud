import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
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
import { useAuthStore } from './stores/useAuthStore';
import type { JSX } from 'react';

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
  return (
    <BrowserRouter>
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
