import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
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

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<TeacherLayout />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/courses" element={<CourseManage />} />
          <Route path="/courses/edit/:id" element={<CourseEdit />} />
          <Route path="/content" element={<ContentManage />} />
          <Route path="/live" element={<LiveManage />} />
          <Route path="/assignments" element={<AssignmentGrade />} />
          <Route path="/exams" element={<ExamManage />} />
          <Route path="/students" element={<StudentList />} />
          <Route path="/analytics" element={<Analytics />} />
          <Route path="/notifications" element={<Notifications />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
