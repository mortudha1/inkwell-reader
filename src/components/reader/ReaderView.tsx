"use client";

import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { AnimatePresence, motion } from "framer-motion";
import { ArrowLeft, List, Loader2, BookOpen, Sparkles } from "lucide-react";

import { Paginator, wordAtPoint, sentenceContext, type WordHit } from "@/lib/reader/paginate";
import { idbGetBook, idbGetChapter } from "@/lib/reader/idb";
import { useProgressStore, useSettingsStore } from "@/lib/reader/store";
import { lookupWord } from "@/lib/reader/lookup";
import {
  chapterBoundaries,
  fontStack,
  MARGIN_STEPS,
  themeClass,
  type BookMeta,
  type TocEntry,
} from "@/lib/reader/types";
import { toast } from "@/hooks/use-toast";
import {
  DefinitionPopover,
  type DefinitionState,
} from "@/components/reader/DefinitionPopover";
import { SettingsSheet } from "@/components/reader/SettingsSheet";
import { TocSheet } from "@/components/reader/TocSheet";
import { Slider } from "@/components/ui/slider";
import { cn } from "@/lib/utils";

type PendingOffset =
  | { kind: "start" }
  | { kind: "end" }
  | { kind: "char"; offset: number }
  | { kind: "anchor"; id: string };

interface Stats {
  globalPercent: number;
  loc: number;
  locTotal: number;
  timeChapter: string | null;
  timeBook: string | null;
}

interface SelPopup {
  x: number;
  y: number;
  text: string;
  isWord: boolean;
}

function fmtTime(min: number): string {
  if (!isFinite(min) || min <= 0) return "under a minute";
  if (min < 1) return "under a minute";
  if (min < 60) return `${Math.ceil(min)} min`;
  const h = Math.floor(min / 60);
  const m = Math.round(min % 60);
  return m > 0 ? `${h} h ${m} min` : `${h} h`;
}

