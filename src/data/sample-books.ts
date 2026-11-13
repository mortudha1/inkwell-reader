// Built-in sample books (public domain) fetched from /samples on first run
// and stored in IndexedDB like any other book.

import { parseTxt } from "@/lib/reader/txt";
import {
  idbGetBook,
  idbListBooks,
  idbPutBook,
  idbPutChapters,
} from "@/lib/reader/idb";
import type { BookMeta } from "@/lib/reader/types";

export interface SampleBook {
  id: string;
  title: string;
  author: string;
  lang: string;
  coverPath: string;
  file: string;
}

export const SAMPLE_BOOKS: SampleBook[] = [
  {
    id: "sample-alice",
    title: "Alice's Adventures in Wonderland",
    author: "Lewis Carroll",
    lang: "en",
    coverPath: "/covers/alice.jpg",
    file: "/samples/alice.txt",
  },
  {
    id: "sample-art-of-war",
    title: "The Art of War",
    author: "Sun Tzu · tr. Lionel Giles",
    lang: "en",
    coverPath: "/covers/art-of-war.jpg",
    file: "/samples/art-of-war.txt",
  },
  {
    id: "sample-meditations",
    title: "Meditations",
    author: "Marcus Aurelius · tr. George Long",
    lang: "en",
    coverPath: "/covers/meditations.jpg",
    file: "/samples/meditations.txt",
  },
];

const SEED_FLAG = "inkwell:seeded:v1";

/**
 * Ensure sample books exist in the library. Runs once per device.
 * Returns nothing — callers simply reload the library afterwards.
 */
export async function seedSampleBooks(): Promise<void> {
  if (typeof window === "undefined") return;
  if (localStorage.getItem(SEED_FLAG)) return;

  try {
    const existing = await idbListBooks();
    const existingIds = new Set(existing.map((b) => b.id));

    for (const sample of SAMPLE_BOOKS) {
      if (existingIds.has(sample.id)) continue;
      try {
        const res = await fetch(sample.file);
        if (!res.ok) continue;
        const text = await res.text();
        const parsed = parseTxt(text);
        if (!parsed.chapters.length) continue;
        const totalChars = parsed.chapters.reduce((a, c) => a + c.chars, 0);
        const meta: BookMeta = {
          id: sample.id,
          title: sample.title,
          author: sample.author,
          format: "txt",
          source: "sample",
          lang: sample.lang,
          coverPath: sample.coverPath,
          hasCoverBlob: false,
          createdAt: Date.now() - (SAMPLE_BOOKS.length - SAMPLE_BOOKS.indexOf(sample)) * 1000,
          totalChars,
          chapters: parsed.chapters.map((c, i) => ({
            index: i,
            title: c.title,
            chars: c.chars,
          })),
          toc: parsed.chapters.map((c, i) => ({
            title: c.title,
            chapterIndex: i,
            anchor: null,
          })),
        };
        await idbPutBook(meta);
        await idbPutChapters(
          parsed.chapters.map((c, i) => ({
            bookId: sample.id,
            index: i,
            html: c.html,
          }))
        );
      } catch {
        // skip this sample silently
      }
    }
    localStorage.setItem(SEED_FLAG, "1");
  } catch {
    // localStorage/IDB unavailable — try again next load
  }
}

export async function getBookWithCoverMeta(id: string): Promise<BookMeta | undefined> {
  return idbGetBook(id);
}
