import { useEffect, type JSX } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import TeacherLayout from './layouts/TeacherLayout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import CourseManage from './pages/CourseManage';
import CourseEdit from './pages/CourseEdit';
import ContentManage from './pages/ContentManage';
import LiveManage from './pages/LiveManage';
import AssignmentGrade from './pages/AssignmentGrade';
import ExamManage from './pages/ExamManage';
import StudentList from './pages/StudentList';
import Analytics from './pages/Analytics';
import Notifications from './pages/Notifications';
import { useAuthStore } from './stores/useAuthStore';

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

function SessionExpiryRedirect() {
  const navigate = useNavigate();
  useEffect(() => {
    const handler = () => navigate('/login', { replace: true });
    window.addEventListener('auth:session-expired', handler);
    return () => window.removeEventListener('auth:session-expired', handler);
  }, [navigate]);
  return null;
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
              <TeacherLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Dashboard />} />
          <Route path="courses" element={<CourseManage />} />
          <Route path="courses/edit/:id" element={<CourseEdit />} />
          <Route path="content" element={<ContentManage />} />
          <Route path="live" element={<LiveManage />} />
          <Route path="assignments" element={<AssignmentGrade />} />
          <Route path="exams" element={<ExamManage />} />
          <Route path="students" element={<StudentList />} />
          <Route path="analytics" element={<Analytics />} />
          <Route path="notifications" element={<Notifications />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
