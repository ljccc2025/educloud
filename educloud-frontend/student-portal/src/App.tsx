import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useEffect, type JSX } from 'react';
import MainLayout from './layouts/MainLayout';
import { useAuthStore } from './stores/useAuthStore';

import Home from './pages/Home';
import CourseList from './pages/CourseList';
import CourseDetail from './pages/CourseDetail';
import MyCourses from './pages/MyCourses';
import Learning from './pages/Learning';
import LiveRoom from './pages/LiveRoom';
import Assignments from './pages/Assignments';
import Exams from './pages/Exams';
import Profile from './pages/Profile';
import Orders from './pages/Orders';
import Login from './pages/Login';
import Notifications from './pages/Notifications';
import AiAssistant from './pages/AiAssistant';
import Community from './pages/Community';

function ProtectedRoute({ children }: { children: JSX.Element }) {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  useEffect(() => {
    // 已有本地 token 时恢复真实用户信息（失败则自动清理过期登录态）。
    void useAuthStore.getState().restore();
  }, []);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<MainLayout />}>
          <Route index element={<Home />} />
          <Route path="courses" element={<CourseList />} />
          <Route path="courses/:id" element={<CourseDetail />} />
          <Route path="my-courses" element={<ProtectedRoute><MyCourses /></ProtectedRoute>} />
          <Route path="learn/:courseId" element={<ProtectedRoute><Learning /></ProtectedRoute>} />
          <Route path="live/:id" element={<LiveRoom />} />
          <Route path="assignments" element={<ProtectedRoute><Assignments /></ProtectedRoute>} />
          <Route path="exams" element={<ProtectedRoute><Exams /></ProtectedRoute>} />
          <Route path="profile" element={<ProtectedRoute><Profile /></ProtectedRoute>} />
          <Route path="orders" element={<ProtectedRoute><Orders /></ProtectedRoute>} />
          <Route path="notifications" element={<ProtectedRoute><Notifications /></ProtectedRoute>} />
          <Route path="ai-assistant" element={<ProtectedRoute><AiAssistant /></ProtectedRoute>} />
          <Route path="community" element={<ProtectedRoute><Community /></ProtectedRoute>} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
