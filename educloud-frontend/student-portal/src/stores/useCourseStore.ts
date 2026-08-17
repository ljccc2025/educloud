import { create } from 'zustand';
import type { Course } from '@/types';
import { courseApi } from '@/services/api';

interface CourseState {
  courses: Course[];
  currentCourse: Course | null;
  loading: boolean;
  fetchCourses: () => Promise<void>;
  fetchCourse: (id: string) => Promise<void>;
}

export const useCourseStore = create<CourseState>((set) => ({
  courses: [],
  currentCourse: null,
  loading: false,
  fetchCourses: async () => {
    set({ loading: true });
    try {
      const data = await courseApi.getAll();
      set({ courses: data, loading: false });
    } catch {
      set({ loading: false });
    }
  },
  fetchCourse: async (id: string) => {
    set({ loading: true });
    try {
      const data = await courseApi.getById(Number(id));
      set({ currentCourse: data ?? null, loading: false });
    } catch {
      set({ loading: false });
    }
  },
}));
