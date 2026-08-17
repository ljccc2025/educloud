import { create } from 'zustand';
import type { Course, Chapter, Courseware } from '../types';
import { api } from '../services/api';

interface CourseState {
  courses: Course[];
  currentCourse: Course | null;
  loading: boolean;
  fetchCourses: () => Promise<void>;
  fetchCourse: (id: string) => Promise<void>;
  createCourse: (data: Partial<Course>) => Promise<Course>;
  updateCourse: (id: string, data: Partial<Course>) => Promise<void>;
  deleteCourse: (id: string) => Promise<void>;
  setCurrentCourse: (course: Course | null) => void;
  addChapter: (courseId: string, title: string) => Promise<void>;
  removeChapter: (courseId: string, chapterId: string) => Promise<void>;
  reorderChapters: (courseId: string, chapters: Chapter[]) => Promise<void>;
  addCourseware: (courseId: string, chapterId: string, courseware: Omit<Courseware, 'id' | 'createdAt'>) => Promise<void>;
  removeCourseware: (courseId: string, chapterId: string, coursewareId: string) => Promise<void>;
}

export const useCourseStore = create<CourseState>((set, get) => ({
  courses: [],
  currentCourse: null,
  loading: false,

  fetchCourses: async () => {
    set({ loading: true });
    const courses = await api.getCourses();
    set({ courses, loading: false });
  },

  fetchCourse: async (id: string) => {
    set({ loading: true });
    const course = await api.getCourse(id);
    set({ currentCourse: course, loading: false });
  },

  createCourse: async (data) => {
    const course = await api.createCourse(data);
    set((state) => ({ courses: [course, ...state.courses], currentCourse: course }));
    return course;
  },

  updateCourse: async (id, data) => {
    const updated = await api.updateCourse(id, data);
    if (updated) {
      set((state) => ({
        courses: state.courses.map((c) => (c.id === id ? updated : c)),
        currentCourse: state.currentCourse?.id === id ? updated : state.currentCourse,
      }));
    }
  },

  deleteCourse: async (id) => {
    await api.deleteCourse(id);
    set((state) => ({
      courses: state.courses.filter((c) => c.id !== id),
      currentCourse: state.currentCourse?.id === id ? null : state.currentCourse,
    }));
  },

  setCurrentCourse: (course) => set({ currentCourse: course }),

  addChapter: async (courseId, title) => {
    const course = get().courses.find((c) => c.id === courseId) ?? get().currentCourse;
    if (!course) return;
    const newChapter: Chapter = {
      id: 'ch-' + Date.now(),
      title,
      order: course.chapters.length + 1,
      coursewares: [],
    };
    const updatedChapters = [...course.chapters, newChapter];
    await get().updateCourse(courseId, { chapters: updatedChapters });
  },

  removeChapter: async (courseId, chapterId) => {
    const course = get().courses.find((c) => c.id === courseId) ?? get().currentCourse;
    if (!course) return;
    const updatedChapters = course.chapters
      .filter((ch) => ch.id !== chapterId)
      .map((ch, i) => ({ ...ch, order: i + 1 }));
    await get().updateCourse(courseId, { chapters: updatedChapters });
  },

  reorderChapters: async (courseId, chapters) => {
    const reordered = chapters.map((ch, i) => ({ ...ch, order: i + 1 }));
    await get().updateCourse(courseId, { chapters: reordered });
  },

  addCourseware: async (courseId, chapterId, courseware) => {
    const course = get().courses.find((c) => c.id === courseId) ?? get().currentCourse;
    if (!course) return;
    const newCw: Courseware = {
      ...courseware,
      id: 'cw-' + Date.now(),
      createdAt: new Date().toISOString().split('T')[0],
    };
    const updatedChapters = course.chapters.map((ch) =>
      ch.id === chapterId ? { ...ch, coursewares: [...ch.coursewares, newCw] } : ch
    );
    await get().updateCourse(courseId, { chapters: updatedChapters });
  },

  removeCourseware: async (courseId, chapterId, coursewareId) => {
    const course = get().courses.find((c) => c.id === courseId) ?? get().currentCourse;
    if (!course) return;
    const updatedChapters = course.chapters.map((ch) =>
      ch.id === chapterId
        ? { ...ch, coursewares: ch.coursewares.filter((cw) => cw.id !== coursewareId) }
        : ch
    );
    await get().updateCourse(courseId, { chapters: updatedChapters });
  },
}));
