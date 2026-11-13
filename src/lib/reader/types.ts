// Core types + design tokens for the Inkwell e-reader.

export interface ChapterMeta {
  index: number;
  title: string;
  chars: number;
}

export interface TocEntry {
  title: string;
  chapterIndex: number;
  anchor: string | null;
}

export interface BookMeta {
  id: string;
  title: string;
  author: string;
  format: "epub" | "txt";
  source: "sample" | "upload";
  lang: string;
  /** Static cover path (samples). Uploads store the blob in IndexedDB instead. */
  coverPath?: string | null;
  hasCoverBlob?: boolean;
  createdAt: number;
  chapters: ChapterMeta[];
  toc: TocEntry[];
  totalChars: number;
}

export interface ChapterContent {
  bookId: string;
  index: number;
  html: string;
}

export interface Progress {
  chapterIndex: number;
  /** char offset of the first visible character within the chapter */
  charOffset: number;
  percent: number;
  updatedAt: number;
}

// ---------------------------------------------------------------------------
// Reader themes (CSS custom props are defined in globals.css per class)
// ---------------------------------------------------------------------------
export type ThemeId = "light" | "paper" | "sage" | "dark" | "black";

export interface ReaderThemeInfo {
  id: ThemeId;
  name: string;
  swatch: string;
}

export const READER_THEMES: ReaderThemeInfo[] = [
  { id: "light", name: "Light", swatch: "#ffffff" },
  { id: "paper", name: "Paper", swatch: "#f2e8d3" },
  { id: "sage", name: "Sage", swatch: "#c9d4c2" },
  { id: "dark", name: "Dark", swatch: "#24231f" },
  { id: "black", name: "Black", swatch: "#000000" },
];

// ---------------------------------------------------------------------------
// Fonts
// ---------------------------------------------------------------------------
export interface FontOption {
  id: string;
  name: string;
  stack: string;
}

export const FONT_OPTIONS: FontOption[] = [
  { id: "literata", name: "Literata", stack: "var(--font-literata), Georgia, 'Times New Roman', serif" },
  { id: "merriweather", name: "Merriweather", stack: "var(--font-merriweather), Georgia, serif" },
  { id: "lora", name: "Lora", stack: "var(--font-lora), Georgia, serif" },
  { id: "atkinson", name: "Atkinson", stack: "var(--font-atkinson), Verdana, sans-serif" },
  { id: "inter", name: "Inter", stack: "var(--font-inter), 'Segoe UI', sans-serif" },
];

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------
export interface ReaderSettings {
  theme: ThemeId;
  font: string;
  fontSize: number; // 14..30 px
  lineHeight: number; // 1.2..2.2
  margin: 0 | 1 | 2 | 3;
  justify: boolean;
  hyphenate: boolean;
}

export const MARGIN_STEPS = [
  { v: 0, label: "S", px: 16 },
  { v: 1, label: "M", px: 26 },
  { v: 2, label: "L", px: 42 },
  { v: 3, label: "XL", px: 60 },
] as const;

export const DEFAULT_SETTINGS: ReaderSettings = {
  theme: "paper",
  font: "literata",
  fontSize: 19,
  lineHeight: 1.6,
  margin: 1,
  justify: true,
  hyphenate: true,
};

export function fontStack(id: string): string {
  return FONT_OPTIONS.find((f) => f.id === id)?.stack ?? FONT_OPTIONS[0].stack;
}

export function themeClass(id: ThemeId): string {
  return `reader-theme-${id}`;
}

/** Cumulative char counts before each chapter (length = chapters.length + 1). */
export function chapterBoundaries(book: BookMeta): number[] {
  const out = [0];
  for (const ch of book.chapters) out.push(out[out.length - 1] + Math.max(1, ch.chars));
  return out;
}
