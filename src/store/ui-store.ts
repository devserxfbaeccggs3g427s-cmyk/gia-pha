import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { AppLocale } from '@/i18n/routing';

export type AppTheme = 'light' | 'dark' | 'system';

interface UiState {
  theme: AppTheme;
  sidebarCollapsed: boolean;
  mobileSidebarOpen: boolean;
  locale: AppLocale;
  setTheme: (theme: AppTheme) => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  toggleSidebar: () => void;
  setMobileSidebarOpen: (open: boolean) => void;
  setLocale: (locale: AppLocale) => void;
}

export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      theme: 'system',
      sidebarCollapsed: false,
      mobileSidebarOpen: false,
      locale: 'vi',
      setTheme: (theme) => set({ theme }),
      setSidebarCollapsed: (sidebarCollapsed) => set({ sidebarCollapsed }),
      toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
      setMobileSidebarOpen: (mobileSidebarOpen) => set({ mobileSidebarOpen }),
      setLocale: (locale) => set({ locale })
    }),
    {
      name: 'kinship.ui',
      storage: createJSONStorage(() => localStorage),
      partialize: ({ theme, sidebarCollapsed, locale }) => ({ theme, sidebarCollapsed, locale })
    }
  )
);

