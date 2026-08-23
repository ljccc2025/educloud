import { create } from 'zustand';
import type { Course, CourseDetail, MyCourse } from '@/types';
import { courseApi, type CourseListParams } from '@/services/courseApi';
import { apiErrorText } from '@/services/http';

interface CourseState {
  courses: Course[];
  total: number;
  currentCourse: CourseDetail | null;
  myCourses: MyCourse[];
  loading: boolean;
  error: string | null;
  fetchCourses: (params?: CourseListParams) => Promise<void>;
  fetchCourse: (id: string) => Promise<void>;
  fetchMyCourses: () => Promise<void>;
  enroll: (id: string) => Promise<void>;
}

export const useCourseStore = create<CourseState>((set) => ({
  courses: [],
  total: 0,
  currentCourse: null,
  myCourses: [],
  loading: false,
  error: null,
  fetchCourses: async (params?: CourseListParams) => {
    set({ loading: true, error: null });
    try {
      const page = await courseApi.getCourses(params);
      set({ courses: page.items, total: page.total, loading: false });
    } catch (e) {
      set({ error: apiErrorText(e), loading: false });
    }
  },
  fetchCourse: async (id: string) => {
    set({ loading: true, error: null });
    try {
      const data = await courseApi.getById(id);
      set({ currentCourse: data, loading: false });
    } catch (e) {
      set({ error: apiErrorText(e), loading: false });
    }
  },
  fetchMyCourses: async () => {
    set({ loading: true, error: null });
    try {
      const page = await courseApi.getMyEnrollments(1, 100);
      set({ myCourses: page.items, loading: false });
    } catch (e) {
      set({ error: apiErrorText(e), loading: false });
    }
  },
  enroll: async (id: string) => {
    await courseApi.enroll(id);
  },
}));