export function ReaderView({ bookId, onExit }: { bookId: string; onExit: () => void }) {
  // ----- stores -----
  const settings = useSettingsStore((s) => s.settings);
  const setSettings = useSettingsStore((s) => s.setSettings);
  const setProgress = useProgressStore((s) => s.setProgress);

  // ----- data state -----
  const [book, setBook] = useState<BookMeta | null>(null);
  const [bookError, setBookError] = useState<string | null>(null);
  const [chapterIndex, setChapterIndex] = useState(0);
  const [chapterHtml, setChapterHtml] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [pageCount, setPageCount] = useState(1);
  const [layoutNonce, setLayoutNonce] = useState(0);
  const [stats, setStats] = useState<Stats>({
    globalPercent: 0,
    loc: 0,
    locTotal: 0,
    timeChapter: null,
    timeBook: null,
  });

  // ----- ui state -----
  const [menuOpen, setMenuOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [tocOpen, setTocOpen] = useState(false);
  const [definition, setDefinition] = useState<DefinitionState | null>(null);
  const [selPopup, setSelPopup] = useState<SelPopup | null>(null);
  /** slider value while the user is dragging it (null = follow reading position) */
  const [scrubbing, setScrubbing] = useState<number | null>(null);

  // ----- refs -----
  const viewportRef = useRef<HTMLDivElement>(null);
  const flowRef = useRef<HTMLDivElement>(null);
  const paginatorRef = useRef<Paginator | null>(null);
  const pendingOffsetRef = useRef<PendingOffset | null>(null);
  /**
   * Char offset the reader is actually on, captured under the CURRENT layout.
   * Re-layouts (font/size/margin changes, resize, font loading) re-anchor on
   * this instead of re-deriving the first visible char from the already-
   * reflowed DOM — so changing typography keeps your place, like a Kindle.
   */
  const anchorOffsetRef = useRef(0);
  const chapterCacheRef = useRef(new Map<number, string>());
  const loadTokenRef = useRef(0);
  const turnTimesRef = useRef<number[]>([]);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const definitionRef = useRef<DefinitionState | null>(null);
  const selectionGuardRef = useRef(false);
  const pointerDownRef = useRef<{ x: number; y: number; t: number } | null>(null);
  const relayoutRef = useRef<() => void>(() => {});
  const syncStatsRef = useRef<() => void>(() => {});
  const saveNowRef = useRef<() => void>(() => {});
  const turnRef = useRef<(dir: 1 | -1) => void>(() => {});

  const boundaries = useMemo(() => (book ? chapterBoundaries(book) : [0]), [book]);
  const chapterTitle = book?.chapters[chapterIndex]?.title ?? "";

  useEffect(() => {
    definitionRef.current = definition;
  }, [definition]);

  const fetchChapter = useCallback(
    async (i: number): Promise<string | null> => {
      const cached = chapterCacheRef.current.get(i);
      if (cached != null) return cached;
      try {
        const c = await idbGetChapter(bookId, i);
        if (c) {
          const map = chapterCacheRef.current;
          map.set(i, c.html);
          if (map.size > 6) {
            const oldest = map.keys().next().value;
            if (oldest !== undefined) map.delete(oldest);
          }
          return c.html;
        }
      } catch {
        /* fallthrough */
      }
      return null;
    },
    [bookId]
  );

  const prefetchChapter = useCallback(
    (i: number) => {
      if (book && i >= 0 && i < book.chapters.length && !chapterCacheRef.current.has(i)) {
        idbGetChapter(bookId, i)
          .then((c) => {
            if (c) chapterCacheRef.current.set(i, c.html);
          })
          .catch(() => {});
      }
    },
    [book, bookId]
  );

  const loadChapter = useCallback(
    (i: number) => {
      const token = ++loadTokenRef.current;
      setChapterIndex(i);
      const cached = chapterCacheRef.current.get(i);
      if (cached != null) {
        setChapterHtml(cached);
        prefetchChapter(i + 1);
        return;
      }
      setChapterHtml(null);
      fetchChapter(i).then((html) => {
        if (token !== loadTokenRef.current || html == null) return;
        setChapterHtml(html);
        prefetchChapter(i + 1);
      });
    },
    [fetchChapter, prefetchChapter]
  );

  // -----------------------------------------------------------------------
  // load book + resume position
  // -----------------------------------------------------------------------
  useEffect(() => {
    let cancelled = false;
    const token = ++loadTokenRef.current;
    idbGetBook(bookId)
      .then(async (b) => {
        if (cancelled) return;
        if (!b) {
          setBookError("This book could not be found in your library.");
          return;
        }
        setBook(b);
        // resume at last position (non-reactive read — no extra subscriptions)
        const p = useProgressStore.getState().progress[bookId];
        const startChapter = p ? Math.min(p.chapterIndex, b.chapters.length - 1) : 0;
        pendingOffsetRef.current = { kind: "char", offset: p ? p.charOffset : 0 };
        setChapterIndex(startChapter);
        const cached = chapterCacheRef.current.get(startChapter);
        if (cached != null) {
          setChapterHtml(cached);
          return;
        }
        setChapterHtml(null);
        const html = await fetchChapter(startChapter);
        if (cancelled || token !== loadTokenRef.current || html == null) return;
        setChapterHtml(html);
      })
      .catch(() => {
        if (!cancelled) setBookError("This book could not be loaded.");
      });
    return () => {
      cancelled = true;
    };
  }, [bookId, fetchChapter]);

  // -----------------------------------------------------------------------
  // pagination
  // -----------------------------------------------------------------------
  const closeDefinition = useCallback(() => {
    try {
      window.getSelection()?.removeAllRanges();
    } catch {
      /* ignore */
    }
    setDefinition(null);
    setSelPopup(null);
  }, []);

  const syncStats = useCallback(() => {
    const pz = paginatorRef.current;
    if (!pz || !book) return;
    const chapterChars = Math.max(1, book.chapters[chapterIndex]?.chars ?? 1);
    const charAt = Math.min(pz.currentCharOffset(), chapterChars - 1);
    anchorOffsetRef.current = charAt;
    const globalChars = boundaries[chapterIndex] + charAt;
    const globalPercent = Math.max(0, Math.min(1, globalChars / Math.max(1, book.totalChars)));

    // reading speed: refine with observed page turns
    const charsPerPage = chapterChars / Math.max(1, pz.pageCount);
    let charsPerMin = 1400;
    const ts = turnTimesRef.current;
    if (ts.length >= 4) {
      const gaps: number[] = [];
      for (let i = 1; i < ts.length; i++) {
        const g = ts[i] - ts[i - 1];
        if (g > 200 && g < 600000) gaps.push(g);
      }
      if (gaps.length >= 3) {
        gaps.sort((a, b) => a - b);
        const median = gaps[Math.floor(gaps.length / 2)];
        charsPerMin = Math.max(250, Math.min(4000, (charsPerPage / median) * 60000));
      }
    }

    const remainingChapter = Math.max(0, chapterChars - charAt);
    const remainingBook = Math.max(0, book.totalChars - globalChars);
    setStats({
      globalPercent,
      loc: Math.floor(globalChars / 128),
      locTotal: Math.floor(Math.max(1, book.totalChars) / 128),
      timeChapter: pz.pageCount > 1 || chapterIndex > 0 ? fmtTime(remainingChapter / charsPerMin) : null,
      timeBook: globalPercent > 0.005 ? fmtTime(remainingBook / charsPerMin) : null,
    });
  }, [book, boundaries, chapterIndex]);

  const relayoutKeepPosition = useCallback(() => {
    const pz = paginatorRef.current;
    if (!pz || chapterHtml == null) return;
    const off = anchorOffsetRef.current;
    pz.layout();
    pz.goToChar(off, false);
    setPage(pz.page);
    setPageCount(pz.pageCount);
    syncStats();
  }, [chapterHtml, syncStats]);

  useEffect(() => {
    syncStatsRef.current = syncStats;
    relayoutRef.current = relayoutKeepPosition;
  }, [syncStats, relayoutKeepPosition]);

  // main layout pass — runs after content commit and on any layout-affecting change
  useLayoutEffect(() => {
    const vp = viewportRef.current;
    const fl = flowRef.current;
    if (!vp || !fl || chapterHtml == null) return;
    if (!paginatorRef.current) paginatorRef.current = new Paginator(vp, fl);
    const pz = paginatorRef.current;

    // anchor on where the reader actually IS (captured under the previous
    // layout), not on whatever the reflowed DOM happens to show first
    const prevOffset = anchorOffsetRef.current;
    pz.layout();

    const pending = pendingOffsetRef.current;
    pendingOffsetRef.current = null;
    if (pending?.kind === "end") {
      pz.goToPage(pz.pageCount - 1, false);
    } else if (pending?.kind === "start") {
      pz.goToPage(0, false);
    } else if (pending?.kind === "anchor") {
      let el: Element | null = null;
      try {
        el = fl.querySelector(`#${CSS.escape(pending.id)}`);
      } catch {
        el = null;
      }
      if (!el) {
        const byId = fl.ownerDocument.getElementById(pending.id);
        if (byId && fl.contains(byId)) el = byId;
      }
      const off = el ? pz.offsetOfElement(el) : null;
      if (off != null) pz.goToChar(off, false);
      else pz.goToPage(0, false);
    } else if (pending?.kind === "char") {
      pz.goToChar(pending.offset, false);
    } else {
      pz.goToChar(prevOffset, false);
    }

    setPage(pz.page);
    setPageCount(pz.pageCount);
    syncStats();
  }, [
    chapterHtml,
    layoutNonce,
    settings.fontSize,
    settings.lineHeight,
    settings.margin,
    settings.font,
    settings.justify,
    settings.hyphenate,
  ]);

  // re-measure when webfonts finish loading
  useEffect(() => {
    if (chapterHtml == null) return;
    let cancelled = false;
    document.fonts?.ready.then(() => {
      if (!cancelled) requestAnimationFrame(() => relayoutRef.current());
    });
    return () => {
      cancelled = true;
    };
  }, [chapterHtml, settings.font, settings.fontSize]);

  // images loading late inside the chapter
  useEffect(() => {
    const fl = flowRef.current;
    if (!fl || chapterHtml == null) return;
    let timer: ReturnType<typeof setTimeout> | null = null;
    const onLoad = () => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => relayoutRef.current(), 150);
    };
    fl.addEventListener("load", onLoad, true);
    return () => {
      fl.removeEventListener("load", onLoad, true);
      if (timer) clearTimeout(timer);
    };
  }, [chapterHtml]);

  // viewport resize / orientation change
  useEffect(() => {
    const vp = viewportRef.current;
    if (!vp || typeof ResizeObserver === "undefined") return;
    let timer: ReturnType<typeof setTimeout> | null = null;
    const ro = new ResizeObserver(() => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => relayoutRef.current(), 180);
    });
    ro.observe(vp);
    return () => {
      ro.disconnect();
      if (timer) clearTimeout(timer);
    };
  }, []);

  // -----------------------------------------------------------------------
  // progress persistence
  // -----------------------------------------------------------------------
  const saveProgressNow = useCallback(() => {
    const pz = paginatorRef.current;
    if (!pz || !book || chapterHtml == null) return;
    const charOffset = pz.currentCharOffset();
    const global = boundaries[chapterIndex] + charOffset;
    setProgress(bookId, {
      chapterIndex,
      charOffset,
      percent: global / Math.max(1, book.totalChars),
      updatedAt: Date.now(),
    });
  }, [book, bookId, boundaries, chapterHtml, chapterIndex, setProgress]);

  useEffect(() => {
    saveNowRef.current = saveProgressNow;
  }, [saveProgressNow]);

  const saveProgressSoon = useCallback(() => {
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      saveTimerRef.current = null;
      saveNowRef.current();
    }, 700);
  }, []);

  useEffect(() => {
    const onHide = () => {
      if (document.visibilityState === "hidden") saveNowRef.current();
    };
    document.addEventListener("visibilitychange", onHide);
    return () => {
      document.removeEventListener("visibilitychange", onHide);
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
      saveNowRef.current();
    };
  }, []);

  // -----------------------------------------------------------------------
  // page turns
  // -----------------------------------------------------------------------
  const recordTurnTime = useCallback(() => {
    const ts = turnTimesRef.current;
    ts.push(Date.now());
    if (ts.length > 14) ts.shift();
  }, []);

  const turn = useCallback(
    (dir: 1 | -1) => {
      const pz = paginatorRef.current;
      if (!pz || !book || chapterHtml == null) return;
      recordTurnTime();
      if (dir === 1) {
        if (pz.page + 1 < pz.pageCount) {
          pz.goToPage(pz.page + 1);
          afterTurn();
        } else if (chapterIndex + 1 < book.chapters.length) {
          pendingOffsetRef.current = { kind: "start" };
          loadChapter(chapterIndex + 1);
        } else {
          toast({ title: "The End", description: "You've reached the end of the book." });
        }
      } else {
        if (pz.page > 0) {
          pz.goToPage(pz.page - 1);
          afterTurn();
        } else if (chapterIndex > 0) {
          pendingOffsetRef.current = { kind: "end" };
          loadChapter(chapterIndex - 1);
        }
      }

      function afterTurn() {
        setPage(pz.page);
        setMenuOpen(false);
        setDefinition(null);
        setSelPopup(null);
        try {
          window.getSelection()?.removeAllRanges();
        } catch {
          /* ignore */
        }
        syncStats();
        saveProgressSoon();
      }
    },
    [book, chapterHtml, chapterIndex, loadChapter, recordTurnTime, saveProgressSoon, syncStats]
  );

  useEffect(() => {
    turnRef.current = turn;
  }, [turn]);

  // -----------------------------------------------------------------------
  // tap & swipe
  // -----------------------------------------------------------------------
  const openWordDefinition = useCallback((hit: WordHit) => {
    try {
      const sel = window.getSelection();
      sel?.removeAllRanges();
      sel?.addRange(hit.range);
    } catch {
      /* ignore */
    }
    selectionGuardRef.current = true;
    window.setTimeout(() => {
      selectionGuardRef.current = false;
    }, 350);

    const below = hit.rect.top < window.innerHeight * 0.42;
    setDefinition({
      word: hit.word,
      x: hit.rect.left + hit.rect.width / 2,
      y: below ? hit.rect.bottom : hit.rect.top,
      below,
      context: hit.context,
      loading: true,
      data: null,
      error: null,
      aiMode: false,
    });
    lookupWord(hit.word, hit.context)
      .then((d) => {
        setDefinition((prev) =>
          prev && prev.word === hit.word ? { ...prev, data: d, loading: false, error: null } : prev
        );
      })
      .catch((err) => {
        setDefinition((prev) =>
          prev && prev.word === hit.word
            ? {
                ...prev,
                loading: false,
                error: err instanceof Error ? err.message : "Lookup failed",
              }
            : prev
        );
      });
  }, []);

  const lookupText = useCallback((text: string, context: string, ai: boolean) => {
    setDefinition({
      word: text,
      x: window.innerWidth / 2,
      y: window.innerHeight * 0.3,
      below: true,
      context,
      loading: true,
      data: null,
      error: null,
      aiMode: ai,
    });
    lookupWord(text, context, ai ? "ai" : "plain")
      .then((d) => {
        setDefinition((prev) =>
          prev && prev.word === text ? { ...prev, data: d, loading: false, error: null } : prev
        );
      })
      .catch((err) => {
        setDefinition((prev) =>
          prev && prev.word === text
            ? {
                ...prev,
                loading: false,
                error: err instanceof Error ? err.message : "Lookup failed",
              }
            : prev
        );
      });
  }, []);

  const handleTap = useCallback(
    (x: number, y: number) => {
      const vp = viewportRef.current;
      const fl = flowRef.current;
      if (!vp || !fl) return;
      const rect = vp.getBoundingClientRect();
      const relX = (x - rect.left) / rect.width;
      if (relX < 0.07) return turn(-1);
      if (relX > 0.93) return turn(1);

      const hit = wordAtPoint(x, y, fl);
      if (hit) return openWordDefinition(hit);

      if (definitionRef.current || selPopup) {
        closeDefinition();
        return;
      }
      if (relX < 0.32) return turn(-1);
      if (relX > 0.68) return turn(1);
      setMenuOpen((v) => !v);
    },
    [turn, openWordDefinition, closeDefinition, selPopup]
  );

  // desktop text selection → floating lookup chip
  useEffect(() => {
    const onSelectionChange = () => {
      if (selectionGuardRef.current) return;
      const sel = window.getSelection();
      const fl = flowRef.current;
      if (!sel || sel.isCollapsed || !fl || sel.rangeCount === 0) {
        setSelPopup(null);
        return;
      }
      if (!fl.contains(sel.anchorNode)) {
        setSelPopup(null);
        return;
      }
      const text = sel.toString().trim().replace(/\s+/g, " ");
      if (!text || text.length > 120 || /\n\n/.test(text)) {
        setSelPopup(null);
        return;
      }
      const r = sel.getRangeAt(0).getBoundingClientRect();
      if (!r.width && !r.height) return;
      setSelPopup({
        x: Math.max(60, Math.min(window.innerWidth - 60, r.left + r.width / 2)),
        y: Math.max(70, r.top),
        text,
        isWord: /^[^\s]+$/.test(text),
      });
    };
    document.addEventListener("selectionchange", onSelectionChange);
    return () => document.removeEventListener("selectionchange", onSelectionChange);
  }, []);

  // keyboard navigation
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (settingsOpen || tocOpen) return;
      const target = e.target as HTMLElement | null;
      if (
        target &&
        (/^(BUTTON|INPUT|TEXTAREA|A|SELECT)$/.test(target.tagName) ||
          target.closest('[role="slider"], [role="dialog"]'))
      )
        return;
      if (e.key === "ArrowRight" || e.key === " " || e.key === "PageDown") {
        e.preventDefault();
        turnRef.current(1);
      } else if (e.key === "ArrowLeft" || e.key === "PageUp") {
        e.preventDefault();
        turnRef.current(-1);
      } else if (e.key === "Escape") {
        if (definitionRef.current) closeDefinition();
        else if (menuOpen) setMenuOpen(false);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [menuOpen, settingsOpen, tocOpen, closeDefinition]);

  // -----------------------------------------------------------------------
  // navigation helpers (slider + toc)
  // -----------------------------------------------------------------------
  const chapterForGlobalChar = useCallback(
    (target: number): { ci: number; local: number } => {
      if (!book) return { ci: 0, local: 0 };
      let ci = 0;
      for (let i = 0; i < book.chapters.length; i++) {
        if (target >= boundaries[i] && target < boundaries[i + 1]) {
          ci = i;
          break;
        }
        if (i === book.chapters.length - 1) ci = i;
      }
      return { ci, local: Math.max(0, Math.floor(target - boundaries[ci])) };
    },
    [book, boundaries]
  );

  const scrubLive = useCallback(
    (frac: number) => {
      const pz = paginatorRef.current;
      if (!pz || !book) return;
      const target = Math.max(0, Math.min(frac * book.totalChars, book.totalChars - 1));
      const { ci, local } = chapterForGlobalChar(target);
      if (ci !== chapterIndex) return; // cross-chapter jumps happen on commit
      pz.goToChar(local, false);
      setPage(pz.page);
      syncStats();
    },
    [book, chapterForGlobalChar, chapterIndex, syncStats]
  );

  const jumpToGlobal = useCallback(
    (frac: number) => {
      const pz = paginatorRef.current;
      if (!pz || !book) return;
      const target = Math.max(0, Math.min(frac * book.totalChars, book.totalChars - 1));
      const { ci, local } = chapterForGlobalChar(target);
      if (ci !== chapterIndex) {
        pendingOffsetRef.current = { kind: "char", offset: local };
        loadChapter(ci);
      } else {
        pz.goToChar(local, false);
        setPage(pz.page);
        syncStats();
      }
      saveProgressSoon();
    },
    [book, chapterForGlobalChar, chapterIndex, loadChapter, saveProgressSoon, syncStats]
  );

  const handleSliderChange = useCallback(
    (v: number[]) => {
      setScrubbing(v[0]);
      scrubLive(v[0] / 1000);
    },
    [scrubLive]
  );

  const handleSliderCommit = useCallback(
    (v: number[]) => {
      setScrubbing(null);
      jumpToGlobal(v[0] / 1000);
    },
    [jumpToGlobal]
  );

  const jumpToToc = useCallback(
    (entry: TocEntry) => {
      setTocOpen(false);
      const pending: PendingOffset = entry.anchor
        ? { kind: "anchor", id: entry.anchor }
        : { kind: "char", offset: 0 };
      if (entry.chapterIndex !== chapterIndex) {
        pendingOffsetRef.current = pending;
        loadChapter(entry.chapterIndex);
      } else {
        pendingOffsetRef.current = pending;
        setLayoutNonce((n) => n + 1);
      }
    },
    [chapterIndex, loadChapter]
  );

  // -----------------------------------------------------------------------
  // definition actions
  // -----------------------------------------------------------------------
  const explainInContext = useCallback(() => {
    const d = definitionRef.current;
    if (!d) return;
    lookupText(d.word, d.context, true);
  }, [lookupText]);

  const retryLookup = useCallback(() => {
    const d = definitionRef.current;
    if (!d) return;
    lookupText(d.word, d.context, d.aiMode);
  }, [lookupText]);

  const handleSelLookup = useCallback(() => {
    const sel = window.getSelection();
    const popup = selPopup;
    setSelPopup(null);
    if (!popup) return;
    let context = "";
    try {
      const node = sel?.anchorNode;
      if (node && node.nodeType === Node.TEXT_NODE) {
        context = sentenceContext(node as Text, (sel?.anchorOffset ?? 0), (sel?.anchorOffset ?? 0) + popup.text.length);
      }
    } catch {
      /* ignore */
    }
    lookupText(popup.text, context, !popup.isWord);
  }, [selPopup, lookupText]);

  // -----------------------------------------------------------------------
  // render
  // -----------------------------------------------------------------------
  const marginPx = MARGIN_STEPS[settings.margin]?.px ?? 26;
  const padY = Math.min(52, Math.max(22, Math.round(marginPx * 0.85)));
  // CRITICAL: the __html object must be referentially stable — React compares
  // the object reference and would otherwise rewrite the chapter DOM (and
  // detach every text node) on each unrelated re-render.
  const flowContent = useMemo(() => ({ __html: chapterHtml ?? "" }), [chapterHtml]);

  if (bookError) {
    return (
      <div className="fixed inset-0 z-40 flex flex-col items-center justify-center gap-4 bg-background px-6 text-center">
        <BookOpen className="size-10 text-muted-foreground" />
        <p className="text-muted-foreground">{bookError}</p>
        <button
          onClick={onExit}
          className="rounded-full border px-5 py-2 text-sm font-medium transition-colors hover:bg-accent"
        >
          Back to library
        </button>
      </div>
    );
  }

  if (!book) {
    return (
      <div className="fixed inset-0 z-40 flex items-center justify-center bg-background">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div
      className={cn(
        "reader-root fixed inset-0 z-40 flex flex-col select-none",
        themeClass(settings.theme)
      )}
      style={{ background: "var(--reader-bg)", color: "var(--reader-fg)" }}
    >
      {/* ------- reading viewport ------- */}
      <div
        ref={viewportRef}
        className="reader-viewport relative flex-1 overflow-hidden"
        style={{
          paddingTop: `calc(${padY}px + env(safe-area-inset-top, 0px))`,
          paddingBottom: `calc(${padY}px + env(safe-area-inset-bottom, 0px))`,
          paddingLeft: `calc(${marginPx}px + env(safe-area-inset-left, 0px))`,
          paddingRight: `calc(${marginPx}px + env(safe-area-inset-right, 0px))`,
          touchAction: "none",
        }}
        onPointerDown={(e) => {
          pointerDownRef.current = { x: e.clientX, y: e.clientY, t: Date.now() };
        }}
        onPointerUp={(e) => {
          const d = pointerDownRef.current;
          pointerDownRef.current = null;
          if (!d) return;
          const dx = e.clientX - d.x;
          const dy = e.clientY - d.y;
          const dt = Date.now() - d.t;
          if (dt > 700) return;
          const dist = Math.hypot(dx, dy);
          if (dist > 48 && Math.abs(dx) > Math.abs(dy) * 1.4) {
            turn(dx < 0 ? 1 : -1);
          } else if (dist < 12) {
            handleTap(e.clientX, e.clientY);
          }
        }}
        role="region"
        aria-label={`Reading ${book.title}`}
      >
        <div
          ref={flowRef}
          className={cn(
            "reader-flow",
            settings.justify && "justify",
            settings.hyphenate && "hyphens"
          )}
          style={{
            fontFamily: fontStack(settings.font),
            fontSize: `${settings.fontSize}px`,
            lineHeight: settings.lineHeight,
          }}
          lang={book.lang || "en"}
          dangerouslySetInnerHTML={flowContent}
        />

        {/* chapter loading */}
        {chapterHtml == null ? (
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
            <Loader2 className="size-6 animate-spin" style={{ color: "var(--reader-muted)" }} />
          </div>
        ) : null}
      </div>

      {/* ------- always-visible thin progress line ------- */}
      <div
        className="h-[2px] w-full shrink-0"
        style={{ background: "var(--reader-line)" }}
        aria-hidden
      >
        <div
          className="h-full transition-[width] duration-300"
          style={{
            width: `${stats.globalPercent * 100}%`,
            background: "var(--reader-accent)",
          }}
        />
      </div>

      {/* ------- top chrome ------- */}
      <AnimatePresence>
        {menuOpen ? (
          <motion.header
            key="chrome-top"
            initial={{ y: -64, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: -64, opacity: 0 }}
            transition={{ duration: 0.22, ease: "easeOut" }}
            className="reader-chrome absolute inset-x-0 top-0 z-30 flex items-center gap-1 border-b px-2 pb-2"
            style={{
              background: "var(--reader-chrome-bg)",
              borderColor: "var(--reader-line)",
              paddingTop: `calc(0.5rem + env(safe-area-inset-top, 0px))`,
            }}
          >
            <button
              onClick={() => {
                saveProgressNow();
                onExit();
              }}
              aria-label="Back to library"
              className="rounded-full p-2 transition-colors hover:bg-black/5 dark:hover:bg-white/10"
            >
              <ArrowLeft className="size-5" />
            </button>
            <div className="mx-1 min-w-0 flex-1 text-center">
              <p className="truncate text-sm font-medium">{book.title}</p>
              <p
                className="truncate text-xs"
                style={{ color: "var(--reader-muted)" }}
              >
                {chapterTitle}
              </p>
            </div>
            <button
              onClick={() => setSettingsOpen(true)}
              aria-label="Reading settings"
              className="reader-serif-aa rounded-full px-2.5 py-1.5 text-base font-semibold transition-colors hover:bg-black/5 dark:hover:bg-white/10"
            >
              Aa
            </button>
            <button
              onClick={() => setTocOpen(true)}
              aria-label="Table of contents"
              className="rounded-full p-2 transition-colors hover:bg-black/5 dark:hover:bg-white/10"
            >
              <List className="size-5" />
            </button>
          </motion.header>
        ) : null}
      </AnimatePresence>

      {/* ------- bottom chrome ------- */}
      <AnimatePresence>
        {menuOpen ? (
          <motion.footer
            key="chrome-bottom"
            initial={{ y: 96, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 96, opacity: 0 }}
            transition={{ duration: 0.22, ease: "easeOut" }}
            className="reader-chrome absolute inset-x-0 bottom-0 z-30 border-t"
            style={{
              background: "var(--reader-chrome-bg)",
              borderColor: "var(--reader-line)",
              paddingBottom: `calc(0.75rem + env(safe-area-inset-bottom, 0px))`,
            }}
          >
            <div className="flex flex-col gap-1.5 px-5 pt-3">
              <Slider
                value={[scrubbing ?? Math.round(stats.globalPercent * 1000)]}
                min={0}
                max={1000}
                step={1}
                onValueChange={handleSliderChange}
                onValueCommit={handleSliderCommit}
                aria-label="Reading progress"
                style={{ ["--primary" as string]: "var(--reader-accent)" }}
              />
              <div
                className="flex items-center justify-between pb-1.5 text-xs"
                style={{ color: "var(--reader-muted)" }}
              >
                <span>
                  Loc {stats.loc.toLocaleString()} of {stats.locTotal.toLocaleString()} ·{" "}
                  {Math.round(stats.globalPercent * 100)}%
                </span>
                <span>
                  {stats.timeChapter ? `${stats.timeChapter} left in chapter` : ""}
                </span>
              </div>
            </div>
          </motion.footer>
        ) : null}
      </AnimatePresence>

      {/* ------- desktop selection lookup chip ------- */}
      {selPopup ? (
        <div
          className="reader-pop fixed z-50 flex items-center gap-1 rounded-full border px-1.5 py-1 shadow-xl"
          style={{
            left: selPopup.x,
            top: selPopup.y - 44,
            transform: "translateX(-50%)",
            background: "var(--reader-pop-bg)",
            borderColor: "var(--reader-line)",
          }}
        >
          <button
            onClick={handleSelLookup}
            className="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium transition-colors hover:bg-black/5"
            style={{ color: "var(--reader-accent)" }}
          >
            <Sparkles className="size-3" />
            {selPopup.isWord ? "Define" : "Explain"}
          </button>
        </div>
      ) : null}

      {/* ------- definition popover ------- */}
      {definition ? (
        <DefinitionPopover
          state={definition}
          onClose={closeDefinition}
          onExplainInContext={explainInContext}
          onRetry={retryLookup}
        />
      ) : null}

      {/* ------- sheets ------- */}
      <SettingsSheet
        open={settingsOpen}
        onOpenChange={setSettingsOpen}
        settings={settings}
        onChange={setSettings}
        theme={settings.theme}
      />
      <TocSheet
        open={tocOpen}
        onOpenChange={setTocOpen}
        book={book}
        currentChapter={chapterIndex}
        onJump={jumpToToc}
        theme={settings.theme}
      />
    </div>
  );
}
