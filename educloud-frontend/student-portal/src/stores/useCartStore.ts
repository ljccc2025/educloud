import { create } from 'zustand';
import type { CartItem } from '@/types';

interface CartState {
  items: CartItem[];
  addToCart: (item: CartItem) => void;
  removeFromCart: (courseId: string) => void;
  clearCart: () => void;
  total: () => number;
  isInCart: (courseId: string) => boolean;
}

export const useCartStore = create<CartState>((set, get) => ({
  items: [],
  addToCart: (item: CartItem) => {
    const exists = get().items.some((i) => i.courseId === item.courseId);
    if (!exists) {
      set((state) => ({ items: [...state.items, item] }));
    }
  },
  removeFromCart: (courseId: string) => {
    set((state) => ({ items: state.items.filter((i) => i.courseId !== courseId) }));
  },
  clearCart: () => set({ items: [] }),
  total: () => get().items.reduce((sum, item) => sum + item.price, 0),
  isInCart: (courseId: string) => get().items.some((i) => i.courseId === courseId),
}));