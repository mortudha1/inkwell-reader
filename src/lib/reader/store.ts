// Client state: reader settings + per-book reading progress, both persisted
// to localStorage via zustand.

"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { DEFAULT_SETTINGS, type Progress, type ReaderSettings } from "./types";

interface SettingsState {
  settings: ReaderSettings;
  setSettings: (patch: Partial<ReaderSettings>) => void
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      settings: DEFAULT_SETTINGS,
      setSettings: (patch) =>
        set((s) => ({ settings: { ...s.settings, ...patch } })),
    }),
    { name: "inkwell:settings", version: 1 }
  )
);

interface ProgressState {
  progress: Record<string, Progress>;
  setProgress: (bookId: string, p: Progress) => void;
  clearProgress: (bookId: string) => void;
}

export const useProgressStore = create<ProgressState>()(
  persist(
    (set) => ({
      progress: {},
      setProgress: (bookId, p) =>
        set((s) => ({ progress: { ...s.progress, [bookId]: p } })),
      clearProgress: (bookId) =>
        set((s) => {
          const next = { ...s.progress };
          delete next[bookId];
          return { progress: next };
        }),
    }),
    { name: "inkwell:progress", version: 1 }
  )
);
